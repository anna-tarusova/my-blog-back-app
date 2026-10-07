package ru.practicum.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Пост в ответе API (элемент ленты).
 */
public class PostDto {
    @Getter
    @Setter
    private Long id;
    @Getter
    @Setter
    private String title;
    @Getter
    @Setter
    private String text;
    @Getter
    @Setter
    private List<String> tags;
    @Getter
    @Setter
    private long likesCount;
    @Getter
    @Setter
    private long commentsCount;

    public PostDto(Long id, String title, String text, List<String> tags, long likesCount, long commentsCount) {
        this.id = id;
        this.title = title;
        this.text = text;
        this.tags = tags;
        this.likesCount = likesCount;
        this.commentsCount = commentsCount;
    }
}

