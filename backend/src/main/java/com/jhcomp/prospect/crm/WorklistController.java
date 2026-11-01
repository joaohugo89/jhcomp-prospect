package com.jhcomp.prospect.crm;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.OrganizationContext;
import com.jhcomp.prospect.shared.web.PageResponse;

/**
 * Fila de trabalho de leads (doc 05 §5, D31). Paginada (RNF-002), com teto de
 * pagina; a organizacao vem da sessao, nunca do pedido.
 */
@RestController
@RequestMapping("/leads")
public class WorklistController {

    static final int MAX_PAGE_SIZE = 100;

    private final LeadWorklistQuery query;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    WorklistController(LeadWorklistQuery query, OrganizationContext organizationContext, Clock clock) {
        this.query = query;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    /** Filtros da fila, lidos da query string; ausentes nao filtram. */
    public record WorklistParams(
            String search,
            LeadStatus status,
            LeadPriority priority,
            String city,
            String segment,
            Boolean noSegment,
            SourceType source,
            UUID assignee,
            Boolean unassigned,
            Integer scoreMin,
            Integer scoreMax,
            LeadWorklistQuery.Focus focus,
            LeadWorklistQuery.SortField sort,
            String direction,
            Integer page,
            Integer size) {}

    @GetMapping("/worklist")
    public PageResponse<LeadWorklistQuery.Item> worklist(@ModelAttribute WorklistParams params) {
        if (params.scoreMin() != null && params.scoreMax() != null && params.scoreMin() > params.scoreMax()) {
            throw new InvalidRequestException("scoreMin must not be greater than scoreMax");
        }
        if (params.direction() != null && !params.direction().equals("asc") && !params.direction().equals("desc")) {
            throw new InvalidRequestException("direction must be asc or desc");
        }
        int page = params.page() == null ? 0 : Math.max(params.page(), 0);
        int size = params.size() == null ? 20 : Math.clamp(params.size(), 1, MAX_PAGE_SIZE);
        var criteria = new LeadWorklistQuery.Criteria(
                null,
                params.search(),
                params.status(),
                params.priority(),
                params.city(),
                params.segment(),
                Boolean.TRUE.equals(params.noSegment()),
                params.source(),
                params.assignee(),
                Boolean.TRUE.equals(params.unassigned()),
                params.scoreMin(),
                params.scoreMax(),
                params.focus(),
                params.sort(),
                "asc".equals(params.direction()));
        var result = query.find(
                organizationContext.requireOrganizationId(), criteria, clock.instant(), PageRequest.of(page, size));
        return PageResponse.from(result, item -> item);
    }

    /**
     * Resumo de um lead: os mesmos campos de uma linha da fila (responsavel,
     * valor, proxima tarefa, entrada na etapa). Lead de outra organizacao
     * responde 404, como se nao existisse.
     */
    @GetMapping("/{leadId}/summary")
    public LeadWorklistQuery.Item summary(@PathVariable UUID leadId) {
        var criteria = new LeadWorklistQuery.Criteria(
                leadId, null, null, null, null, null, false, null, null, false, null, null, null, null, false);
        return query.find(organizationContext.requireOrganizationId(), criteria, clock.instant(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Lead"));
    }
}
