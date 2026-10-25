package com.jhcomp.prospect.shared.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Envelope de listagem paginada (RNF-002, doc 05 §1).
 *
 * <p>Nao se devolve o {@code Page} do Spring Data diretamente: sua
 * serializacao nao e um contrato estavel e expoe estrutura interna de
 * paginacao, o que ja gerou aviso em versoes recentes do Spring.
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
