package ru.practicum.dto;

/**
 * Ответ API с комментарием поста: {@code GET /api/posts/{id}/comments}.
 *
 * @param id     идентификатор комментария
 * @param text   текст комментария
 * @param postId идентификатор поста, к которому относится комментарий
 */
public record CommentDto(Long id, String text, Long postId) {
}

