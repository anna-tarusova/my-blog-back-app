package ru.practicum.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.dto.PostCreateDto;
import ru.practicum.dto.PostDto;
import ru.practicum.dto.PostsPageDto;
import ru.practicum.service.PostService;

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
}

