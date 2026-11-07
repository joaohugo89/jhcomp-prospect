package com.jhcomp.prospect.crm;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.lead.application.LeadService;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.lead.domain.LeadStatusChanged;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.text.Texts;

/**
 * Deals (D2, RF-043).
 *
 * <p>A deal appears when the lead reaches proposal sent, or won without having
 * passed through a proposal. Before that there is no value or service to
 * record.
 */
@Service
public class DealService {

    private final DealRepository dealRepository;
    private final LeadService leadService;
    private final Clock clock;

    DealService(DealRepository dealRepository, LeadService leadService, Clock clock) {
        this.dealRepository = dealRepository;
        this.leadService = leadService;
        this.clock = clock;
    }

    /** The deal together with its lead, which is where the outcome comes from (D2). */
    public record DealView(DealEntity deal, LeadStatus leadStatus) {}

    @Transactional(readOnly = true)
    public DealView find(UUID leadId) {
        LeadEntity lead = leadService.find(leadId);
        DealEntity deal = dealRepository.findByOrganizationIdAndLeadId(lead.getOrganizationId(), lead.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Deal"));
        return new DealView(deal, lead.getStatus());
    }

    /** Updates service, value and loss reason. A deal only exists from the proposal onwards. */
    @Transactional
    public DealView update(UUID leadId, String service, BigDecimal estimatedValue, String lossReason) {
        LeadEntity lead = leadService.find(leadId);
        DealEntity deal = dealRepository.findByOrganizationIdAndLeadId(lead.getOrganizationId(), lead.getId())
                .orElseThrow(() -> new ConflictException("Deal exists only after a proposal is sent"));
        deal.update(Texts.blankToNull(service), estimatedValue, Texts.blankToNull(lossReason), clock.instant());
        return new DealView(deal, lead.getStatus());
    }

    /** Reacts to the status change in the same transaction: deal and status never diverge. */
    @EventListener
    void onStatusChanged(LeadStatusChanged event) {
        if (event.to() == LeadStatus.PROPOSAL_SENT || event.to() == LeadStatus.WON) {
            if (dealRepository.findByOrganizationIdAndLeadId(event.organizationId(), event.leadId()).isEmpty()) {
                dealRepository.save(new DealEntity(event.organizationId(), event.leadId(), clock.instant()));
            }
        }
        if (event.to() == LeadStatus.LOST) {
            dealRepository.findByOrganizationIdAndLeadId(event.organizationId(), event.leadId())
                    .ifPresent(deal -> deal.recordLossReasonIfAbsent(event.reason(), clock.instant()));
        }
    }
}
