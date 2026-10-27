package com.jhcomp.prospect.company.presentation;

import java.time.Instant;

import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.company.infrastructure.persistence.LeadSourceEntity.SourceData;
import com.jhcomp.prospect.shared.web.InputPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Origem informada pelo operador (RF-020).
 *
 * <p>Nao ha campo de tipo: por esta rota a origem e sempre {@code MANUAL}.
 * Aceitar o tipo do cliente permitiria registrar como importacao ou integracao
 * um dado digitado a mao, e a proveniencia deixaria de ser confiavel.
 */
public record SourceRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 300) String reference,
        @Size(max = 1000) @Pattern(regexp = InputPatterns.WEB_URL, message = "must be an http or https URL") String url,
        @PastOrPresent Instant collectedAt,
        @Size(max = 1000) String note) {

    public SourceData toData() {
        return new SourceData(SourceType.MANUAL, name, reference, url, collectedAt, note);
    }

    /** Origem opcional: ausente, o caso de uso aplica a origem manual padrao. */
    public static SourceData toDataOrNull(SourceRequest request) {
        return request == null ? null : request.toData();
    }
}
