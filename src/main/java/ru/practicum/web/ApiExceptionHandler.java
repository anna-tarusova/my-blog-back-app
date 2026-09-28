package ru.practicum.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import ru.practicum.exceptions.NotFoundException;

import java.util.NoSuchElementException;

/**
 * Единая обработка ошибок запроса: некорректные параметры ленты и тела запроса отдают 400 с текстовым
 * описанием, отсутствующий ресурс — 404. Остальные исключения не перехватываются,
 * чтобы не ломать штатные ответы Spring MVC (404 от обработки маршрутов и т.д.).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({
            NotFoundException.class,
            HttpClientErrorException.NotFound.class})
    public ResponseEntity<String> handle404(Exception exception) {
        log.debug("Request rejected with 404: {}", exception.getMessage());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<String> handleBadRequest(Exception exception) {
        log.debug("Request rejected with 400: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body(exception.getMessage());
    }

    /** Пост (ресурс) не найден по id из пути запроса — например, {@code PUT /api/posts/999}. */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException exception) {
        log.debug("Resource not found: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body(exception.getMessage());
    }

    /** Файл из multipart-части превысил лимит из {@code <multipart-config>} (10 МБ на файл). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        log.debug("Multipart upload rejected: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Uploaded file exceeds the maximum allowed size");
    }

    /** Тело запроса заявлено как multipart/form-data, но разобрать его части не удалось. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<String> handleMultipartFailure(MultipartException exception) {
        log.debug("Malformed multipart request: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body(exception.getMessage());
    }
}
