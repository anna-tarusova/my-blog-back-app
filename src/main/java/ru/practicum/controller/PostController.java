package ru.practicum.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.practicum.dto.CommentDto;
import ru.practicum.dto.PostCreateDto;
import ru.practicum.dto.PostDto;
import ru.practicum.dto.PostImageDto;
import ru.practicum.dto.PostUpdateDto;
import ru.practicum.dto.PostsPageDto;
import ru.practicum.service.PostService;

import java.io.IOException;
import java.util.List;

/**
 * REST-контроллер постов.
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * Лента постов: {@code GET /api/posts?search=Lalala&pageNumber=1&pageSize=5}.
     * Все параметры обязательные; отсутствующие или некорректные значения дают 400.
     */
    @GetMapping
    public PostsPageDto getPosts(@RequestParam String search,
                                 @RequestParam int pageNumber,
                                 @RequestParam int pageSize) {
        return postService.getPosts(search, pageNumber, pageSize);
    }

    /**
     * Создание поста: {@code POST /api/posts}.
     * Тело запроса — {@code {"title": "...", "text": "...", "tags": ["tag_1", "tag_2"]}}.
     * Отсутствующие или пустые обязательные поля дают 400.
     */
    @PostMapping
    public ResponseEntity<PostDto> createPost(@RequestBody PostCreateDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(request));
    }

    /**
     * Редактирование поста: {@code PUT /api/posts/{id}}.
     * Тело запроса — {@code {"id": 3, "title": "...", "text": "...", "tags": [...]}}; все поля обязательные,
     * {@code id} в теле должен совпадать с id в пути. Неизвестный id даёт 404, некорректное тело — 400.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PostDto> updatePost(@PathVariable Long id, @RequestBody PostUpdateDto request) {
        return ResponseEntity.ok(postService.updatePost(id, request));
    }

    /**
     * Удаление поста: {@code DELETE /api/posts/{id}}.
     * Пост удаляется вместе со всеми комментариями (каскадом также теги, лайки и картинка).
     * Известный id — {@code 200 OK} без тела, неизвестный — 404, некорректный — 400.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        postService.deletePost(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Инкремент лайков: {@code POST /api/posts/{id}/likes}.
     * В теле ответа — обновлённое число лайков поста (JSON-число), неизвестный id — 404.
     */
    @PostMapping("/{id}/likes")
    public ResponseEntity<Long> incrementLikes(@PathVariable Long id) {
        return ResponseEntity.ok(postService.incrementLikes(id));
    }

    /**
     * Обновление картинки поста: {@code PUT /api/posts/{id}/image}, {@code multipart/form-data}
     * с частью {@code name="image"}. Успешное обновление — {@code 200 OK} без тела;
     * отсутствующая или пустая часть — 400, неизвестный id — 404, не-multipart — 415.
     */
    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updatePostImage(@PathVariable Long id,
                                                @RequestPart("image") MultipartFile image) throws IOException {
        postService.updateImage(id, image.getOriginalFilename(), image.getContentType(), image.getBytes());
        return ResponseEntity.ok().build();
    }

    /**
     * Получение картинки поста: {@code GET /api/posts/{id}/image} (лента и страница поста).
     * В теле ответа — сырые байты картинки, {@code Content-Type} — MIME-тип, сохранённый при загрузке;
     * неизвестный id или пост без картинки — 404, некорректный id — 400.
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getPostImage(@PathVariable Long id) {
        PostImageDto image = postService.getPostImage(id);
        return ResponseEntity.ok()
                .contentType(imageContentType(image.contentType()))
                .body(image.data());
    }

    /**
     * Получение комментариев поста: {@code GET /api/posts/{id}/comments}.
     * JSON-массив {@code [{id, text, postId}]} в порядке добавления (id по возрастанию);
     * пост без комментариев — пустой массив, неизвестный id — 404, некорректный id — 400.
     */
    @GetMapping("/{id}/comments")
    public List<CommentDto> getPostComments(@PathVariable Long id) {
        return postService.getComments(id);
    }

    /**
     * MIME-тип из БД; некорректное сохранённое значение не должно приводить к 500 —
     * отдаётся {@code application/octet-stream}.
     */
    private static MediaType imageContentType(String storedContentType) {
        try {
            return MediaType.parseMediaType(storedContentType);
        } catch (InvalidMediaTypeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

