package ru.practicum.dto;

/**
 * Read-модель картинки поста (таблица {@code post_images}): имя файла, MIME-тип и содержимое.
 * Возвращается DAO-слоем; в JSON не сериализуется — ответ API {@code GET /api/posts/{id}/image}
 * отдаётся сырыми байтами с MIME-типом из этой модели.
 *
 * @param fileName    имя файла, с которым картинка была загружена
 * @param contentType MIME-тип картинки, сохранённый при загрузке
 * @param data        содержимое файла
 */
public record PostImageDto(String fileName, String contentType, byte[] data) {
}

