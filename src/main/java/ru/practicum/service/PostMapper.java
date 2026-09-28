package ru.practicum.service;

import org.springframework.stereotype.Component;
import ru.practicum.dto.PostDto;
import ru.practicum.model.Post;

import java.util.List;

/**
 * Преобразование постов из формата БД в формат API ({@link PostDto}).
 * Правила представления: обрезка длинного текста и теги без символа "#".
 */
@Component
public class PostMapper {

    /** По требованию API текст в ленте обрезается до 128 символов. */
    public static final int MAX_TEXT_LENGTH = 128;

    private static final String ELLIPSIS = "…";
    private static final String TAG_PREFIX = "#";

    /**
     * Только что созданный пост: текст отдаётся целиком (без обрезки под ленту),
     * лайков и комментариев у нового поста ещё нет.
     */
    public PostDto toCreatedDto(Post post, List<String> tags) {
        return new PostDto(post.getId(), post.getTitle(), post.getText(), tags, 0L, 0L);
    }

    private String toPreviewText(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH) + ELLIPSIS;
    }

    /** В БД теги хранятся так, как их ввёл пользователь (возможно, с "#"), а API отдаёт их без "#". */
    private List<String> withoutTagPrefixes(List<String> tags) {
        return tags.stream()
                .map(tag -> tag.startsWith(TAG_PREFIX) ? tag.substring(TAG_PREFIX.length()) : tag)
                .toList();
    }
}
