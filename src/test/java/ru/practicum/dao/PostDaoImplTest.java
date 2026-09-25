package ru.practicum.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import ru.practicum.config.JdbcConfig;
import ru.practicum.dto.PostPreview;
import ru.practicum.model.Post;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверки SQL ленты постов на in-memory H2.
 */
@SpringJUnitConfig(classes = {JdbcConfig.class, PostDaoImplTest.DaoConfig.class})
@TestPropertySource("classpath:db-test.properties")
class PostDaoImplTest {

    @Configuration
    @ComponentScan("ru.practicum.dao")
    static class DaoConfig {
    }

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM likes", Map.of());
        jdbcTemplate.update("DELETE FROM comments", Map.of());
        jdbcTemplate.update("DELETE FROM tags", Map.of());
        jdbcTemplate.update("DELETE FROM posts", Map.of());
    }

    @Test
    void countBySearchIgnoresCaseAndLikeWildcards() {
        insertPost("Lalala post", "текст");
        insertPost("Другой пост", "текст");
        insertPost("Скидка 100%", "текст");

        assertEquals(1L, postRepository.countBySearch("lalala"));
        assertEquals(1L, postRepository.countBySearch("LALALA"));
        assertEquals(3L, postRepository.countBySearch(""));
        // "%" в поисковой строке экранируется и ищется как обычный символ
        assertEquals(1L, postRepository.countBySearch("%"));
    }

    @Test
    void findPreviewPageReturnsNewestPostsWithCountersAndTags() {
        long firstPostId = insertPost("Первый", "текст");
        long secondPostId = insertPost("Второй", "текст");
        long thirdPostId = insertPost("Третий", "текст");

        insertTag(thirdPostId, "#tag_1");
        insertTag(thirdPostId, "#tag_2");
        insertLike(thirdPostId);
        insertLike(thirdPostId);
        insertComment(thirdPostId);

        List<PostPreview> firstPage = postRepository.findPreviewPage("", 2, 0L);

        assertEquals(2, firstPage.size());
        assertEquals(List.of(thirdPostId, secondPostId), firstPage.stream().map(PostPreview::id).toList());
        assertEquals(List.of("#tag_1", "#tag_2"), firstPage.getFirst().tags());
        assertEquals(2L, firstPage.getFirst().likesCount());
        assertEquals(1L, firstPage.getFirst().commentsCount());
        assertEquals(List.of(), firstPage.get(1).tags());
        assertEquals(0L, firstPage.get(1).likesCount());

        List<PostPreview> secondPage = postRepository.findPreviewPage("", 2, 2L);
        assertEquals(List.of(firstPostId), secondPage.stream().map(PostPreview::id).toList());
    }

    @Test
    void findPreviewPageFiltersByTitleAndReturnsEmptyListForNoMatches() {
        insertPost("Lalala post", "текст");
        insertPost("Другой пост", "текст");

        assertEquals(1, postRepository.findPreviewPage("lalala", 10, 0L).size());
        assertTrue(postRepository.findPreviewPage("нет такого", 10, 0L).isEmpty());
    }

    private long insertPost(String title, String text) {
        Post post = postRepository.save(Post.builder().title(title).text(text).build());
        return post.getId();
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
}
