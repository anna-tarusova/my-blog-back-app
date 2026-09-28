package ru.practicum.service.impl;

import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import ru.practicum.dao.CommentRepository;
import ru.practicum.dao.PostRepository;
import ru.practicum.dao.TagRepository;
import ru.practicum.dto.CommentCreateDto;
import ru.practicum.dto.CommentDto;
import ru.practicum.dto.CommentUpdateDto;
import ru.practicum.dto.PostCreateDto;
import ru.practicum.dto.PostDto;
import ru.practicum.dto.PostImageDto;
import ru.practicum.dto.PostUpdateDto;
import ru.practicum.dto.PostsPageDto;
import ru.practicum.exceptions.NotFoundException;
import ru.practicum.model.Comment;
import ru.practicum.model.Page;
import ru.practicum.model.Post;
import ru.practicum.model.Tag;
import ru.practicum.service.PostMapper;
import ru.practicum.service.PostService;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Реализация работы с постами: лента (валидация параметров страницы, чтение из DAO, сборка ответа)
 * и создание поста вместе с тегами.
 */
@Service
public class PostServiceImpl implements PostService {

    private static final String TAG_PREFIX = "#";

    /** Значение Content-Type по умолчанию, если multipart-часть не передала MIME-тип. */
    private static final String DEFAULT_IMAGE_CONTENT_TYPE = "application/octet-stream";

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final TagRepository tagRepository;
    private final PostMapper postMapper;

