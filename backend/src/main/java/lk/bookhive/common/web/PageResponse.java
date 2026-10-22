package lk.bookhive.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * The JSON shape for every paginated list. {@code page} starts at 0.
 *
 * <pre>{"content":[...],"page":0,"size":20,"totalElements":134,"totalPages":7}</pre>
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  /** Maps entities to DTOs, e.g. {@code PageResponse.from(books, bookMapper::toDto)}. */
  public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
    return from(page.map(mapper));
  }
}
