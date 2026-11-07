package com.jhcomp.prospect.company.presentation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.company.application.CompanyService;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanySegmentsQuery;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanySpecifications;
import com.jhcomp.prospect.company.presentation.CompanyResponses.CompanyResponse;
import com.jhcomp.prospect.company.presentation.CompanyResponses.DuplicateCandidateResponse;
import com.jhcomp.prospect.company.presentation.CompanyResponses.SourceResponse;
import com.jhcomp.prospect.shared.security.OrganizationContext;
import com.jhcomp.prospect.shared.web.PageRequests;
import com.jhcomp.prospect.shared.web.PageResponse;

import jakarta.validation.Valid;

/**
 * Companies (RF-010, RF-012, RF-014, RF-022; doc 05 §4).
 *
 * <p>No business logic: it validates the input and delegates to the use case.
 * No parameter carries the organisation.
 */
@RestController
@RequestMapping("/companies")
public class CompanyController {

    /** A public sort field mapped to an entity property. */
    private static final Map<String, String> SORTABLE = Map.of(
            "name", "normalizedName",
            "city", "city",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final CompanyService companyService;
    private final CompanySegmentsQuery segmentsQuery;
    private final OrganizationContext organizationContext;

    CompanyController(
            CompanyService companyService,
            CompanySegmentsQuery segmentsQuery,
            OrganizationContext organizationContext) {
        this.companyService = companyService;
        this.segmentsQuery = segmentsQuery;
        this.organizationContext = organizationContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@Valid @RequestBody CreateCompanyRequest request) {
        return CompanyResponse.from(
                companyService.create(request.toData(), SourceRequest.toDataOrNull(request.source())));
    }

    @GetMapping
    public PageResponse<CompanyResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {

        var criteria = new CompanySpecifications.Criteria(search, segment, city, state, includeArchived);
        var pageable = PageRequests.of(page, size, sort, SORTABLE, CompanyService.DEFAULT_SORT);
        return PageResponse.from(companyService.list(criteria, pageable), CompanyResponse::from);
    }

    /** Segments in use, for the filter and for suggestions in the form, so no new spelling is created by accident. */
    @GetMapping("/segments")
    public CompanySegmentsQuery.Segments segments() {
        return segmentsQuery.segments(organizationContext.requireOrganizationId());
    }

    /** Possible duplicates while the form is typed (RF-014). It only suggests. */
    @PostMapping("/duplicate-check")
    public List<DuplicateCandidateResponse> duplicateCheck(
            @Valid @RequestBody CompanyToolRequests.DuplicateCheckRequest request) {
        return companyService.probeDuplicates(request.toProbe()).stream()
                .map(DuplicateCandidateResponse::from)
                .toList();
    }

    /** Segment or city across several companies at once. */
    @PostMapping("/bulk")
    public CompanyService.BulkResult bulk(@Valid @RequestBody CompanyToolRequests.BulkUpdateRequest request) {
        return companyService.bulkUpdate(request.companyIds(), request.segment(), request.city(), request.state());
    }

    @GetMapping("/{companyId}")
    public CompanyResponse get(@PathVariable UUID companyId) {
        return CompanyResponse.from(companyService.find(companyId));
    }

    @PatchMapping("/{companyId}")
    public CompanyResponse update(@PathVariable UUID companyId, @Valid @RequestBody UpdateCompanyRequest request) {
        return CompanyResponse.from(companyService.update(companyId, request.toPatch()));
    }

    @PostMapping("/{companyId}/archive")
    public CompanyResponse archive(@PathVariable UUID companyId) {
        return CompanyResponse.from(companyService.archive(companyId));
    }

    /** The company's provenance history (RF-022). */
    @GetMapping("/{companyId}/sources")
    public PageResponse<SourceResponse> sources(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.from(companyService.sources(companyId, pageable), SourceResponse::from);
    }

    /** Possible duplicates (RF-014). At most ten per signal. */
    @GetMapping("/{companyId}/duplicates")
    public List<DuplicateCandidateResponse> duplicates(@PathVariable UUID companyId) {
        return companyService.duplicates(companyId).stream()
                .map(DuplicateCandidateResponse::from)
                .toList();
    }
}
