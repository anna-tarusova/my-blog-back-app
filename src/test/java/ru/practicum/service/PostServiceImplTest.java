package ru.practicum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.dao.PostRepository;
import ru.practicum.dao.TagRepository;
import ru.practicum.dto.PostCreateDto;
import ru.practicum.dto.PostDto;
import ru.practicum.dto.PostPreview;
import ru.practicum.dto.PostsPageDto;
import ru.practicum.model.Post;
import ru.practicum.model.Tag;
import ru.practicum.service.impl.PostServiceImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private TagRepository tagRepository;

    @Spy
    private PostMapper postMapper;

    @InjectMocks
    private PostServiceImpl postService;

    @Test
    void returnsFirstPageWithHasNextTrue() {
        when(postRepository.countBySearch("lala")).thenReturn(11L);
        when(postRepository.findPreviewPage("lala", 5, 0L)).thenReturn(List.of(preview(1L,
                "Пост", "текст")));

        PostsPageDto page = postService.getPosts("lala", 1, 5);

        assertEquals(1, page.posts().size());
        assertFalse(page.hasPrev());
        assertTrue(page.hasNext());
        assertEquals(3, page.lastPage());
    }

    @Test
    void returnsLastPageWithHasPrevTrueAndOffsetPassedToDao() {
        when(postRepository.countBySearch("lala")).thenReturn(11L);
        when(postRepository.findPreviewPage("lala", 5, 10L)).thenReturn(List.of(preview(1L,
                "Пост", "текст")));

        PostsPageDto page = postService.getPosts("lala", 3, 5);

        assertTrue(page.hasPrev());
        assertFalse(page.hasNext());
        assertEquals(3, page.lastPage());
        verify(postRepository).findPreviewPage("lala", 5, 10L);
    }

    @Test
    void returnsEmptyPageWithLastPageOneWhenNothingFound() {
        when(postRepository.countBySearch("нет такого")).thenReturn(0L);
        when(postRepository.findPreviewPage("нет такого", 5, 0L)).thenReturn(List.of());

        PostsPageDto page = postService.getPosts("нет такого", 1, 5);

        assertTrue(page.posts().isEmpty());
        assertFalse(page.hasPrev());
        assertFalse(page.hasNext());
        assertEquals(1, page.lastPage());
    }

    @Test
    void returnsEmptyPageWithHasPrevTrueWhenPageIsBeyondTheLastOne() {
        when(postRepository.countBySearch("")).thenReturn(10L);
        when(postRepository.findPreviewPage("", 5, 20L)).thenReturn(List.of());

        PostsPageDto page = postService.getPosts("", 5, 5);

        assertTrue(page.posts().isEmpty());
        assertTrue(page.hasPrev());
        assertFalse(page.hasNext());
        assertEquals(2, page.lastPage());
    }

    @Test
    void truncatesTextLongerThan128CharactersAndStripsTagPrefixes() {
        String longText = "a".repeat(200);
        when(postRepository.countBySearch("")).thenReturn(1L);
        when(postRepository.findPreviewPage("", 5, 0L))
                .thenReturn(List.of(new PostPreview(1L, "Пост", longText, List.of("#tag_1", "tag_2"),
                        5L, 1L)));

        PostDto post = postService.getPosts("", 1, 5).posts().getFirst();

        assertEquals(129, post.text().length());
        assertTrue(post.text().endsWith("…"));
        assertEquals(List.of("tag_1", "tag_2"), post.tags());
        assertEquals(5L, post.likesCount());
        assertEquals(1L, post.commentsCount());
    }

    @Test
    void keepsTextOfExactly128CharactersAsIs() {
        String text = "a".repeat(128);
        when(postRepository.countBySearch("")).thenReturn(1L);
        when(postRepository.findPreviewPage("", 5, 0L)).thenReturn(List.of(preview(1L,
                "Пост", text)));

        PostDto post = postService.getPosts("", 1, 5).posts().getFirst();

        assertEquals(text, post.text());
    }

    @Test
    void trimsSearchQueryBeforeCallingDao() {
        when(postRepository.countBySearch("Lalala")).thenReturn(0L);
        when(postRepository.findPreviewPage("Lalala", 5, 0L)).thenReturn(List.of());

        postService.getPosts("  Lalala  ", 1, 5);

        verify(postRepository).countBySearch("Lalala");
        verify(postRepository).findPreviewPage("Lalala", 5, 0L);
    }

    @Test
    void rejectsNotPositivePageNumberAndPageSize() {
        assertThrows(IllegalArgumentException.class, () -> postService.getPosts("", 0, 5));
        assertThrows(IllegalArgumentException.class, () -> postService.getPosts("", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> postService.getPosts("", -1, -5));
    }

    @Test
    void createsPostWithTrimmedFieldsNormalizedTagsAndZeroCounters() {
        givenSavedPostGetsId(42L);

        PostDto created = postService.createPost(new PostCreateDto(
                " Название поста ", " Текст поста ",
                Arrays.asList("#tag_1", " tag_1 ", "tag_2", "   ", null)));

        assertEquals(42L, created.id());
        assertEquals("Название поста", created.title());
        assertEquals("Текст поста", created.text());
        assertEquals(List.of("tag_1", "tag_2"), created.tags());
        assertEquals(0L, created.likesCount());
        assertEquals(0L, created.commentsCount());

        verify(tagRepository).saveAll(argThat(tags -> tagDescriptions(tags)
                .equals(List.of("42:tag_1", "42:tag_2"))));
    }

    @Test
    void createsPostWithoutTags() {
        givenSavedPostGetsId(7L);

        PostDto created = postService.createPost(new PostCreateDto("Заголовок", "Текст", List.of()));

        assertEquals(List.of(), created.tags());
        verify(tagRepository).saveAll(argThat(tags -> !tags.iterator().hasNext()));
    }

    @Test
    void doesNotTruncateTextOfCreatedPost() {
        givenSavedPostGetsId(1L);
        String longText = "a".repeat(200);

        PostDto created = postService.createPost(new PostCreateDto("Заголовок", longText, List.of()));

        assertEquals(longText, created.text());
    }

    @Test
    void rejectsCreateRequestWithBlankFieldsOrMissingTags() {
        assertThrows(IllegalArgumentException.class,
                () -> postService.createPost(new PostCreateDto(null, "текст", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> postService.createPost(new PostCreateDto("   ", "текст", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> postService.createPost(new PostCreateDto("Заголовок", null, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> postService.createPost(new PostCreateDto("Заголовок", "  ", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> postService.createPost(new PostCreateDto("Заголовок", "текст", null)));

        verifyNoInteractions(postRepository, tagRepository);
    }

    private void givenSavedPostGetsId(long id) {
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(id);
            assertNotNull(post.getTitle());
            return post;
        });
    }

    private List<String> tagDescriptions(Iterable<Tag> tags) {
        List<String> descriptions = new ArrayList<>();
        tags.forEach(tag -> descriptions.add(tag.getPostId() + ":" + tag.getName()));
        return descriptions;
    }

    private PostPreview preview(long id, String title, String text) {
        return new PostPreview(id, title, text, List.of(), 0L, 0L);
    }
}
