package ru.practicum;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Разворачивает webapp из {@code src/main/webapp} (та же структура, что в war-файле: WEB-INF/web.xml)
 * во встроенный Tomcat и проверяет, что описанные в web.xml контексты Spring поднимаются
 * и приложение обслуживает HTTP-запросы. Классы приложения и зависимости берутся из classpath
 * (как в war они лежат в WEB-INF/classes и WEB-INF/lib), вместо PostgreSQL — in-memory H2.
 */
class WebappDeploymentTest {

    private static final String H2_URL = "jdbc:h2:mem:blog-webapp-test;DB_CLOSE_DELAY=-1"
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static Tomcat tomcat;

    private static String baseUrl;

    @BeforeAll
    static void deployWebapp() throws Exception {
        System.setProperty("jdbc.url", H2_URL);
        System.setProperty("jdbc.username", "sa");
        System.setProperty("jdbc.password", "");

        tomcat = new Tomcat();
        tomcat.setPort(0);
        tomcat.getConnector();

        Context context = tomcat.addWebapp("", new File("src/main/webapp").getAbsolutePath());
        // классы и зависимости приложения доступны webapp-загрузчику через родительский classloader теста
        context.setParentClassLoader(WebappDeploymentTest.class.getClassLoader());

        tomcat.start();
        baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort();
    }

    @AfterAll
    static void undeployWebapp() throws Exception {
        tomcat.stop();
        tomcat.destroy();
        Map.of("jdbc.url", "", "jdbc.username", "", "jdbc.password", "").keySet().forEach(System::clearProperty);
    }

    @Test
    void servesPostApiThroughContextsDeclaredInWebXml() throws Exception {
        JsonNode emptyFeed = OBJECT_MAPPER.readTree(
                get("/api/posts?search=no-such-post&pageNumber=1&pageSize=5").body());
        assertEquals(0, emptyFeed.get("posts").size());
        assertEquals(1, emptyFeed.get("lastPage").asInt());

        HttpResponse<String> createdResponse = post("/api/posts", """
                {"title": "Пост из war", "text": "Текст поста в формате Markdown...", "tags": ["tag_1"]}
                """);
        assertEquals(201, createdResponse.statusCode());

        JsonNode createdPost = OBJECT_MAPPER.readTree(createdResponse.body());
        assertTrue(createdPost.get("id").asLong() > 0);
        assertEquals(1, createdPost.get("tags").size());

        JsonNode feed = OBJECT_MAPPER.readTree(get("/api/posts?search=war&pageNumber=1&pageSize=5").body());
        assertEquals(1, feed.get("posts").size());
        assertEquals(createdPost.get("id").asLong(), feed.get("posts").get(0).get("id").asLong());
        assertEquals("Пост из war", feed.get("posts").get(0).get("title").asText());
    }

    @Test
    void returns400And404FromWebLayer() throws Exception {
        assertEquals(400, get("/api/posts?search=a&pageNumber=1").statusCode());
        assertEquals(404, get("/api/unknown").statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
