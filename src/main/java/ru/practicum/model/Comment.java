package ru.practicum.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Доменная модель комментария к посту (таблица {@code comments}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("comments")
public class Comment {

    @Id
    private Long id;

    /** Идентификатор поста, к которому относится комментарий (колонка post_id). */
    private Long postId;

    /** Текст комментария. */
    private String text;
}
