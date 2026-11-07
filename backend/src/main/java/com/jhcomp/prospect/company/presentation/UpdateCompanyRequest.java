package com.jhcomp.prospect.company.presentation;

import com.jhcomp.prospect.company.application.CompanyService.CompanyPatch;
import com.jhcomp.prospect.shared.web.InputPatterns;
import com.jhcomp.prospect.shared.web.WebUrl;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A partial company update (doc 05 §4).
 *
 * <p>An absent field keeps its value; an empty string clears an optional one.
 * Name, city and state are required and cannot be cleared.
 */
public record UpdateCompanyRequest(
        @Size(max = 200) @Pattern(regexp = InputPatterns.NOT_BLANK_WHEN_PRESENT, message = "must not be blank")
                String name,
        @Size(max = 200) String tradeName,
        @Size(max = 80) String segment,
        @Size(max = 120) @Pattern(regexp = InputPatterns.NOT_BLANK_WHEN_PRESENT, message = "must not be blank")
                String city,
        @Pattern(regexp = InputPatterns.STATE, message = "must be a two-letter state code") String state,
        @Size(max = 500) @WebUrl String websiteUrl,
        @Size(max = 500) @WebUrl String instagramUrl,
        @Pattern(regexp = InputPatterns.PHONE, message = "must be a phone number") String phone,
        @Email @Size(max = 254) String businessEmail,
        @Size(max = 300) String address,
        @Size(max = 2000) String notes) {

    public CompanyPatch toPatch() {
        return new CompanyPatch(
                name, tradeName, segment, city, state, websiteUrl, instagramUrl, phone, businessEmail, address, notes);
    }
}
