package ru.practicum.dao;

import org.springframework.data.repository.CrudRepository;
import ru.practicum.model.Post;

/**
 * DAO-слой постов: CRUD-операции из Spring Data JDBC плюс кастомные запросы ленты ({@link PostDao}).
 */
public interface PostRepository extends CrudRepository<Post, Long>, PostDao {

}
