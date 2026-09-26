package ru.practicum.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.practicum.config.AppConfig;
import ru.practicum.config.WebConfig;
import ru.practicum.dao.PostRepository;
import ru.practicum.model.Post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционный тест эндпоинта ленты постов: Controller - Service - DAO - H2.
 */
@SpringJUnitWebConfig({AppConfig.class, WebConfig.class})
@TestPropertySource("classpath:db-test.properties")
class PostControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.update("DELETE FROM likes", Map.of());
        jdbcTemplate.update("DELETE FROM comments", Map.of());
        jdbcTemplate.update("DELETE FROM tags", Map.of());
        jdbcTemplate.update("DELETE FROM post_images", Map.of());
        jdbcTemplate.update("DELETE FROM posts", Map.of());
    }

    @Test
    void returnsNewestPostWithAllRequiredFields() throws Exception {
        long firstPostId = insertPost("Название поста 1", "Текст поста в формате Markdown...");
        long secondPostId = insertPost("Название поста 2", "Ещё один текст");
        insertTag(firstPostId, "#tag_1");
        insertTag(firstPostId, "#tag_2");

        JsonNode response = performFeed("", "1", "2");

        assertEquals(Set.of("posts", "hasPrev", "hasNext", "lastPage"), fieldNames(response));
        assertFalse(response.get("hasPrev").asBoolean());
        assertFalse(response.get("hasNext").asBoolean());
        assertEquals(1, response.get("lastPage").asInt());
        assertEquals(2, response.get("posts").size());

        JsonNode newestPost = response.get("posts").get(0);
        assertEquals(Set.of("id", "title", "text", "tags", "likesCount", "commentsCount"), fieldNames(newestPost));
        assertEquals(secondPostId, newestPost.get("id").asLong());
        assertEquals("Название поста 2", newestPost.get("title").asText());
        assertEquals("Ещё один текст", newestPost.get("text").asText());
        assertEquals(0, newestPost.get("likesCount").asLong());
        assertEquals(0, newestPost.get("commentsCount").asLong());
        assertTrue(newestPost.get("tags").isArray());
        assertEquals(0, newestPost.get("tags").size());
    }

    @Test
    void returnsPageFlagsForFirstMiddleAndOutOfRangePages() throws Exception {
        for (int i = 1; i <= 5; i++) {
            insertPost("Пост " + i, "текст");
        }

        JsonNode firstPage = performFeed("", "1", "2");
        assertEquals(2, firstPage.get("posts").size());
        assertFalse(firstPage.get("hasPrev").asBoolean());
        assertTrue(firstPage.get("hasNext").asBoolean());
        assertEquals(3, firstPage.get("lastPage").asInt());

        JsonNode lastPage = performFeed("", "3", "2");
        assertEquals(1, lastPage.get("posts").size());
        assertTrue(lastPage.get("hasPrev").asBoolean());
        assertFalse(lastPage.get("hasNext").asBoolean());
        assertEquals(3, lastPage.get("lastPage").asInt());

        JsonNode pageAfterLast = performFeed("", "4", "2");
        assertEquals(0, pageAfterLast.get("posts").size());
        assertTrue(pageAfterLast.get("hasPrev").asBoolean());
        assertFalse(pageAfterLast.get("hasNext").asBoolean());
        assertEquals(3, pageAfterLast.get("lastPage").asInt());
    }

    @Test
    void filtersPostsByTitleWithoutCaseSensitivity() throws Exception {
        insertPost("Lalala post", "текст");
        insertPost("Другой пост", "текст");

        JsonNode response = performFeed("LALALA", "1", "5");

        assertEquals(1, response.get("posts").size());
        assertEquals("Lalala post", response.get("posts").get(0).get("title").asText());
        assertEquals(1, response.get("lastPage").asInt());
    }

    @Test
    void truncatesTextLongerThan128CharactersAndKeepsShorterTextAsIs() throws Exception {
        insertPost("Длинный пост", "a".repeat(200));
        insertPost("Короткий пост", "b".repeat(128));

        JsonNode longPost = performFeed("Длинный", "1", "5").get("posts").get(0);
        JsonNode shortPost = performFeed("Короткий", "1", "5").get("posts").get(0);

        String truncatedText = longPost.get("text").asText();
        assertEquals(128 + 1, truncatedText.length());
        assertEquals("a".repeat(128) + "…", truncatedText);
        assertEquals("b".repeat(128), shortPost.get("text").asText());
    }

    @Test
    void returnsTagsWithoutPrefixAndCountsLikesAndComments() throws Exception {
        long postId = insertPost("Пост с тегами", "текст");
        insertTag(postId, "#tag_1");
        insertTag(postId, "#tag_2");
        insertTag(postId, "tag_3");
        for (int i = 0; i < 5; i++) {
            insertLike(postId);
        }
        insertComment(postId);

        JsonNode post = performFeed("", "1", "5").get("posts").get(0);

        assertEquals(3, post.get("tags").size());
        assertEquals("tag_1", post.get("tags").get(0).asText());
        assertEquals("tag_2", post.get("tags").get(1).asText());
        assertEquals("tag_3", post.get("tags").get(2).asText());
        assertEquals(5, post.get("likesCount").asLong());
        assertEquals(1, post.get("commentsCount").asLong());
    }

    @Test
    void returnsEmptyFeedWithLastPageOneWhenNothingMatches() throws Exception {
        insertPost("Lalala post", "текст");

        JsonNode response = performFeed("нет такого поста", "1", "5");

        assertEquals(0, response.get("posts").size());
        assertFalse(response.get("hasPrev").asBoolean());
        assertFalse(response.get("hasNext").asBoolean());
        assertEquals(1, response.get("lastPage").asInt());
    }

    @Test
    void rejectsMissingOrNotPositiveParametersWith400() throws Exception {
        mockMvc.perform(get("/api/posts").param("pageNumber", "1").param("pageSize", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));

        mockMvc.perform(get("/api/posts").param("search", "Lalala").param("pageSize", "5"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/posts").param("search", "Lalala").param("pageNumber", "1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/posts").param("search", "Lalala").param("pageNumber", "0").param("pageSize", "5"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/posts").param("search", "Lalala").param("pageNumber", "1").param("pageSize", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/posts").param("search", "Lalala").param("pageNumber", "abc").param("pageSize", "5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownEndpointReturns404() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsPostAndReturnsItWith201() throws Exception {
        JsonNode created = createPost("Название поста 3", "Текст поста в формате Markdown...", List.of("tag_1", "tag_2"));

        assertEquals(Set.of("id", "title", "text", "tags", "likesCount", "commentsCount"), fieldNames(created));
        assertTrue(created.get("id").asLong() > 0);
        assertEquals("Название поста 3", created.get("title").asText());
        assertEquals("Текст поста в формате Markdown...", created.get("text").asText());
        assertEquals(List.of("tag_1", "tag_2"), tagsOf(created));
        assertEquals(0, created.get("likesCount").asLong());
        assertEquals(0, created.get("commentsCount").asLong());
    }

    @Test
    void returnsFullTextOfCreatedPostWithoutTruncation() throws Exception {
        String longText = "a".repeat(200);

        JsonNode created = createPost("Длинный пост", longText, List.of());

        assertEquals(longText, created.get("text").asText());
    }

    @Test
    void normalizesTagsAndDropsDuplicatesOnCreate() throws Exception {
        JsonNode created = createPost("Пост с тегами", "текст", List.of("#tag_1", " tag_1 ", "tag_2", "  "));

        assertEquals(List.of("tag_1", "tag_2"), tagsOf(created));
    }

    @Test
    void createdPostAppearsInFeedWithTruncatedTextAndSavedTags() throws Exception {
        String longText = "a".repeat(200);
        JsonNode created = createPost("Пост из API", longText, List.of("#tag_1", "tag_2"));

        JsonNode feedPost = performFeed("", "1", "5").get("posts").get(0);

        assertEquals(created.get("id").asLong(), feedPost.get("id").asLong());
        assertEquals("Пост из API", feedPost.get("title").asText());
        assertEquals("a".repeat(128) + "…", feedPost.get("text").asText());
        assertEquals(List.of("tag_1", "tag_2"), tagsOf(feedPost));
        assertEquals(0, feedPost.get("likesCount").asLong());
        assertEquals(0, feedPost.get("commentsCount").asLong());
    }

    @Test
    void rejectsCreateRequestWithoutRequiredFieldsWith400() throws Exception {
        Map<String, Object> withoutTitle = Map.of("text", "текст", "tags", List.of());
        Map<String, Object> withoutText = Map.of("title", "Заголовок", "tags", List.of());
        Map<String, Object> withoutTags = Map.of("title", "Заголовок", "text", "текст");
        Map<String, Object> blankTitle = Map.of("title", "   ", "text", "текст", "tags", List.of());
        Map<String, Object> blankText = Map.of("title", "Заголовок", "text", "  ", "tags", List.of());

        for (Map<String, Object> invalidBody : List.of(withoutTitle, withoutText, withoutTags, blankTitle, blankText)) {
            mockMvc.perform(postWithJsonBody(OBJECT_MAPPER.writeValueAsString(invalidBody)))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(postWithJsonBody("не json"))
                .andExpect(status().isBadRequest());

        assertEquals(0, performFeed("", "1", "5").get("posts").size());
    }

    private JsonNode createPost(String title, String text, List<String> tags) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("text", text);
        body.put("tags", tags);

        String response = mockMvc.perform(postWithJsonBody(OBJECT_MAPPER.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        return OBJECT_MAPPER.readTree(response);
    }

    @Test
    void updatesPostAndReturnsItWithActualCountersAndNewTags() throws Exception {
        long postId = insertPost("Старое название", "Старый текст");
        insertTag(postId, "#old_tag");
        insertLike(postId);
        insertLike(postId);
        insertComment(postId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", postId);
        body.put("title", "Название поста 3");
        body.put("text", "Текст поста в формате Markdown...");
        body.put("tags", List.of("tag_1", "tag_2"));

        String response = mockMvc.perform(putWithJsonBody(postId, OBJECT_MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode updated = OBJECT_MAPPER.readTree(response);
        assertEquals(Set.of("id", "title", "text", "tags", "likesCount", "commentsCount"), fieldNames(updated));
        assertEquals(postId, updated.get("id").asLong());
        assertEquals("Название поста 3", updated.get("title").asText());
        assertEquals("Текст поста в формате Markdown...", updated.get("text").asText());
        assertEquals(List.of("tag_1", "tag_2"), tagsOf(updated));
        assertEquals(2, updated.get("likesCount").asLong());
        assertEquals(1, updated.get("commentsCount").asLong());

        // старые теги заменены новыми, пост виден в ленте с обновлённым названием
        JsonNode feedPost = performFeed("Название поста 3", "1", "5").get("posts").get(0);
        assertEquals(postId, feedPost.get("id").asLong());
        assertEquals(List.of("tag_1", "tag_2"), tagsOf(feedPost));
        assertEquals(2, feedPost.get("likesCount").asLong());
        assertEquals(1, feedPost.get("commentsCount").asLong());
    }

    @Test
    void returns400ForInvalidUpdateAnd404ForUnknownPost() throws Exception {
        long postId = insertPost("Заголовок", "текст");

        Map<String, Object> validBody = new LinkedHashMap<>();
        validBody.put("id", postId);
        validBody.put("title", "Заголовок");
        validBody.put("text", "текст");
        validBody.put("tags", List.of());

        // id в теле не совпадает с id в пути
        Map<String, Object> wrongIdBody = new LinkedHashMap<>(validBody);
        wrongIdBody.put("id", postId + 1);
        mockMvc.perform(putWithJsonBody(postId, OBJECT_MAPPER.writeValueAsString(wrongIdBody)))
                .andExpect(status().isBadRequest());

        // неполные тела запроса
        Map<String, Object> withoutId = new LinkedHashMap<>(validBody);
        withoutId.remove("id");
        Map<String, Object> withoutTitle = new LinkedHashMap<>(validBody);
        withoutTitle.remove("title");
        Map<String, Object> withoutText = new LinkedHashMap<>(validBody);
        withoutText.remove("text");
        Map<String, Object> withoutTags = new LinkedHashMap<>(validBody);
        withoutTags.remove("tags");
        Map<String, Object> blankTitle = new LinkedHashMap<>(validBody);
        blankTitle.put("title", "   ");

        for (Map<String, Object> invalidBody : List.of(withoutId, withoutTitle, withoutText, withoutTags, blankTitle)) {
            mockMvc.perform(putWithJsonBody(postId, OBJECT_MAPPER.writeValueAsString(invalidBody)))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(putWithJsonBody(postId, "не json"))
                .andExpect(status().isBadRequest());

        // неизвестный пост (id в теле совпадает с путём, но поста нет в БД)
        Map<String, Object> unknownPostBody = new LinkedHashMap<>(validBody);
        unknownPostBody.put("id", postId + 100);
        mockMvc.perform(putWithJsonBody(postId + 100, OBJECT_MAPPER.writeValueAsString(unknownPostBody)))
                .andExpect(status().isNotFound());

        // валидация не изменила пост
        JsonNode feedPost = performFeed("Заголовок", "1", "5").get("posts").get(0);
        assertEquals("текст", feedPost.get("text").asText());
    }

    private MockHttpServletRequestBuilder postWithJsonBody(String json) {
        return post("/api/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private MockHttpServletRequestBuilder putWithJsonBody(long postId, String json) {
        return put("/api/posts/" + postId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private JsonNode performFeed(String search, String pageNumber, String pageSize) throws Exception {
        String json = mockMvc.perform(get("/api/posts")
                        .param("search", search)
                        .param("pageNumber", pageNumber)
                        .param("pageSize", pageSize))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        return OBJECT_MAPPER.readTree(json);
    }

    @Test
    void deletesPostWithCommentsLikesTagsAndImageAndReturns200() throws Exception {
        long postId = insertPost("Пост на удаление", "текст");
        insertTag(postId, "#tag_1");
        insertLike(postId);
        insertComment(postId);
        mockMvc.perform(uploadImage(postId,
                        new MockMultipartFile("image", "image_name.jpg", "image/jpeg", new byte[]{0x01})))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/posts/" + postId))
                .andExpect(status().isOk());

        assertEquals(0, performFeed("Пост на удаление", "1", "5").get("posts").size());
        assertEquals(0, countChildRows("comments", postId));
        assertEquals(0, countChildRows("tags", postId));
        assertEquals(0, countChildRows("likes", postId));
        assertEquals(0, countChildRows("post_images", postId));
    }

    @Test
    void returns404WhenDeletingUnknownPostAnd400ForNonNumericId() throws Exception {
        mockMvc.perform(delete("/api/posts/999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/posts/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void incrementsLikesAndReturnsUpdatedCountInBody() throws Exception {
        long postId = insertPost("Пост для лайков", "текст");

        mockMvc.perform(post("/api/posts/" + postId + "/likes"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("1"));
        mockMvc.perform(post("/api/posts/" + postId + "/likes"))
                .andExpect(status().isOk())
                .andExpect(content().string("2"));

        JsonNode feedPost = performFeed("Пост для лайков", "1", "5").get("posts").get(0);
        assertEquals(2, feedPost.get("likesCount").asInt());

        mockMvc.perform(post("/api/posts/999/likes"))
                .andExpect(status().isNotFound());
    }

    @Test
    void storesImageUploadedAsMultipartAndReplacesItOnSecondUpload() throws Exception {
        long postId = insertPost("Пост с картинкой", "текст");
        byte[] imageBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0x00, (byte) 0x01};

        mockMvc.perform(uploadImage(postId,
                        new MockMultipartFile("image", "image_name.jpg", "image/jpeg", imageBytes)))
                .andExpect(status().isOk());

        Map<String, Object> stored = imageRow(postId);
        assertEquals("image_name.jpg", stored.get("file_name"));
        assertEquals("image/jpeg", stored.get("content_type"));
        assertArrayEquals(imageBytes, (byte[]) stored.get("data"));

        byte[] replacement = {(byte) 0x01, (byte) 0x02, (byte) 0x03};
        mockMvc.perform(uploadImage(postId,
                        new MockMultipartFile("image", "new.png", "image/png", replacement)))
                .andExpect(status().isOk());

        assertEquals(1, countChildRows("post_images", postId));
        Map<String, Object> replaced = imageRow(postId);
        assertEquals("new.png", replaced.get("file_name"));
        assertEquals("image/png", replaced.get("content_type"));
        assertArrayEquals(replacement, (byte[]) replaced.get("data"));
    }

    @Test
    void validatesImageUploadRequests() throws Exception {
        long postId = insertPost("Пустая картинка", "текст");

        // часть называется не "image" → обязательная часть отсутствует
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/posts/" + postId + "/image")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[]{0x01})))
                .andExpect(status().isBadRequest());

        // пустой файл
        mockMvc.perform(uploadImage(postId,
                        new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0])))
                .andExpect(status().isBadRequest());

        // неизвестный пост
        mockMvc.perform(uploadImage(999_999L,
                        new MockMultipartFile("image", "x.jpg", "image/jpeg", new byte[]{0x01})))
                .andExpect(status().isNotFound());

        // не-multipart-запрос
        mockMvc.perform(put("/api/posts/" + postId + "/image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());

        // заявлен multipart/form-data, но тело разобрать нельзя → MultipartException → 400
        mockMvc.perform(put("/api/posts/" + postId + "/image")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .content("это не multipart"))
                .andExpect(status().isBadRequest());

        assertEquals(0, countChildRows("post_images", postId));
    }

    @Test
    void returnsPostImageAsRawBytesWithStoredContentType() throws Exception {
        long postId = insertPost("Пост с картинкой", "текст");
        byte[] imageBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0x00, (byte) 0x01};
        mockMvc.perform(uploadImage(postId,
                        new MockMultipartFile("image", "image_name.jpg", "image/jpeg", imageBytes)))
                .andExpect(status().isOk());

        MvcResult imageResponse = mockMvc.perform(get("/api/posts/" + postId + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("image/jpeg")))
                .andReturn();

        assertArrayEquals(imageBytes, imageResponse.getResponse().getContentAsByteArray());

        // неизвестный пост → 404, пост без картинки → 404, нечисловой id → 400
        mockMvc.perform(get("/api/posts/999999/image"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/" + insertPost("Пост без картинки", "текст") + "/image"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/abc/image"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fallsBackToOctetStreamWhenStoredContentTypeIsInvalid() throws Exception {
        long postId = insertPost("Пост с битым MIME", "текст");
        mockMvc.perform(uploadImage(postId,
                        new MockMultipartFile("image", "x.jpg", "image/jpeg", new byte[]{0x01})))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE post_images SET content_type = 'broken-mime' WHERE post_id = :postId",
                Map.of("postId", postId));

        mockMvc.perform(get("/api/posts/" + postId + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    void returnsCommentsOfPostAsJsonArray() throws Exception {
        long postId = insertPost("Пост с комментариями", "текст");
        insertComment(postId, "Комментарий к посту 1");
        insertComment(postId, "Ещё один комментарий к посту 1");

        String body = mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode comments = OBJECT_MAPPER.readTree(body);
        assertTrue(comments.isArray());
        assertEquals(2, comments.size());

        JsonNode first = comments.get(0);
        Set<String> fields = new HashSet<>();
        first.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("id", "text", "postId"), fields);
        assertTrue(first.get("id").asLong() > 0);
        assertEquals(postId, first.get("postId").asLong());
        assertEquals("Комментарий к посту 1", first.get("text").asText());

        JsonNode second = comments.get(1);
        assertTrue(second.get("id").asLong() > first.get("id").asLong());
        assertEquals(postId, second.get("postId").asLong());
        assertEquals("Ещё один комментарий к посту 1", second.get("text").asText());
    }

    @Test
    void returnsEmptyCommentsArrayWhenPostHasNoComments() throws Exception {
        long postId = insertPost("Пост без комментариев", "текст");

        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("[]"));
    }

    @Test
    void validatesCommentsRequest() throws Exception {
        mockMvc.perform(get("/api/posts/999999/comments"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/abc/comments"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder uploadImage(long postId, MockMultipartFile file) {
        return multipart(HttpMethod.PUT, "/api/posts/" + postId + "/image").file(file);
    }

    private Map<String, Object> imageRow(long postId) {
        return jdbcTemplate.queryForMap(
                "SELECT file_name, content_type, data FROM post_images WHERE post_id = :postId",
                Map.of("postId", postId));
    }

    private int countChildRows(String table, long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE post_id = :postId",
                Map.of("postId", postId), Integer.class);
    }

    private long insertPost(String title, String text) {
        return postRepository.save(Post.builder().title(title).text(text).build()).getId();
    }
    private void insertTag(long postId, String name) {
        jdbcTemplate.update("INSERT INTO tags (post_id, name) VALUES (:postId, :name)",
                Map.of("postId", postId, "name", name));
    }

    private void insertLike(long postId) {
        jdbcTemplate.update("INSERT INTO likes (post_id) VALUES (:postId)", Map.of("postId", postId));
    }

    private void insertComment(long postId) {
        jdbcTemplate.update("INSERT INTO comments (post_id, text) VALUES (:postId, :text)",
                Map.of("postId", postId, "text", "комментарий"));
    }

    private void insertComment(long postId, String text) {
        jdbcTemplate.update("INSERT INTO comments (post_id, text) VALUES (:postId, :text)",
                Map.of("postId", postId, "text", text));
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private List<String> tagsOf(JsonNode post) {
        List<String> tags = new ArrayList<>();
        post.get("tags").forEach(tag -> tags.add(tag.asText()));
        return tags;
    }
}
