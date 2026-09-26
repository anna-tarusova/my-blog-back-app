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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void storesMultipartImageIncrementsLikesAndDeletesPostThroughWarStack() throws Exception {
        HttpResponse<String> createdResponse = post("/api/posts", """
                {"title": "Multipart картинка", "text": "Текст", "tags": ["pic"]}
                """);
        assertEquals(201, createdResponse.statusCode());
        long postId = OBJECT_MAPPER.readTree(createdResponse.body()).get("id").asLong();

        // multipart-загрузка через web.xml (<multipart-config>) и StandardServletMultipartResolver
        byte[] imageBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x10};
        assertEquals(200, putImage(postId, imageBytes).statusCode());
        try (Connection connection = DriverManager.getConnection(H2_URL, "sa", "");
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT file_name, content_type, data FROM post_images WHERE post_id = ?")) {
            statement.setLong(1, postId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "после загрузки должна появиться строка в post_images");
                assertEquals("image_name.jpg", resultSet.getString("file_name"));
                assertEquals("image/jpeg", resultSet.getString("content_type"));
                assertArrayEquals(imageBytes, resultSet.getBytes("data"));
            }
        }

        // инкремент лайков: обновлённое число лайков в теле ответа
        HttpResponse<String> firstLike = send(HttpRequest.newBuilder(
                        URI.create(baseUrl + "/api/posts/" + postId + "/likes"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
        assertEquals(200, firstLike.statusCode());
        assertEquals("1", firstLike.body());

        // удаление: пост исчезает вместе с тегами, лайками и картинкой
        assertEquals(200, send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/posts/" + postId))
                .DELETE()
                .build()).statusCode());

        JsonNode feed = OBJECT_MAPPER.readTree(get("/api/posts?search=Multipart&pageNumber=1&pageSize=5").body());
        assertEquals(0, feed.get("posts").size());
        assertEquals(0, countRelatedRows("post_images", postId));
        assertEquals(0, countRelatedRows("likes", postId));
        assertEquals(0, countRelatedRows("tags", postId));
    }

    @Test
    void servesImageAndCommentsOfDeployedPost() throws Exception {
        HttpResponse<String> createdResponse = post("/api/posts", """
                {"title": "Пост с картинкой и комментариями", "text": "Текст", "tags": []}
                """);
        assertEquals(201, createdResponse.statusCode());
        long postId = OBJECT_MAPPER.readTree(createdResponse.body()).get("id").asLong();

        // комментариев создаёт API ещё нет — вставляем напрямую в БД
        insertComment(postId, "Комментарий к посту 1");
        insertComment(postId, "Ещё один комментарий к посту 1");

        // картинка: загрузили → отдаётся сырыми байтами с сохранённым MIME-типом
        byte[] imageBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0x00, (byte) 0x01};
        assertEquals(200, putImage(postId, imageBytes).statusCode());

        HttpResponse<byte[]> image = HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/posts/" + postId + "/image")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, image.statusCode());
        assertTrue(image.headers().firstValue("Content-Type").isPresent());
        assertEquals("image/jpeg", image.headers().firstValue("Content-Type").get());
        assertArrayEquals(imageBytes, image.body());

        // комментарии: JSON-массив [{id, text, postId}]
        HttpResponse<String> comments = send(HttpRequest.newBuilder(
                        URI.create(baseUrl + "/api/posts/" + postId + "/comments")).GET().build());
        assertEquals(200, comments.statusCode());
        JsonNode parsedComments = OBJECT_MAPPER.readTree(comments.body());
        assertTrue(parsedComments.isArray());
        assertEquals(2, parsedComments.size());
        for (JsonNode comment : parsedComments) {
            assertEquals(postId, comment.get("postId").asLong());
            assertTrue(comment.get("id").asLong() > 0);
        }
        assertEquals("Комментарий к посту 1", parsedComments.get(0).get("text").asText());
        assertEquals("Ещё один комментарий к посту 1", parsedComments.get(1).get("text").asText());

        assertEquals(404, get("/api/posts/" + postId + "/missing").statusCode());
        assertEquals(404, get("/api/posts/999999/image").statusCode());
        assertEquals(404, get("/api/posts/999999/comments").statusCode());
    }

    @Test
    void rejectsImageLargerThanMultipartConfigLimit() throws Exception {
        HttpResponse<String> createdResponse = post("/api/posts", """
                {"title": "Огромная картинка", "text": "Текст", "tags": []}
                """);
        assertEquals(201, createdResponse.statusCode());
        long postId = OBJECT_MAPPER.readTree(createdResponse.body()).get("id").asLong();

        // 11 МБ > лимита 10 МБ из <multipart-config> web.xml → MaxUploadSizeExceededException → 413
        byte[] oversized = new byte[11 * 1024 * 1024];
        assertEquals(413, putImage(postId, oversized).statusCode());
        assertEquals(0, countRelatedRows("post_images", postId));
    }

    private HttpResponse<String> putImage(long postId, byte[] imageContent) throws Exception {
        String boundary = "----MyBlogUploadBoundary";
        byte[] partHead = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"image_name.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] partTail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] body = new byte[partHead.length + imageContent.length + partTail.length];
        System.arraycopy(partHead, 0, body, 0, partHead.length);
        System.arraycopy(imageContent, 0, body, partHead.length, imageContent.length);
        System.arraycopy(partTail, 0, body, partHead.length + imageContent.length, partTail.length);

        return send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/posts/" + postId + "/image"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build());
    }

    private int countRelatedRows(String table, long postId) throws Exception {
        try (Connection connection = DriverManager.getConnection(H2_URL, "sa", "");
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE post_id = ?")) {
            statement.setLong(1, postId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    private void insertComment(long postId, String text) throws Exception {
        try (Connection connection = DriverManager.getConnection(H2_URL, "sa", "");
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO comments (post_id, text) VALUES (?, ?)")) {
            statement.setLong(1, postId);
            statement.setString(2, text);
            statement.executeUpdate();
        }
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
