package com.jhcomp.prospect.shared.web;

import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.jhcomp.prospect.shared.error.InvalidRequestException;

/**
 * Paginacao e ordenacao recebidas da API (RNF-002, doc 05 §1).
 *
 * <p>A ordenacao chega como {@code campo,direcao} e so aceita campos de uma
 * lista explicita, traduzidos para a propriedade da entidade. Repassar o texto
 * do cliente direto ao Spring Data permitiria ordenar por qualquer coluna,
 * inclusive as que nao sao expostas, e o nome publico ficaria acoplado ao nome
 * interno.
 */
public final class PageRequests {

    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    /**
     * @param allowedSort campo publico para propriedade da entidade
     * @param defaultSort usada quando {@code sort} nao e informado
     */
    public static Pageable of(int page, int size, String sort, Map<String, String> allowedSort, Sort defaultSort) {
        // Teto no tamanho da pagina: um size arbitrario permitiria extrair a
        // base inteira numa requisicao (doc 05 §12).
        int effectiveSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int effectivePage = Math.max(page, 0);
        return PageRequest.of(effectivePage, effectiveSize, parseSort(sort, allowedSort, defaultSort));
    }

    private static Sort parseSort(String sort, Map<String, String> allowedSort, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }

        String[] parts = sort.split(",", -1);
        String property = allowedSort.get(parts[0].strip());
        if (property == null || parts.length > 2) {
            throw new InvalidRequestException("Unsupported sort: " + String.join(", ", allowedSort.keySet()));
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = switch (parts[1].strip().toLowerCase(Locale.ROOT)) {
                case "asc" -> Sort.Direction.ASC;
                case "desc" -> Sort.Direction.DESC;
                default -> throw new InvalidRequestException("Sort direction must be asc or desc");
            };
        }

        // Desempate estavel por id: sem ele, registros com o mesmo valor podem
        // trocar de pagina entre duas requisicoes.
        return Sort.by(direction, property).and(Sort.by("id"));
    }
}
