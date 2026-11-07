package com.jhcomp.prospect.crm;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.company.application.CompanyService;
import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.company.presentation.CompanyResponses.CompanyResponse;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.OrganizationContext;
import com.jhcomp.prospect.shared.web.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Companies as a working tool (doc 05 §4, D32): the list with lead and
 * activity, the commercial cycles, deleting what was entered by mistake,
 * merging a duplicate, and undoing an import. The organisation comes from the
 * session; a resource from another organisation answers 404.
 */
@RestController
public class CompanyWorkspaceController {

    private final CompanyWorklistQuery worklist;
    private final CompanyRemovalService removal;
    private final CompanyMergeService merge;
    private final CompanyService companyService;
    private final OrganizationContext organizationContext;

    CompanyWorkspaceController(
            CompanyWorklistQuery worklist,
            CompanyRemovalService removal,
            CompanyMergeService merge,
            CompanyService companyService,
            OrganizationContext organizationContext) {
        this.worklist = worklist;
        this.removal = removal;
        this.merge = merge;
        this.companyService = companyService;
        this.organizationContext = organizationContext;
    }

    public record WorklistParams(
            String search,
            String segment,
            Boolean noSegment,
            String city,
            SourceType source,
            CompanyWorklistQuery.Focus focus,
            CompanyWorklistQuery.SortField sort,
            String direction,
            Integer page,
            Integer size) {}

    @GetMapping("/companies/worklist")
    public PageResponse<CompanyWorklistQuery.Item> worklist(@ModelAttribute WorklistParams params) {
        if (params.direction() != null && !params.direction().equals("asc") && !params.direction().equals("desc")) {
            throw new InvalidRequestException("direction must be asc or desc");
        }
        var criteria = new CompanyWorklistQuery.Criteria(
                null,
                params.search(),
                params.segment(),
                Boolean.TRUE.equals(params.noSegment()),
                params.city(),
                params.source(),
                params.focus(),
                params.sort(),
                "asc".equals(params.direction()));
        var result = worklist.find(organizationContext.requireOrganizationId(), criteria, page(params.page(), params.size()));
        return PageResponse.from(result, item -> item);
    }

    /** The fields of one list row, for the preview and the detail header. */
    @GetMapping("/companies/{companyId}/summary")
    public CompanyWorklistQuery.Item summary(@PathVariable UUID companyId) {
        var criteria = new CompanyWorklistQuery.Criteria(companyId, null, null, false, null, null, null, null, false);
        return worklist.find(organizationContext.requireOrganizationId(), criteria, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Company"));
    }

    @GetMapping("/companies/{companyId}/leads")
    public PageResponse<CompanyWorklistQuery.LeadCycle> leads(
            @PathVariable UUID companyId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        UUID organizationId = organizationContext.requireOrganizationId();
        companyService.find(companyId);
        return PageResponse.from(worklist.leads(organizationId, companyId, page(page, size)), cycle -> cycle);
    }

    /** Whether the caller may delete the company right now, and why not. */
    @GetMapping("/companies/{companyId}/removal")
    public CompanyRemovalService.Eligibility removal(@PathVariable UUID companyId) {
        return removal.eligibility(companyId);
    }

    /** The typed name confirms the intent, as in the dialog on screen. */
    public record DeleteCompanyRequest(@NotBlank @Size(max = 200) String confirmationName) {}

    @DeleteMapping("/companies/{companyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID companyId, @Valid @RequestBody DeleteCompanyRequest request) {
        removal.delete(companyId, request.confirmationName());
    }

    public record MergeRequest(@NotNull UUID sourceCompanyId) {}

    public record MergeResponse(CompanyResponse company, List<String> filledFields, int movedLeads) {}

    /** The company in the path absorbs {@code sourceCompanyId}, which ends up archived. */
    @PostMapping("/companies/{companyId}/merge")
    public MergeResponse merge(@PathVariable UUID companyId, @Valid @RequestBody MergeRequest request) {
        var result = merge.merge(companyId, request.sourceCompanyId());
        return new MergeResponse(CompanyResponse.from(result.target()), result.filledFields(), result.movedLeads());
    }

    @GetMapping("/imports/{importId}/undo")
    public CompanyRemovalService.UndoResult previewUndo(@PathVariable UUID importId) {
        return removal.previewUndo(importId);
    }

    @PostMapping("/imports/{importId}/undo")
    public CompanyRemovalService.UndoResult undo(@PathVariable UUID importId) {
        return removal.undoImport(importId);
    }

    private static PageRequest page(Integer page, Integer size) {
        return PageRequest.of(
                page == null ? 0 : Math.max(page, 0),
                size == null ? 20 : Math.clamp(size, 1, WorklistController.MAX_PAGE_SIZE));
    }
}
