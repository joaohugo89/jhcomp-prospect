package com.jhcomp.prospect.lead.presentation;

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

import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.company.presentation.SourceRequest;
import com.jhcomp.prospect.lead.application.LeadService;
import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadSpecifications;
import com.jhcomp.prospect.lead.presentation.LeadRequests.BulkAction;
import com.jhcomp.prospect.lead.presentation.LeadRequests.BulkUpdateRequest;
import com.jhcomp.prospect.lead.presentation.LeadRequests.BulkUpdateResponse;
import com.jhcomp.prospect.lead.presentation.LeadRequests.ChangePriorityRequest;
import com.jhcomp.prospect.lead.presentation.LeadRequests.ChangeStatusRequest;
import com.jhcomp.prospect.lead.presentation.LeadRequests.CreateLeadRequest;
import com.jhcomp.prospect.lead.presentation.LeadRequests.UpdateLeadRequest;
import com.jhcomp.prospect.lead.presentation.LeadResponses.LeadResponse;
import com.jhcomp.prospect.lead.presentation.LeadResponses.TimelineEntryResponse;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.web.PageRequests;
import com.jhcomp.prospect.shared.web.PageResponse;

import jakarta.validation.Valid;

/**
 * Leads (RF-011, RF-012, RF-013; doc 05 §5).
 *
 * <p>Moving a Kanban card is {@code PATCH /leads/{id}/status} (D2): there is
 * no deal stage route.
 */
@RestController
@RequestMapping("/leads")
public class LeadController {

    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "score", "score",
            "companyName", "company.normalizedName");

    private final LeadService leadService;

    LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeadResponse create(@Valid @RequestBody CreateLeadRequest request) {
        return LeadResponse.from(leadService.create(
                request.companyId(), request.priority(), SourceRequest.toDataOrNull(request.source())));
    }

    @GetMapping
    public PageResponse<LeadResponse> list(
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) LeadPriority priority,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer scoreMin,
            @RequestParam(required = false) Integer scoreMax,
            @RequestParam(required = false) SourceType source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {

        if (scoreMin != null && scoreMax != null && scoreMin > scoreMax) {
            throw new InvalidRequestException("scoreMin must not be greater than scoreMax");
        }

        var criteria = new LeadSpecifications.Criteria(
                status, priority, segment, city, search, scoreMin, scoreMax, source, null);
        var pageable = PageRequests.of(page, size, sort, SORTABLE, LeadService.DEFAULT_SORT);
        return PageResponse.from(leadService.list(criteria, pageable), LeadResponse::from);
    }

    @GetMapping("/{leadId}")
    public LeadResponse get(@PathVariable UUID leadId) {
        return LeadResponse.from(leadService.find(leadId));
    }

    @PatchMapping("/{leadId}")
    public LeadResponse update(@PathVariable UUID leadId, @Valid @RequestBody UpdateLeadRequest request) {
        return LeadResponse.from(leadService.assign(leadId, request.assignedUserId()));
    }

    @PatchMapping("/{leadId}/status")
    public LeadResponse changeStatus(@PathVariable UUID leadId, @Valid @RequestBody ChangeStatusRequest request) {
        return LeadResponse.from(leadService.changeStatus(
                leadId, request.status(), request.reason(), request.isContactConfirmed()));
    }

    /** Owner or priority across several leads at once (D31); all or nothing. */
    @PostMapping("/bulk")
    public BulkUpdateResponse bulk(@Valid @RequestBody BulkUpdateRequest request) {
        if (request.action() == BulkAction.PRIORITY && request.priority() == null) {
            throw new InvalidRequestException("priority is required for PRIORITY");
        }
        int updated = leadService.bulkUpdate(
                request.leadIds(),
                request.action() == BulkAction.PRIORITY ? request.priority() : null,
                request.action() == BulkAction.ASSIGN ? request.assignedUserId() : null);
        return new BulkUpdateResponse(updated);
    }

    @PatchMapping("/{leadId}/priority")
    public LeadResponse changePriority(@PathVariable UUID leadId, @Valid @RequestBody ChangePriorityRequest request) {
        return LeadResponse.from(leadService.changePriority(leadId, request.priority()));
    }

    /** The consolidated history (RF-013): statuses and origins, newest first. */
    @GetMapping("/{leadId}/timeline")
    public PageResponse<TimelineEntryResponse> timeline(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.from(leadService.timeline(leadId, pageable), TimelineEntryResponse::from);
    }
}
