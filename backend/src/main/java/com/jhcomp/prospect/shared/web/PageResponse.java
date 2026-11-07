package com.jhcomp.prospect.shared.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * The paginated listing envelope (RNF-002, doc 05 §1).
 *
 * <p>Spring Data's {@code Page} is not returned directly: its serialisation
 * is not a stable contract and exposes internal pagination structure, which
 * recent Spring versions already warn about.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
