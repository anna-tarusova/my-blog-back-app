package ru.practicum.dto;

import java.util.List;

/**
 * Пост в ответе API (элемент ленты).
 */
public record PostDto(
        Long id,
        String title,
        String text,
        List<String> tags,
        long likesCount,
        long commentsCount) {
}
