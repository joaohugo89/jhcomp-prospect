package com.jhcomp.prospect.company.presentation;

import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity.CompanyData;
import com.jhcomp.prospect.shared.web.InputPatterns;
import com.jhcomp.prospect.shared.web.WebUrl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro de empresa (RF-010, doc 05 §4).
 *
 * <p>Nao ha {@code organizationId}: a organizacao vem da sessao.
 */
public record CreateCompanyRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String tradeName,
        @Size(max = 80) String segment,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Pattern(regexp = InputPatterns.STATE, message = "must be a two-letter state code") String state,
        @Size(max = 500) @WebUrl String websiteUrl,
        @Size(max = 500) @WebUrl String instagramUrl,
        @Pattern(regexp = InputPatterns.PHONE, message = "must be a phone number") String phone,
        @Email @Size(max = 254) String businessEmail,
        @Size(max = 300) String address,
        @Size(max = 2000) String notes,
        @Valid SourceRequest source) {

    public CompanyData toData() {
        return new CompanyData(
                name, tradeName, segment, city, state, websiteUrl, instagramUrl, phone, businessEmail, address, notes);
    }
}
