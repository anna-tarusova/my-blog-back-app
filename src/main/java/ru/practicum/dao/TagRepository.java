package ru.practicum.dao;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import ru.practicum.model.Tag;

public interface TagRepository extends CrudRepository<Tag, Long> {
    @Modifying
    @Query("DELETE FROM TAGS WHERE POST_ID = :postId")
    void deleteByPostId(Long postId);
}