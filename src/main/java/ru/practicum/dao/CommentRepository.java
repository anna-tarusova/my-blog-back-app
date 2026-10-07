package ru.practicum.dao;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.Comment;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends CrudRepository<Comment, Long> {
    @Query("SELECT ID, POST_ID, TEXT FROM COMMENTS WHERE POST_ID = :postId ORDER BY ID ASC")
    List<Comment> findByPostId(@Param("postId") Long postId);

    @Query("SELECT ID, POST_ID, TEXT FROM COMMENTS WHERE ID = :id AND POST_ID = :postId")
    Optional<Comment> findByIdAndPostId(@Param("id") Long id, @Param("postId") Long postId);
}