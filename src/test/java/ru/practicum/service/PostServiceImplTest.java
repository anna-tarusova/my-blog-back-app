package ru.practicum.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.practicum.dao.CommentRepository;
import ru.practicum.dao.PostRepository;
import ru.practicum.dao.TagRepository;
import ru.practicum.dto.CommentCreateDto;
import ru.practicum.dto.PostCreateDto;
import ru.practicum.dto.PostDto;
import ru.practicum.dto.PostUpdateDto;
import ru.practicum.exceptions.NotFoundException;
import ru.practicum.model.Post;
import ru.practicum.model.Tag;
import ru.practicum.service.impl.PostServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private PostMapper postMapper;

    @InjectMocks
    private PostServiceImpl postService;

    private Post testPost;
    private PostDto testPostDto;

    @BeforeEach
    void setUp() {
        testPost = Post.builder().id(1L).title("Test Title").text("Test Text").likesCount(0).build();
        testPostDto = new PostDto(1L, "Test Title", "Test Text", List.of(), 0, 0);
    }

    @Test
    void getPost_shouldReturnPostWithTags() {
        when(postRepository.findPostDtoById(1L)).thenReturn(Optional.of(testPostDto));
        when(postRepository.findTags(1L)).thenReturn(List.of("tag1", "tag2"));

        PostDto result = postService.getPost(1L);

        assertNotNull(result);
        assertEquals(2, result.getTags().size());
        assertTrue(result.getTags().contains("tag1"));
    }

    @Test
    void getPost_notFound_shouldThrowNotFoundException() {
        when(postRepository.findPostDtoById(99L)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class, () -> postService.getPost(99L));
        assertEquals("post not found", exception.getMessage());
    }

    @Test
    void getPostsPage_invalidPageNumber_shouldThrowBadRequest() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> postService.getPostsPage(null, 0, 10));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void createPost_shouldSavePostAndTagsAndReturnDto() {
        // 1. Создаем перехватчик для списка тегов
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Tag>> tagCaptor = ArgumentCaptor.forClass(List.class);

        PostCreateDto request = new PostCreateDto("New Title", "New Text", List.of("#tag1", "tag2"));
        Post savedPost = Post.builder().id(2L).title("New Title").text("New Text").build();

        when(postRepository.save(any(Post.class))).thenReturn(savedPost);
        when(postMapper.toCreatedDto(eq(savedPost), anyList())).thenReturn(testPostDto);

        // 2. Вызываем тестируемый метод
        PostDto result = postService.createPost(request);

        // 3. Проверяем базовые вызовы
        assertNotNull(result);
        verify(postRepository, times(1)).save(any(Post.class));

        // 4. Перехватываем аргумент, переданный в saveAll
        verify(tagRepository, times(1)).saveAll(tagCaptor.capture());

        // 5. Получаем перехваченный список и проверяем его содержимое обычными assert'ами
        List<Tag> savedTags = tagCaptor.getValue();

        assertEquals(2, savedTags.size());
        assertEquals("tag1", savedTags.get(0).getName()); // # был удален методом normalizeTags
        assertEquals("tag2", savedTags.get(1).getName());
        assertEquals(2L, savedTags.get(0).getPostId());   // Проверяем, что postId привязан корректно
        assertEquals(2L, savedTags.get(1).getPostId());
    }

    @Test
    void createPost_nullTitle_shouldThrowIllegalArgumentException() {
        PostCreateDto request = new PostCreateDto(null, "Text", List.of());
        assertThrows(IllegalArgumentException.class, () -> postService.createPost(request));
    }

    @Test
    void updatePost_shouldUpdatePostAndReplaceTags() {
        PostUpdateDto request = new PostUpdateDto(1L, "Updated Title", "Updated Text", List.of("newTag"));

        when(postRepository.findById(1L)).thenReturn(Optional.of(testPost));
        when(postRepository.save(any(Post.class))).thenReturn(testPost);
        when(postRepository.findPostDtoById(1L)).thenReturn(Optional.of(testPostDto));

        PostDto result = postService.updatePost(request);

        assertNotNull(result);
        verify(tagRepository, times(1)).deleteByPostId(1L);
        verify(tagRepository, times(1)).saveAll(anyList());
    }

    @Test
    void incrementLikes_postExists_shouldReturnNewCount() {
        when(postRepository.incrementLikes(1L)).thenReturn(1);
        when(postRepository.findLikesCountById(1L)).thenReturn(Optional.of(5L));

        long result = postService.incrementLikes(1L);

        assertEquals(5L, result);
    }

    @Test
    void incrementLikes_postNotFound_shouldThrowResponseStatusException() {
        when(postRepository.incrementLikes(99L)).thenReturn(0);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> postService.incrementLikes(99L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void createComment_mismatchedPostId_shouldThrowBadRequest() {
        CommentCreateDto request = new CommentCreateDto(2L, "Nice!");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> postService.createComment(1L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("postId in path does not match postId in body"));
    }

    @Test
    void createComment_blankText_shouldThrowBadRequest() {
        CommentCreateDto request = new CommentCreateDto(1L, "   ");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> postService.createComment(1L, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Comment text is required"));
    }
}