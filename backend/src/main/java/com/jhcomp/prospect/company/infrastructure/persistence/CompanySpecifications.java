package com.jhcomp.prospect.company.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.jhcomp.prospect.shared.normalization.ContactNormalizer;
import com.jhcomp.prospect.shared.persistence.LikePatterns;

import jakarta.persistence.criteria.Predicate;

/**
 * Filtros combinaveis da listagem de empresas (RF-012).
 *
 * <p>O escopo de organizacao nao e um filtro opcional: e aplicado sempre, e
 * nao existe forma de montar a consulta sem ele.
 */
public final class CompanySpecifications {

    private CompanySpecifications() {
    }

    /** Criterios de listagem. Campos nulos ou vazios nao filtram. */
    public record Criteria(String search, String segment, String city, String state, boolean includeArchived) {}

    public static Specification<CompanyEntity> matching(UUID organizationId, Criteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationId"), organizationId));

            if (!criteria.includeArchived()) {
                predicates.add(builder.isNull(root.get("archivedAt")));
            }

            // A busca compara com o nome normalizado, pela mesma regra usada
            // ao gravar (D10): "salao" encontra "Salão", e espaco excedente no
            // termo nao impede o resultado.
            ContactNormalizer.name(criteria.search()).ifPresent(term -> predicates.add(builder.like(
                    root.get("normalizedName"), LikePatterns.contains(term), LikePatterns.ESCAPE)));

            if (hasText(criteria.segment())) {
                // Mesmo segmento com outra caixa ou espaco nas pontas (CompanyOriginSql.SEGMENT_EQUALS).
                predicates.add(builder.equal(
                        builder.lower(builder.trim(root.get("segment"))),
                        criteria.segment().strip().toLowerCase(Locale.ROOT)));
            }
            if (hasText(criteria.city())) {
                predicates.add(builder.equal(
                        builder.lower(root.get("city")), criteria.city().strip().toLowerCase(Locale.ROOT)));
            }
            if (hasText(criteria.state())) {
                predicates.add(builder.equal(root.get("state"), criteria.state().strip().toUpperCase(Locale.ROOT)));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
