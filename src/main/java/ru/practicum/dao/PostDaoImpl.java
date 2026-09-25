package ru.practicum.dao;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.practicum.dto.PostPreview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