    public PostServiceImpl(PostRepository postRepository,
                           CommentRepository commentRepository,
                           TagRepository tagRepository,
                           PostMapper postMapper) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.tagRepository = tagRepository;
        this.postMapper = postMapper;
    }

    @Override
    public PostDto getPost(long id) {
        Optional<PostDto> post = postRepository.findPostDtoById(id);
        if (post.isEmpty())
        {
            throw new NotFoundException("post not found");
        }
        return post.get();
    }

    @Override
    public PostsPageDto getPostsPage(String search, int pageNumber, int pageSize) {
        if (pageNumber < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pageNumber must be >= 1");
        }
        if (pageSize < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pageSize must be >= 1");
        }

        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        long total = postRepository.countPosts(normalizedSearch);
        int lastPage = total == 0 ? 1 : (int) Math.ceil((double) total / pageSize);

        // Если запрошенная страница больше последней — возвращаем пустую страницу
        List<PostDto> posts;
        if (pageNumber > lastPage) {
            posts = List.of();
        } else {
            int offset = (pageNumber - 1) * pageSize;
            posts = postRepository.findPostsPage(normalizedSearch, pageSize, offset);
        }

        boolean hasPrev = pageNumber > 1;
        boolean hasNext = pageNumber < lastPage;

        return new PostsPageDto(posts, hasPrev, hasNext, lastPage);
    }

    @Override
    @Transactional
    public PostDto createPost(PostCreateDto request) {
        String title = requireField(request.title(), "title");
        String text = requireField(request.text(), "text");
        List<String> tags = normalizeTags(request.tags());

        Post savedPost = postRepository.save(Post.builder().title(title).text(text).build());

        tagRepository.saveAll(tags.stream()
                .map(tag -> Tag.builder().postId(savedPost.getId()).name(tag).build())
                .toList());

        return postMapper.toCreatedDto(savedPost, tags);
    }

    @Override
    @Transactional
    public PostDto updatePost(PostUpdateDto request) {
        if (request.id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Post id is required");
        }

        String title = requireField(request.title(), "title");
        String text = requireField(request.text(), "text");
        List<String> tags = normalizeTags(request.tags());

        // Проверяем существование поста
        Post existingPost = postRepository.findById(request.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        // Обновляем пост
        Post updatedPost = Post.builder()
                .id(existingPost.getId())
                .title(title)
                .text(text)
                .likesCount(existingPost.getLikesCount()) // сохраняем количество лайков
                .build();

        postRepository.save(updatedPost);

        // Удаляем старые теги
        tagRepository.deleteByPostId(request.id());

        // Создаем новые теги
        tagRepository.saveAll(tags.stream()
                .map(tag -> Tag.builder().postId(request.id()).name(tag).build())
                .toList());

        // Возвращаем актуальный PostDto с подсчитанными likesCount и commentsCount
        return postRepository.findPostDtoById(request.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }

    @Override
    @Transactional
    public void deletePost(Long id) {
        if (!postRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
        postRepository.deleteById(id);
    }

    @Override
    @Transactional
    public long incrementLikes(Long id) {
        int updated = postRepository.incrementLikes(id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
        return postRepository.findLikesCountById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }

    @Override
    @Transactional
    public void updateImage(Long id, byte[] image) {
        if (!postRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
        int updated = postRepository.updateImage(id, image);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
    }

    @Override
    public byte[] getImage(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        if (post.getImage() == null || post.getImage().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }

        return post.getImage();
    }

    @Override
    public List<CommentDto> getCommentsByPostId(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }

        return commentRepository.findByPostId(postId)
                .stream()
                .map(comment -> new CommentDto(
                        comment.getId(),
                        comment.getText(),
                        comment.getPostId()
                ))
                .toList();
    }

    @Override
    public CommentDto getCommentById(Long postId, Long commentId) {
        if (!postRepository.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }

        Comment comment = commentRepository.findByIdAndPostId(commentId, postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        return new CommentDto(
                comment.getId(),
                comment.getText(),
                comment.getPostId()
        );
    }

    @Override
    @Transactional
    public CommentDto createComment(Long postId, CommentCreateDto request) {
        // Проверяем, что postId в пути совпадает с postId в теле
        if (request.postId() == null || !postId.equals(request.postId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "postId in path does not match postId in body");
        }

        // Валидация текста
        if (request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment text is required");
        }

        // Проверяем существование поста
        if (!postRepository.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }

        // Создаём и сохраняем комментарий
        Comment savedComment = commentRepository.save(Comment.builder()
                .postId(postId)
                .text(request.text().trim())
                .build());

        return new CommentDto(
                savedComment.getId(),
                savedComment.getText(),
                savedComment.getPostId()
        );
    }

    private static CommentDto toCommentDto(Comment comment) {
        return new CommentDto(comment.getId(), comment.getText(), comment.getPostId());
    }

    @Override
    @Transactional
    public CommentDto updateComment(Long postId, Long commentId, CommentUpdateDto request) {
        // Проверяем совпадение id в пути и в теле
        if (!commentId.equals(request.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment id in path does not match id in body");
        }

        // Проверяем совпадение postId в пути и в теле
        if (!postId.equals(request.postId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Post id in path does not match postId in body");
        }

        // Валидация текста
        if (request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment text is required");
        }

        // Проверяем существование поста
        if (!postRepository.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }

        // Ищем комментарий, принадлежащий этому посту
        Comment comment = commentRepository.findByIdAndPostId(commentId, postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        // Обновляем текст
        Comment updatedComment = Comment.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .text(request.text().trim())
                .build();

        commentRepository.save(updatedComment);

        return new CommentDto(
                updatedComment.getId(),
                updatedComment.getText(),
                updatedComment.getPostId()
        );
    }

    @Override
    @Transactional
    public void deleteComment(Long postId, Long commentId) {
        // Проверяем существование поста
        if (!postRepository.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }

        // Ищем комментарий, принадлежащий этому посту
        Comment comment = commentRepository.findByIdAndPostId(commentId, postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        // Удаляем комментарий
        commentRepository.delete(comment);
    }

    /** Оба id (поста и комментария) обязаны быть заданы; иначе — 400. */
    private static void requireIdPair(Long id, Long commentId) {
        if (id == null || commentId == null) {
            throw new IllegalArgumentException("id and commentId must not be empty");
        }
    }

    /**
     * Комментарий, принадлежащий посту: пост и комментарий должны существовать,
     * комментарий другого поста считается ненайденным (404).
     */
    private Comment requireCommentOfPost(Long id, Long commentId) {
        Post post = requirePost(id);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("Comment not found: " + commentId));
        if (!post.getId().equals(comment.getPostId())) {
            throw new NoSuchElementException("Comment " + commentId + " does not belong to post " + id);
        }
        return comment;
    }

    /** Пост по id или {@code NoSuchElementException} (обрабатывается в {@code ApiExceptionHandler} как 404). */
    private Post requirePost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Post not found: " + id));
    }

    private static String requireField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return value.trim();
    }

    /** Теги приводятся к единому виду: без "#", без лишних пробелов, без пустых значений и повторов. */
    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            throw new IllegalArgumentException("tags must not be null");
        }
        return tags.stream()
                .filter(tag -> tag != null)
                .map(String::trim)
                .map(tag -> tag.startsWith(TAG_PREFIX) ? tag.substring(TAG_PREFIX.length()).trim() : tag)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    }
}

