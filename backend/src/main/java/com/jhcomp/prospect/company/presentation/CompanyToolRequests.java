package com.jhcomp.prospect.company.presentation;

import java.util.List;
import java.util.UUID;

import com.jhcomp.prospect.company.application.CompanyService;
import com.jhcomp.prospect.company.application.CompanyService.DuplicateProbe;
import com.jhcomp.prospect.shared.web.InputPatterns;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Pedidos das ferramentas da lista de empresas: duplicata ao digitar e edicao em lote. */
public final class CompanyToolRequests {

    private CompanyToolRequests() {
    }

    /**
     * O que o formulario tem ate agora. Por POST, e nao query string: telefone
     * e site nao vao parar em log de acesso.
     */
    public record DuplicateCheckRequest(
            @Size(max = 200) String name,
            @Size(max = 120) String city,
            @Size(max = 2) String state,
            @Size(max = 500) String websiteUrl,
            @Size(max = 40) String phone,
            UUID excludeCompanyId) {

        public DuplicateProbe toProbe() {
            return new DuplicateProbe(name, city, state, websiteUrl, phone, excludeCompanyId);
        }
    }

    /** Segmento e/ou cidade com estado; o que vier nulo nao muda. */
    public record BulkUpdateRequest(
            @NotEmpty @Size(max = CompanyService.MAX_BULK) List<@NotNull UUID> companyIds,
            @Size(max = 80) @Pattern(regexp = InputPatterns.NOT_BLANK_WHEN_PRESENT, message = "must not be blank")
                    String segment,
            @Size(max = 120) @Pattern(regexp = InputPatterns.NOT_BLANK_WHEN_PRESENT, message = "must not be blank")
                    String city,
            @Pattern(regexp = InputPatterns.STATE, message = "must be a two-letter state code") String state) {}
}
