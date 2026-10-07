package ru.practicum.dao;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.dto.PostDto;

import java.util.List;
import java.util.Optional;

public interface PostDao {
    @Query("""
            SELECT 
                p.ID,
                p.TITLE,
                p.TEXT,
                COALESCE(p.LIKES_COUNT, 0) AS LIKES_COUNT,
                COUNT(DISTINCT c.ID) AS COMMENTS_COUNT
            FROM POSTS p
            LEFT JOIN COMMENTS c ON c.POST_ID = p.ID
            WHERE (:search IS NULL OR :search = '' 
                   OR p.TITLE ILIKE '%' || :search || '%' 
                   OR p.TEXT ILIKE '%' || :search || '%')
            GROUP BY p.ID, p.TITLE, p.TEXT, p.LIKES_COUNT
            ORDER BY p.ID DESC
            LIMIT :limit OFFSET :offset
            """)
    List<PostDto> findPostsPage(@Param("search") String search,
                                @Param("limit") int limit,
                                @Param("offset") int offset);

    @Query("""
            SELECT 
                name 
            FROM tags
            WHERE post_id = :id
            """)
    List<String> findTags(@Param("id") Long id);

    @Query("""
            SELECT COUNT(*)
            FROM POSTS p
            WHERE (:search IS NULL OR :search = '' 
                   OR p.TITLE ILIKE '%' || :search || '%' 
                   OR p.TEXT ILIKE '%' || :search || '%')
            """)
    long countPosts(@Param("search") String search);

    @Query("""
            SELECT 
                p.ID,
                p.TITLE,
                p.TEXT,
                COALESCE(p.LIKES_COUNT, 0) AS LIKES_COUNT,
                COUNT(DISTINCT c.ID) AS COMMENTS_COUNT
            FROM POSTS p
            LEFT JOIN COMMENTS c ON c.POST_ID = p.ID
            WHERE p.ID = :id
            GROUP BY p.ID, p.TITLE, p.TEXT, p.LIKES_COUNT
            """)
    Optional<PostDto> findPostDtoById(@Param("id") Long id);

    @Modifying
    @Query("UPDATE POSTS SET LIKES_COUNT = LIKES_COUNT + 1 WHERE ID = :id")
    int incrementLikes(@Param("id") Long id);

    @Query("SELECT LIKES_COUNT FROM POSTS WHERE ID = :id")
    Optional<Long> findLikesCountById(@Param("id") Long id);

    @Modifying
    @Query("UPDATE POSTS SET IMAGE = :image WHERE ID = :id")
    int updateImage(@Param("id") Long id, @Param("image") byte[] image);

    @Query("SELECT IMAGE FROM POSTS WHERE ID = :id")
    Optional<byte[]> findImageById(@Param("id") Long id);
}