package ru.practicum.service;

import ru.practicum.dto.PostCreateDto;
import ru.practicum.dto.PostDto;
import ru.practicum.dto.PostsPageDto;

/**
 * Бизнес-логика постов блога.
 */
public interface PostService {

    /**
     * Лента постов с поиском и постраничной выдачей.
     *
     * @param search     строка поиска по названию поста
     * @param pageNumber номер страницы, начиная с 1
     * @param pageSize   количество постов на странице
     * @return посты страницы и признаки наличия соседних страниц
     */
    PostsPageDto getPosts(String search, int pageNumber, int pageSize);

    /**
     * Создаёт пост вместе с его тегами.
     *
     * @param request название, текст (Markdown) и теги нового поста
     * @return созданный пост; {@code likesCount} и {@code commentsCount} равны 0
     * @throws IllegalArgumentException если обязательные поля не заполнены
     */
    PostDto createPost(PostCreateDto request);
}
