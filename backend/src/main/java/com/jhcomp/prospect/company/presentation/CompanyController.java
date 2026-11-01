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
 * Empresas (RF-010, RF-012, RF-014, RF-022; doc 05 §4).
 *
 * <p>Sem regra de negocio: valida a entrada e delega ao caso de uso. Nenhum
 * parametro carrega a organizacao.
 */
@RestController
@RequestMapping("/companies")
public class CompanyController {

    /** Campo publico de ordenacao para propriedade da entidade. */
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

    /** Segmentos em uso, para o filtro e para sugerir no cadastro (sem criar grafias novas por engano). */
    @GetMapping("/segments")
    public CompanySegmentsQuery.Segments segments() {
        return segmentsQuery.segments(organizationContext.requireOrganizationId());
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

    /** Historico de origem da empresa (RF-022). */
    @GetMapping("/{companyId}/sources")
    public PageResponse<SourceResponse> sources(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.from(companyService.sources(companyId, pageable), SourceResponse::from);
    }

    /** Possiveis duplicatas (RF-014). No maximo dez por sinal. */
    @GetMapping("/{companyId}/duplicates")
    public List<DuplicateCandidateResponse> duplicates(@PathVariable UUID companyId) {
        return companyService.duplicates(companyId).stream()
                .map(DuplicateCandidateResponse::from)
                .toList();
    }
}
