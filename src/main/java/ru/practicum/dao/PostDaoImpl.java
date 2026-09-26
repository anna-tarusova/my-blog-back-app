package ru.practicum.dao;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.practicum.dto.CommentDto;
import ru.practicum.dto.PostImageDto;
import ru.practicum.dto.PostPreview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Реализация SQL-запросов ленты постов на {@link NamedParameterJdbcTemplate}.
 * SQL написан на переносимом подмножестве (PostgreSQL и H2), без агрегатных функций конкретной СУБД:
 * счётчики лайков/комментариев берутся подзапросами, теги — вторым запросом по списку id (без N+1 на пост).
 */
@Repository
public class PostDaoImpl implements PostDao {

    /** Фильтр по названию поста: поиск подстроки без учёта регистра. */
    private static final String TITLE_FILTER = "LOWER(p.title) LIKE LOWER(:pattern) ESCAPE '\\'";

    private static final String COUNT_BY_SEARCH_SQL = """
            SELECT COUNT(*)
            FROM posts p
            WHERE %s
            """.formatted(TITLE_FILTER);

    private static final String FIND_PREVIEW_PAGE_SQL = """
            SELECT p.id AS id,
                   p.title AS title,
                   p.text AS text,
                   (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id) AS likes_count,
                   (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) AS comments_count
            FROM posts p
            WHERE %s
            ORDER BY p.id DESC
            LIMIT :limit OFFSET :offset
            """.formatted(TITLE_FILTER);

    private static final String FIND_TAGS_SQL = """
            SELECT t.post_id AS post_id, t.name AS name
            FROM tags t
            WHERE t.post_id IN (:postIds)
            ORDER BY t.post_id, t.id
            """;

    private static final String DELETE_TAGS_BY_POST_ID_SQL = """
            DELETE FROM tags
            WHERE post_id = :postId
            """;

    private static final String COUNT_LIKES_BY_POST_ID_SQL = """
            SELECT COUNT(*)
            FROM likes
            WHERE post_id = :postId
            """;

    private static final String COUNT_COMMENTS_BY_POST_ID_SQL = """
            SELECT COUNT(*)
            FROM comments
            WHERE post_id = :postId
            """;

    private static final String INSERT_LIKE_SQL = """
            INSERT INTO likes (post_id)
            VALUES (:postId)
            """;

    private static final String DELETE_IMAGE_BY_POST_ID_SQL = """
            DELETE FROM post_images
            WHERE post_id = :postId
            """;

    private static final String INSERT_IMAGE_SQL = """
            INSERT INTO post_images (post_id, file_name, content_type, data)
            VALUES (:postId, :fileName, :contentType, :data)
            """;

    private static final String FIND_COMMENTS_BY_POST_ID_SQL = """
            SELECT c.id AS id,
                   c.post_id AS post_id,
                   c.text AS text
            FROM comments c
            WHERE c.post_id = :postId
            ORDER BY c.id
            """;

    private static final String FIND_IMAGE_BY_POST_ID_SQL = """
            SELECT i.file_name AS file_name,
                   i.content_type AS content_type,
                   i.data AS data
            FROM post_images i
            WHERE i.post_id = :postId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostDaoImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long countBySearch(String search) {
        Long count = jdbcTemplate.queryForObject(COUNT_BY_SEARCH_SQL, Map.of("pattern", likePattern(search)), Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public List<PostPreview> findPreviewPage(String search, int limit, long offset) {
        Map<String, Object> params = Map.of(
                "pattern", likePattern(search),
                "limit", limit,
                "offset", offset);

        List<PostPreview> posts = jdbcTemplate.query(FIND_PREVIEW_PAGE_SQL, params, (resultSet, rowNumber) -> new PostPreview(
                resultSet.getLong("id"),
                resultSet.getString("title"),
                resultSet.getString("text"),
                List.of(),
                resultSet.getLong("likes_count"),
                resultSet.getLong("comments_count")));

        return posts.isEmpty() ? posts : attachTags(posts);
    }

    @Override
    public void deleteTagsByPostId(long postId) {
        jdbcTemplate.update(DELETE_TAGS_BY_POST_ID_SQL, Map.of("postId", postId));
    }

    @Override
    public long countLikesByPostId(long postId) {
        return count(COUNT_LIKES_BY_POST_ID_SQL, postId);
    }

    @Override
    public long countCommentsByPostId(long postId) {
        return count(COUNT_COMMENTS_BY_POST_ID_SQL, postId);
    }

    @Override
    public void insertLike(long postId) {
        jdbcTemplate.update(INSERT_LIKE_SQL, Map.of("postId", postId));
    }

    @Override
    public void saveImage(long postId, String fileName, String contentType, byte[] data) {
        jdbcTemplate.update(DELETE_IMAGE_BY_POST_ID_SQL, Map.of("postId", postId));
        jdbcTemplate.update(INSERT_IMAGE_SQL, Map.of(
                "postId", postId,
                "fileName", fileName,
                "contentType", contentType,
                "data", data));
    }

    @Override
    public List<CommentDto> findCommentsByPostId(long postId) {
        return jdbcTemplate.query(FIND_COMMENTS_BY_POST_ID_SQL, Map.of("postId", postId),
                (resultSet, rowNumber) -> new CommentDto(
                        resultSet.getLong("id"),
                        resultSet.getString("text"),
                        resultSet.getLong("post_id")));
    }

    @Override
    public Optional<PostImageDto> findImageByPostId(long postId) {
        List<PostImageDto> images = jdbcTemplate.query(FIND_IMAGE_BY_POST_ID_SQL, Map.of("postId", postId),
                (resultSet, rowNumber) -> new PostImageDto(
                        resultSet.getString("file_name"),
                        resultSet.getString("content_type"),
                        resultSet.getBytes("data")));
        return images.stream().findFirst();
    }

    private long count(String sql, long postId) {
        Long count = jdbcTemplate.queryForObject(sql, Map.of("postId", postId), Long.class);
        return count == null ? 0L : count;
    }

    private List<PostPreview> attachTags(List<PostPreview> posts) {
        List<Long> postIds = posts.stream().map(PostPreview::id).toList();

        Map<Long, List<String>> tagsByPostId = jdbcTemplate.query(FIND_TAGS_SQL, Map.of("postIds", postIds), resultSet -> {
            Map<Long, List<String>> tags = new HashMap<>();
            while (resultSet.next()) {
                tags.computeIfAbsent(resultSet.getLong("post_id"), postId -> new ArrayList<>())
                        .add(resultSet.getString("name"));
            }
            return tags;
        });

        return posts.stream()
                .map(post -> post.withTags(tagsByPostId.getOrDefault(post.id(), List.of())))
                .toList();
    }

    /** Экранирует спецсимволы LIKE, чтобы "%" или "_" в поисковой строке не превращались в шаблон. */
    private static String likePattern(String search) {
        String escaped = search.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
