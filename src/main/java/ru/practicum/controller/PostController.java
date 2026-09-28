package ru.practicum.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.practicum.dto.CommentCreateDto;
import ru.practicum.dto.CommentDto;
import ru.practicum.dto.CommentUpdateDto;
import ru.practicum.dto.PostCreateDto;
import ru.practicum.dto.PostDto;
import ru.practicum.dto.PostUpdateDto;
import ru.practicum.dto.PostsPageDto;
import ru.practicum.service.PostService;

import java.io.IOException;
import java.util.List;

/**
 * REST-контроллер постов.
 */
@RestController
@RequestMapping(path = "/api/posts", produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(
        origins = {"http://localhost", "http://localhost:80", "http://localhost:3000", "http://localhost:5173"},
        allowCredentials = "true",
        allowedHeaders = "*",
        methods = {org.springframework.web.bind.annotation.RequestMethod.GET,
                org.springframework.web.bind.annotation.RequestMethod.POST,
                org.springframework.web.bind.annotation.RequestMethod.PUT,
                org.springframework.web.bind.annotation.RequestMethod.DELETE,
                org.springframework.web.bind.annotation.RequestMethod.OPTIONS,
                org.springframework.web.bind.annotation.RequestMethod.PATCH}
)
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public PostsPageDto getPosts(@RequestParam(required = false) String search,
                                 @RequestParam(defaultValue = "1") int pageNumber,
                                 @RequestParam(defaultValue = "10") int pageSize) {
        return postService.getPostsPage(search, pageNumber, pageSize);
    }

    @GetMapping("/{id}")
    public PostDto getPost(@PathVariable Long id) {
        return postService.getPost(id);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        byte[] image = postService.getImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(image);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PostDto> createPost(@RequestBody PostCreateDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostDto> updatePost(@PathVariable Long id,
                                              @RequestBody PostUpdateDto request) {
        return ResponseEntity.ok(postService.updatePost(request));
    }

    @PutMapping(value = "/{id}/image")
    public ResponseEntity<Void> updateImage(@PathVariable Long id,
                                            @RequestPart("image") MultipartFile image) {
        try {
            postService.updateImage(id, image.getBytes());
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read image file");
        }
    }

    @PostMapping("/{id}/likes")
    public ResponseEntity<Long> incrementLikes(@PathVariable Long id) {
        return ResponseEntity.ok(postService.incrementLikes(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        postService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Получение комментариев поста: {@code GET /api/posts/{id}/comments}.
     * Возвращает список комментариев в порядке их создания.
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<List<CommentDto>> getComments(@PathVariable Long id) {
        List<CommentDto> comments = postService.getCommentsByPostId(id);
        return ResponseEntity.ok(comments);
    }

    /**
     * Получение комментария поста: {@code GET /api/posts/{postId}/comments/{commentId}}.
     * Возвращает комментарий по его идентификатору.
     */
    @GetMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<CommentDto> getComment(
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        CommentDto comment = postService.getCommentById(postId, commentId);
        return ResponseEntity.ok(comment);
    }

    /**
     * Добавление комментария к посту: {@code POST /api/posts/{postId}/comments}.
     * Тело запроса — {@code {"text": "...", "postId": 1}}.
     * Возвращает созданный комментарий с идентификатором.
     */
    @PostMapping("/{postId}/comments")
    public ResponseEntity<CommentDto> createComment(
            @PathVariable Long postId,
            @RequestBody CommentCreateDto request) {
        CommentDto comment = postService.createComment(postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    /**
     * Редактирование комментария к посту: {@code PUT /api/posts/{postId}/comments/{commentId}}.
     * Тело запроса — {@code {"id": 2, "text": "...", "postId": 1}}.
     * Возвращает обновлённый комментарий.
     */
    @PutMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<CommentDto> updateComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestBody CommentUpdateDto request) {
        CommentDto comment = postService.updateComment(postId, commentId, request);
        return ResponseEntity.ok(comment);
    }

    /**
     * Удаление комментария к посту: {@code DELETE /api/posts/{postId}/comments/{commentId}}.
     * Возвращает 200 OK при успешном удалении.
     */
    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        postService.deleteComment(postId, commentId);
        return ResponseEntity.ok().build();
    }
}

