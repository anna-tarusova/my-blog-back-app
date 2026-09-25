package ru.practicum.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.config.AppConfig;
import ru.practicum.dao.PostRepository;
import ru.practicum.dao.TagRepository;
import ru.practicum.dto.PostCreateDto;
import ru.practicum.dto.PostDto;
import ru.practicum.dto.PostsPageDto;
import ru.practicum.model.Post;
import ru.practicum.model.Tag;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Интеграционные тесты сервисного слоя и его взаимодействия со слоем модели:
 * Spring Test Framework поднимает настоящий корневой контекст ({@link AppConfig}) с сервисами,
 * Spring Data JDBC и in-memory H2 вместо PostgreSQL.
 */
@SpringJUnitConfig(AppConfig.class)
@TestPropertySource(properties = PostServiceIntegrationTest.H2_URL)
@Transactional
class PostServiceIntegrationTest {

    static final String H2_URL = "jdbc.url=jdbc:h2:mem:blog-service-test;DB_CLOSE_DELAY=-1"
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    void createsPostWithTagsAndStoresModelEntities() {
        PostDto created = postService.createPost(
                new PostCreateDto("Название поста 3", "Текст поста в формате Markdown...", List.of("#tag_1", "tag_2")));

        assertNotNull(created.id());
        assertEquals("Название поста 3", created.title());
        assertEquals(List.of("tag_1", "tag_2"), created.tags());
        assertEquals(0L, created.likesCount());
        assertEquals(0L, created.commentsCount());

        Post storedPost = postRepository.findById(created.id()).orElseThrow();
        assertEquals("Название поста 3", storedPost.getTitle());
        assertEquals("Текст поста в формате Markdown...", storedPost.getText());

        List<Tag> storedTags = tagsOfPost(created.id());
        assertEquals(1, postRepository.count());
        assertEquals(List.of("tag_1", "tag_2"), storedTags.stream().map(Tag::getName).toList());
        assertTrue(storedTags.stream().allMatch(tag -> created.id().equals(tag.getPostId())));

        assertEquals(2, tagRepository.count());
    }

    @Test
    void storesSingleRowForDuplicatedTags() {
        PostDto created = postService.createPost(
                new PostCreateDto("Пост", "текст", List.of("#tag_1", " tag_1 ", "tag_1")));

        assertEquals(List.of("tag_1"), tagsOfPost(created.id()).stream().map(Tag::getName).toList());
        assertEquals(1, tagRepository.count());
    }

    @Test
    void createdPostIsReadBackByFeedWithTruncatedText() {
        String longText = "a".repeat(200);
        PostDto created = postService.createPost(new PostCreateDto("Длинный пост", longText, List.of("tag_1")));

        PostsPageDto page = postService.getPosts("", 1, 5);

        assertEquals(1, page.posts().size());
        PostDto feedPost = page.posts().getFirst();
        assertEquals(created.id(), feedPost.id());
        assertEquals("Длинный пост", feedPost.title());
        assertEquals("a".repeat(128) + "…", feedPost.text());
        assertEquals(List.of("tag_1"), feedPost.tags());
        assertEquals(0L, feedPost.likesCount());
        assertEquals(0L, feedPost.commentsCount());
        assertEquals(1, page.lastPage());
    }

    @Test
    void searchesPostsByTitleThroughModelLayer() {
        postService.createPost(new PostCreateDto("Lalala post", "текст", List.of()));
        postService.createPost(new PostCreateDto("Другой пост", "текст", List.of()));

        PostsPageDto matching = postService.getPosts("lalala", 1, 10);
        assertEquals(1, matching.posts().size());
        assertEquals("Lalala post", matching.posts().getFirst().title());

        assertEquals(2, postService.getPosts("", 1, 10).posts().size());
    }

    @Test
    void doesNotStoreAnythingWhenRequestIsInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> postService.createPost(new PostCreateDto("   ", "текст", List.of("tag_1"))));

        assertEquals(0, postRepository.count());
        assertEquals(0, tagRepository.count());
    }

    private List<Tag> tagsOfPost(Long postId) {
        return StreamSupport.stream(tagRepository.findAll().spliterator(), false)
                .filter(tag -> postId.equals(tag.getPostId()))
                .toList();
    }
}
