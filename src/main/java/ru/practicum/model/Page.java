package ru.practicum.model;

/**
 * Объект пагинации: номер страницы и её размер.
 * Вся арифметика постраничной выдачи собрана здесь.
 */
public record Page(int pageNumber, int pageSize) {

    public Page {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be greater than 0, but was " + pageNumber);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be greater than 0, but was " + pageSize);
        }
    }

    /** Смещение первой записи страницы (SQL OFFSET). */
    public long offset() {
        return (long) (pageNumber - 1) * pageSize;
    }

    /** Номер последней страницы для указанного общего количества записей (минимум 1). */
    public int lastPageNumber(long totalElements) {
        return (int) Math.max(1, (totalElements + pageSize - 1) / pageSize);
    }

    public boolean hasPrevious() {
        return pageNumber > 1;
    }

    public boolean hasNext(long totalElements) {
        return pageNumber < lastPageNumber(totalElements);
    }
}
