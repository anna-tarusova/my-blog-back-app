package ru.practicum.dto;

import java.util.List;

/**
 * Тело запроса на создание поста.
 *
 * @param title название поста
 * @param text  текст поста в формате Markdown
 * @param tags  список тегов (может быть пустым)
 */
public record PostCreateDto(String title, String text, List<String> tags) {
}
