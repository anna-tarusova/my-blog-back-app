package ru.practicum.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dao.PostRepository;
import ru.practicum.dao.TagRepository;
import ru.practicum.dto.CommentDto;
import ru.practicum.dto.PostCreateDto;
import ru.practicum.dto.PostDto;
import ru.practicum.dto.PostImageDto;
import ru.practicum.dto.PostUpdateDto;
import ru.practicum.dto.PostsPageDto;
import ru.practicum.model.Page;
import ru.practicum.model.Post;
import ru.practicum.model.Tag;
import ru.practicum.service.PostMapper;
import ru.practicum.service.PostService;

import java.util.List;
import java.util.NoSuchElementException;

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
    private final TagRepository tagRepository;
    private final PostMapper postMapper;

    public PostServiceImpl(PostRepository postRepository, TagRepository tagRepository, PostMapper postMapper) {
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
        this.postMapper = postMapper;
    }

    @Override
    public PostsPageDto getPosts(String search, int pageNumber, int pageSize) {
        Page page = new Page(pageNumber, pageSize);
        String searchQuery = search == null ? "" : search.trim();

        long totalElements = postRepository.countBySearch(searchQuery);
        List<PostDto> posts = postMapper.toDtos(
                postRepository.findPreviewPage(searchQuery, page.pageSize(), page.offset()));

        return new PostsPageDto(
                posts,
                page.hasPrevious(),
                page.hasNext(totalElements),
                page.lastPageNumber(totalElements));
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
    public PostDto updatePost(Long id, PostUpdateDto request) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be empty");
        }
        if (request == null || !id.equals(request.id())) {
            throw new IllegalArgumentException("id in request body must match post id in path");
        }
        String title = requireField(request.title(), "title");
        String text = requireField(request.text(), "text");
        List<String> tags = normalizeTags(request.tags());

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Post not found: " + id));
        post.setTitle(title);
        post.setText(text);
        Post savedPost = postRepository.save(post);

        postRepository.deleteTagsByPostId(id);
        tagRepository.saveAll(tags.stream()
                .map(tag -> Tag.builder().postId(savedPost.getId()).name(tag).build())
                .toList());

        return new PostDto(
                savedPost.getId(),
                savedPost.getTitle(),
                savedPost.getText(),
                tags,
                postRepository.countLikesByPostId(id),
                postRepository.countCommentsByPostId(id));
    }

    @Override
    @Transactional
    public void deletePost(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be empty");
        }
        requirePost(id);
        postRepository.deleteById(id);
    }

    @Override
    @Transactional
    public long incrementLikes(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be empty");
        }
        requirePost(id);
        postRepository.insertLike(id);
        return postRepository.countLikesByPostId(id);
    }

    @Override
    @Transactional
    public void updateImage(Long id, String fileName, String contentType, byte[] data) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be empty");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("image file name must not be empty");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("image must not be empty");
        }
        requirePost(id);
        String resolvedContentType = (contentType == null || contentType.isBlank())
                ? DEFAULT_IMAGE_CONTENT_TYPE
                : contentType;
        postRepository.saveImage(id, fileName.trim(), resolvedContentType, data);
    }

    @Override
    public PostImageDto getPostImage(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be empty");
        }
        Post post = requirePost(id);
        return postRepository.findImageByPostId(post.getId())
                .orElseThrow(() -> new NoSuchElementException("Post image not found: " + id));
    }

    @Override
    public List<CommentDto> getComments(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be empty");
        }
        Post post = requirePost(id);
        return postRepository.findCommentsByPostId(post.getId());
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

