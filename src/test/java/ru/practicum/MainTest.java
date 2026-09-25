package ru.practicum;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Поднимает приложение так же, как это делает {@link Main}, и проверяет реальные HTTP-ответы
 * встроенного Tomcat (без Postgres — на in-memory H2 из тестового classpath).
 */
class MainTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, String> H2_PROPERTIES = Map.of(
            "jdbc.url", "jdbc:h2:mem:blog-main-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;"
                    + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
            "jdbc.username", "sa",
            "jdbc.password", "");

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static Tomcat tomcat;

    private static String baseUrl;

    @BeforeAll
    static void startApplication() throws Exception {
        H2_PROPERTIES.forEach(System::setProperty);

        tomcat = Main.startServer(0);
        baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort();
    }

    @AfterAll
    static void stopApplication() throws Exception {
        tomcat.stop();
        tomcat.destroy();
        H2_PROPERTIES.keySet().forEach(System::clearProperty);
    }

    @Test
    void returnsEmptyFeedForSearchWithoutMatches() throws Exception {
        HttpResponse<String> response = get("/api/posts?search=no-such-post&pageNumber=1&pageSize=5");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));

        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        assertEquals(0, body.get("posts").size());
        assertEquals(1, body.get("lastPage").asInt());
        assertEquals(false, body.get("hasPrev").asBoolean());
        assertEquals(false, body.get("hasNext").asBoolean());
    }

    @Test
    void createsPostOverHttpAndReturnsItInFeed() throws Exception {
        String requestBody = """
                {
                  "title": "Пост через HTTP",
                  "text": "Текст поста в формате Markdown...",
                  "tags": ["tag_1", "tag_2"]
                }
                """;

        HttpResponse<String> created = send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/posts"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build());

        assertEquals(201, created.statusCode());
        JsonNode createdBody = OBJECT_MAPPER.readTree(created.body());
        assertEquals("Пост через HTTP", createdBody.get("title").asText());
        assertEquals(2, createdBody.get("tags").size());
        assertEquals(0, createdBody.get("likesCount").asLong());
        assertEquals(0, createdBody.get("commentsCount").asLong());

        JsonNode feed = OBJECT_MAPPER.readTree(get("/api/posts?search=HTTP&pageNumber=1&pageSize=5").body());
        assertEquals(1, feed.get("posts").size());
        assertEquals(createdBody.get("id").asLong(), feed.get("posts").get(0).get("id").asLong());
        assertEquals("Пост через HTTP", feed.get("posts").get(0).get("title").asText());
    }

    @Test
    void returns400ForMissingParameterAnd404ForUnknownPath() throws Exception {
        assertEquals(400, get("/api/posts?search=&pageNumber=1").statusCode());
        assertEquals(404, get("/api/unknown").statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
