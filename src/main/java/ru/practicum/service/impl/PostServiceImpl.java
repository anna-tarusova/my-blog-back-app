package ru.practicum.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dao.PostRepository;
import ru.practicum.dao.TagRepository;
import ru.practicum.dto.PostCreateDto;
import ru.practicum.dto.PostDto;
import ru.practicum.dto.PostsPageDto;
import ru.practicum.model.Page;
import ru.practicum.model.Post;
import ru.practicum.model.Tag;
import ru.practicum.service.PostMapper;
import ru.practicum.service.PostService;

import java.util.List;

/**
 * Реализация работы с постами: лента (валидация параметров страницы, чтение из DAO, сборка ответа)
 * и создание поста вместе с тегами.
 */
@Service
public class PostServiceImpl implements PostService {

    private static final String TAG_PREFIX = "#";

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

