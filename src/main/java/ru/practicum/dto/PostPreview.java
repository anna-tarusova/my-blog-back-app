package ru.practicum.dto;

import java.util.List;

/**
 * Read-модель поста для ленты: сам пост вместе с агрегированными счётчиками и тегами.
 * Возвращается DAO-слоем; в JSON не сериализуется (для API есть {@link PostDto}).
 */
public record PostPreview(Long id, String title, String text, List<String> tags, long likesCount, long commentsCount) {

    public PostPreview withTags(List<String> tags) {
        return new PostPreview(id, title, text, tags, likesCount, commentsCount);
    }
}
