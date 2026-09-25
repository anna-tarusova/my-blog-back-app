package ru.practicum.dao;

import org.springframework.data.repository.CrudRepository;
import ru.practicum.model.Tag;

/**
 * DAO-слой тегов постов.
 */
public interface TagRepository extends CrudRepository<Tag, Long> {
}
