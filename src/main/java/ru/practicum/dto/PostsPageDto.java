package ru.practicum.dto;

import java.util.List;

/**
 * Ответ API на запрос ленты постов: посты текущей страницы и признаки наличия соседних страниц.
 *
 * @param posts   посты текущей страницы
 * @param hasPrev true, если текущая страница не первая
 * @param hasNext true, если текущая страница не последняя
 * @param lastPage номер последней страницы
 */
public record PostsPageDto(List<PostDto> posts, boolean hasPrev, boolean hasNext, int lastPage) {
}
