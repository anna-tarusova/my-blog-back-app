package ru.practicum.dto;

/**
 * Запрос на добавление комментария: {@code POST /api/posts/{id}/comments}.
 *
 * @param postId идентификатор поста из пути запроса (обязательное поле, должно совпадать с путём)
 * @param text   текст комментария
 */
public record CommentCreateDto(Long postId, String text) {
}

