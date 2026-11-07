package com.jhcomp.prospect.company.presentation;

import java.time.Instant;

import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.company.infrastructure.persistence.LeadSourceEntity.SourceData;
import com.jhcomp.prospect.shared.web.WebUrl;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * An origin supplied by the operator (RF-020).
 *
 * <p>There is no type field: on this route the origin is always
 * {@code MANUAL}. Accepting the type from the client would allow hand-typed
 * data to be recorded as an import or an integration, and provenance would
 * stop being trustworthy.
 */
public record SourceRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 300) String reference,
        @Size(max = 1000) @WebUrl String url,
        @PastOrPresent Instant collectedAt,
        @Size(max = 1000) String note) {

    public SourceData toData() {
        return new SourceData(SourceType.MANUAL, name, reference, url, collectedAt, note);
    }

    /** The origin is optional: when absent, the use case applies the default manual origin. */
    public static SourceData toDataOrNull(SourceRequest request) {
        return request == null ? null : request.toData();
    }
}
