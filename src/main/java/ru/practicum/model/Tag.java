package ru.practicum.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Доменная модель тега поста (таблица {@code tags}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("tags")
public class Tag {

    @Id
    private Long id;

    /** Идентификатор поста, которому принадлежит тег (колонка post_id). */
    private Long postId;

    /** Название тега. Хранится без символа "#". */
    private String name;
}
