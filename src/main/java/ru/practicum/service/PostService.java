package ru.practicum.service;

import ru.practicum.dto.CommentCreateDto;
import ru.practicum.dto.CommentDto;
import ru.practicum.dto.CommentUpdateDto;
import ru.practicum.dto.PostCreateDto;
import ru.practicum.dto.PostDto;
import ru.practicum.dto.PostUpdateDto;
import ru.practicum.dto.PostsPageDto;

import java.util.List;

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
    PostsPageDto getPostsPage(String search, int pageNumber, int pageSize);

    /**
     * Возвращает пост
     * @param id ид поста
     * @return пост
     */
    PostDto getPost(long id);

      /**
     * Создаёт пост вместе с его тегами.
     *
     * @param request название, текст (Markdown) и теги нового поста
     * @return созданный пост; {@code likesCount} и {@code commentsCount} равны 0
     * @throws IllegalArgumentException если обязательные поля не заполнены
     */
    PostDto createPost(PostCreateDto request);

    /**
     * Редактирует пост: обновляет название, текст и полностью заменяет список тегов.
     *
     * @param request идентификатор, название, текст (Markdown) и теги поста
     * @return обновлённый пост; {@code likesCount} и {@code commentsCount} — фактические значения
     * @throws java.util.NoSuchElementException если пост с таким id не найден
     * @throws IllegalArgumentException         если обязательные поля не заполнены
     *                                         или {@code id} в теле запроса не совпадает с id в пути
     */
    PostDto updatePost(PostUpdateDto request);

    /**
     * Удаляет пост вместе со всеми связанными данными: комментариями, тегами, лайками и картинкой
     * (строки дочерних таблиц удаляются каскадно по внешним ключам {@code ON DELETE CASCADE}).
     *
     * @param id идентификатор поста из пути запроса
     * @throws java.util.NoSuchElementException если пост с таким id не найден
     * @throws IllegalArgumentException         если {@code id} не задан
     */
    void deletePost(Long id);

    /**
     * Добавляет +1 к числу лайков поста.
     *
     * @param id идентификатор поста из пути запроса
     * @return обновлённое число лайков поста (фактическое значение после инкремента)
     * @throws java.util.NoSuchElementException если пост с таким id не найден
     * @throws IllegalArgumentException         если {@code id} не задан
     */
    long incrementLikes(Long id);


    void updateImage(Long id, byte[] image);

    /**
     * Картинка поста для отдачи в теле ответа: MIME-тип, сохранённый при загрузке, и сырые байты.
     *
     * @param id идентификатор поста из пути запроса
     * @return картинка поста
     * @throws java.util.NoSuchElementException если пост не найден или картинка не загружена
     * @throws IllegalArgumentException         если {@code id} не задан
     */
    byte[] getImage(Long id);

    List<CommentDto> getCommentsByPostId(Long postId);

    CommentDto getCommentById(Long postId, Long commentId);

    /**
     * Создаёт комментарий к посту: {@code POST /api/posts/{id}/comments}.
     *
     * @param id      идентификатор поста из пути запроса
     * @param request идентификатор поста (должен совпадать с путём) и текст комментария
     * @return созданный комментарий с сгенерированным id
     * @throws java.util.NoSuchElementException если пост не найден
     * @throws IllegalArgumentException         если обязательные поля не заполнены
     *                                         или {@code postId} в теле не совпадает с id в пути
     */
    CommentDto createComment(Long id, CommentCreateDto request);

    /**
     * Редактирует комментарий поста: {@code PUT /api/posts/{id}/comments/{commentId}}.
     *
     * @param id        идентификатор поста из пути запроса
     * @param commentId идентификатор комментария из пути запроса
     * @param request   идентификатор комментария, идентификатор поста и новый текст;
     *                  оба id должны совпадать с путём запроса
     * @return обновлённый комментарий
     * @throws java.util.NoSuchElementException если пост или комментарий не найдены
     *                                         (комментарий другого поста тоже считается ненайденным)
     * @throws IllegalArgumentException         если обязательные поля не заполнены,
     *                                         {@code id} в теле не совпадает с id в пути
     *                                         или {@code postId} в теле не совпадает с id в пути
     */
    CommentDto updateComment(Long id, Long commentId, CommentUpdateDto request);

    /**
     * Удаляет комментарий поста: {@code DELETE /api/posts/{id}/comments/{commentId}}.
     *
     * @param id        идентификатор поста из пути запроса
     * @param commentId идентификатор комментария из пути запроса
     * @throws java.util.NoSuchElementException если пост или комментарий не найдены
     *                                         (комментарий другого поста тоже считается ненайденным)
     * @throws IllegalArgumentException         если один из id не задан
     */
    void deleteComment(Long id, Long commentId);
}
