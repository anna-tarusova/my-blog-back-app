package ru.practicum.dao;

import ru.practicum.dto.CommentDto;
import ru.practicum.dto.PostImageDto;
import ru.practicum.dto.PostPreview;

import java.util.List;
import java.util.Optional;

/**
 * Кастомная (SQL) часть репозитория постов: запросы ленты, которые не выражаются CRUD-методами Spring Data.
 * Реализация — {@link PostDaoImpl}, Spring Data JDBC «подмешивает» её в {@link PostRepository}.
 */
public interface PostDao {

    /** Общее количество постов, в названии которых встречается {@code search} (без учёта регистра). */
    long countBySearch(String search);

    /**
     * Страница постов для ленты вместе с числом лайков, числом комментариев и списком тегов.
     * Посты отсортированы от новых к старым (по id по убыванию).
     *
     * @param search подстрока названия поста (без учёта регистра), пустая строка — без фильтра
     * @param limit  размер страницы
     * @param offset смещение первой записи страницы
     */
    List<PostPreview> findPreviewPage(String search, int limit, long offset);

    /** Удаляет все теги поста (используется при редактировании: список тегов заменяется целиком). */
    void deleteTagsByPostId(long postId);

    /** Число лайков поста. */
    long countLikesByPostId(long postId);

    /** Число комментариев поста. */
    long countCommentsByPostId(long postId);

    /** Добавляет один лайк поста (строка в таблице likes). */
    void insertLike(long postId);

    /**
     * Сохраняет или полностью заменяет картинку поста (upsert: старая строка удаляется, новая вставляется).
     *
     * @param postId      идентификатор поста — существование поста проверяется до вызова (внешний ключ)
     * @param fileName    оригинальное имя файла
     * @param contentType MIME-тип файла (не пустой)
     * @param data        содержимое файла
     */
    void saveImage(long postId, String fileName, String contentType, byte[] data);

    /**
     * Комментарии поста в порядке добавления (по id возрастанию).
     * Существование поста проверяется до вызова; для поста без комментариев — пустой список.
     */
    List<CommentDto> findCommentsByPostId(long postId);

    /**
     * Картинка поста (имя файла, MIME-тип, содержимое) или пустой {@code Optional},
     * если картинка не загружена. Существование поста проверяется до вызова.
     */
    Optional<PostImageDto> findImageByPostId(long postId);
}
