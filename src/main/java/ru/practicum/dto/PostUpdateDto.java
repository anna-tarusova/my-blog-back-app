package ru.practicum.dto;

import java.util.List;

/**
 * Тело запроса на редактирование поста.
 *
 * @param id    идентификатор редактируемого поста (должен совпадать с id в пути запроса)
 * @param title новое название поста
 * @param text  новый текст поста в формате Markdown
 * @param tags  новый список тегов (заменяет теги поста целиком)
 */
public record PostUpdateDto(Long id, String title, String text, List<String> tags) {
}

