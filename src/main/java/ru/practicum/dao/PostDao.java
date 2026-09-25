package ru.practicum.dao;

import ru.practicum.dto.PostPreview;

import java.util.List;

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
}
