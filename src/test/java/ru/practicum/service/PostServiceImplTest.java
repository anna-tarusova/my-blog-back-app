package ru.practicum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.dao.PostRepository;
import ru.practicum.dao.TagRepository;
import ru.practicum.dto.CommentDto;
import ru.practicum.dto.PostCreateDto;
import ru.practicum.dto.PostDto;
import ru.practicum.dto.PostImageDto;
import ru.practicum.dto.PostPreview;
import ru.practicum.dto.PostUpdateDto;
import ru.practicum.dto.PostsPageDto;
import ru.practicum.model.Post;
import ru.practicum.model.Tag;
import ru.practicum.service.impl.PostServiceImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
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

    @Test
    void updatesPostReplacingTagsAndReturningActualCounters() {
        Post existing = Post.builder().id(3L).title("Старый заголовок").text("Старый текст").build();
        when(postRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(postRepository.countLikesByPostId(3L)).thenReturn(5L);
        when(postRepository.countCommentsByPostId(3L)).thenReturn(2L);

        PostDto updated = postService.updatePost(3L, new PostUpdateDto(
                3L, " Название поста 3 ", " Текст поста ",
                Arrays.asList("#tag_1", " tag_1 ", "tag_2", "   ", null)));

        assertEquals(3L, updated.id());
        assertEquals("Название поста 3", updated.title());
        assertEquals("Текст поста", updated.text());
        assertEquals(List.of("tag_1", "tag_2"), updated.tags());
        assertEquals(5L, updated.likesCount());
        assertEquals(2L, updated.commentsCount());

        verify(postRepository).deleteTagsByPostId(3L);
        verify(tagRepository).saveAll(argThat(tags -> tagDescriptions(tags)
                .equals(List.of("3:tag_1", "3:tag_2"))));
    }

    @Test
    void rejectsUpdateRequestWithBlankFieldsOrMissingTags() {
        assertThrows(IllegalArgumentException.class,
                () -> postService.updatePost(1L, new PostUpdateDto(1L, null, "текст", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> postService.updatePost(1L, new PostUpdateDto(1L, "   ", "текст", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> postService.updatePost(1L, new PostUpdateDto(1L, "Заголовок", null, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> postService.updatePost(1L, new PostUpdateDto(1L, "Заголовок", "текст", null)));
        assertThrows(IllegalArgumentException.class,
                () -> postService.updatePost(1L, new PostUpdateDto(null, "Заголовок", "текст", List.of())));

        verifyNoInteractions(postRepository, tagRepository);
    }

    @Test
    void rejectsUpdateWhenBodyIdDoesNotMatchPathId() {
        assertThrows(IllegalArgumentException.class,
                () -> postService.updatePost(2L, new PostUpdateDto(1L, "Заголовок", "текст", List.of())));

        verifyNoInteractions(postRepository, tagRepository);
    }

    @Test
    void rejectsUpdateOfUnknownPost() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> postService.updatePost(99L, new PostUpdateDto(99L, "Заголовок", "текст", List.of())));

        verify(postRepository).findById(99L);
        verifyNoInteractions(tagRepository);
    }

    @Test
    void deletesPostWithAllRelatedRowsWhenItExists() {
        when(postRepository.findById(3L)).thenReturn(Optional.of(existingPost(3L)));

        postService.deletePost(3L);

        verify(postRepository).deleteById(3L);
        verifyNoInteractions(tagRepository);
    }

    @Test
    void rejectsDeleteOfUnknownPost() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> postService.deletePost(99L));

        verify(postRepository, never()).deleteById(anyLong());
        verifyNoInteractions(tagRepository);
    }

    @Test
    void rejectsDeleteWithMissingId() {
        assertThrows(IllegalArgumentException.class, () -> postService.deletePost(null));

        verifyNoInteractions(postRepository, tagRepository);
    }

    @Test
    void incrementsLikesAndReturnsUpdatedCount() {
        when(postRepository.findById(3L)).thenReturn(Optional.of(existingPost(3L)));
        when(postRepository.countLikesByPostId(3L)).thenReturn(6L);

        long likesCount = postService.incrementLikes(3L);

        assertEquals(6L, likesCount);
        verify(postRepository).insertLike(3L);
        verifyNoInteractions(tagRepository);
    }

    @Test
    void rejectsLikeOfUnknownPost() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> postService.incrementLikes(99L));

        verify(postRepository, never()).insertLike(anyLong());
    }

    @Test
    void rejectsLikeWithMissingId() {
        assertThrows(IllegalArgumentException.class, () -> postService.incrementLikes(null));

        verifyNoInteractions(postRepository, tagRepository);
    }

    @Test
    void savesImageForExistingPostWithGivenFileNameAndContentType() {
        when(postRepository.findById(3L)).thenReturn(Optional.of(existingPost(3L)));
        byte[] image = {(byte) 0xFF, (byte) 0xD8, 0x01};

        postService.updateImage(3L, " image_name.jpg ", "image/jpeg", image);

        verify(postRepository).saveImage(3L, "image_name.jpg", "image/jpeg", image);
        verifyNoInteractions(tagRepository);
    }

    @Test
    void replacesBlankContentTypeWithOctetStream() {
        when(postRepository.findById(3L)).thenReturn(Optional.of(existingPost(3L)));

        postService.updateImage(3L, "image.jpg", " ", new byte[]{0x01});

        verify(postRepository).saveImage(3L, "image.jpg", "application/octet-stream", new byte[]{0x01});
    }

    @Test
    void rejectsImageUploadWithBlankFileNameEmptyFileOrMissingId() {
        assertThrows(IllegalArgumentException.class,
                () -> postService.updateImage(1L, " ", "image/jpeg", new byte[]{0x01}));
        assertThrows(IllegalArgumentException.class,
                () -> postService.updateImage(1L, "image.jpg", "image/jpeg", new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> postService.updateImage(1L, "image.jpg", "image/jpeg", null));
        assertThrows(IllegalArgumentException.class,
                () -> postService.updateImage(null, "image.jpg", "image/jpeg", new byte[]{0x01}));

        verifyNoInteractions(postRepository, tagRepository);
    }

    @Test
    void rejectsImageUploadForUnknownPost() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> postService.updateImage(99L, "image.jpg", "image/jpeg", new byte[]{0x01}));

        verify(postRepository, never()).saveImage(anyLong(), anyString(), anyString(), any(byte[].class));
    }

    @Test
    void returnsImageOfPostWithStoredContentType() {
        when(postRepository.findById(3L)).thenReturn(Optional.of(existingPost(3L)));
        byte[] image = {(byte) 0xFF, (byte) 0xD8, 0x01};
        when(postRepository.findImageByPostId(3L))
                .thenReturn(Optional.of(new PostImageDto("image_name.jpg", "image/jpeg", image)));

        PostImageDto loaded = postService.getPostImage(3L);

        assertEquals("image_name.jpg", loaded.fileName());
        assertEquals("image/jpeg", loaded.contentType());
        assertArrayEquals(image, loaded.data());
        verifyNoInteractions(tagRepository);
    }

    @Test
    void rejectsImageRequestForUnknownPost() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> postService.getPostImage(99L));

        verify(postRepository, never()).findImageByPostId(anyLong());
    }

    @Test
    void rejectsImageRequestWhenImageIsNotUploaded() {
        when(postRepository.findById(3L)).thenReturn(Optional.of(existingPost(3L)));
        when(postRepository.findImageByPostId(3L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> postService.getPostImage(3L));
    }

    @Test
    void rejectsImageRequestWithMissingId() {
        assertThrows(IllegalArgumentException.class, () -> postService.getPostImage(null));

        verifyNoInteractions(postRepository, tagRepository);
    }

    @Test
    void returnsCommentsOfExistingPost() {
        when(postRepository.findById(3L)).thenReturn(Optional.of(existingPost(3L)));
        when(postRepository.findCommentsByPostId(3L)).thenReturn(List.of(
                new CommentDto(1L, "Комментарий к посту 1", 3L),
                new CommentDto(2L, "Ещё один комментарий к посту 1", 3L)));

        List<CommentDto> comments = postService.getComments(3L);

        assertEquals(2, comments.size());
        assertEquals("Комментарий к посту 1", comments.get(0).text());
        assertEquals(Long.valueOf(3), comments.get(0).postId());
        assertEquals("Ещё один комментарий к посту 1", comments.get(1).text());
        verifyNoInteractions(tagRepository);
    }

    @Test
    void returnsEmptyCommentListWhenPostHasNoComments() {
        when(postRepository.findById(3L)).thenReturn(Optional.of(existingPost(3L)));
        when(postRepository.findCommentsByPostId(3L)).thenReturn(List.of());

        assertEquals(List.of(), postService.getComments(3L));
    }

    @Test
    void rejectsCommentsRequestForUnknownPost() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> postService.getComments(99L));

        verify(postRepository, never()).findCommentsByPostId(anyLong());
    }

    @Test
    void rejectsCommentsRequestWithMissingId() {
        assertThrows(IllegalArgumentException.class, () -> postService.getComments(null));

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

    private Post existingPost(long id) {
        return Post.builder().id(id).title("Заголовок").text("Текст").build();
    }
}
