package ru.practicum.dto;

/**
 * Тело запроса на редактирование комментария: {@code PUT /api/posts/{id}/comments/{commentId}}.
 *
 * @param id     идентификатор редактируемого комментария (должен совпадать с id в пути запроса)
 * @param postId идентификатор поста (должен совпадать с id в пути запроса)
 * @param text   новый текст комментария
 */
public record CommentUpdateDto(Long id, String text, Long postId) {
}

