package com.jhcomp.prospect.shared.web;

import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

/**
 * Pagination and sorting received by the API (RNF-002, doc 05 §1).
 *
 * <p>Sorting arrives as {@code field,direction} and only accepts fields from
 * an explicit list, translated into the entity's property. Passing the
 * client's text straight to Spring Data would allow sorting by any column,
 * including those that are not exposed, and would couple the public name to
 * the internal one.
 */
public final class PageRequests {

    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    /**
     * @param allowedSort public field to entity property
     * @param defaultSort used when {@code sort} is not supplied
     */
    public static Pageable of(int page, int size, String sort, Map<String, String> allowedSort, Sort defaultSort) {
        // A cap on page size: an arbitrary size would allow the whole base to
        // be extracted in one request (doc 05 §12).
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

        // A stable tiebreak by id: without it, records sharing a value can
        // swap pages between two requests.
        return Sort.by(direction, property).and(Sort.by(AssignedIdEntity::getId));
    }
}
