package com.jhcomp.prospect.crm;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.lead.application.LeadService;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadSpecifications;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadStatusChangeEntity;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadStatusChangeRepository;
import com.jhcomp.prospect.shared.security.OrganizationContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

/**
 * The Kanban board (RF-040, doc 08 §8): one column per stage, with what the
 * cards need.
 *
 * <p>Each column carries at most {@link #CARDS_PER_STAGE} cards, the total,
 * and the deal value of the whole stage; the rest stays in the lead listing,
 * which is paginated (RNF-002). Last interaction, next task, service and stage
 * entry each come from a single query for the whole board, not one per card.
 * The final columns show only what closed within the last
 * {@link #RECENT_CLOSED_DAYS} days: without that they would grow forever and
 * compete for space with the stages in progress.
 */
@Service
public class BoardService {

    static final int CARDS_PER_STAGE = 50;
    static final int RECENT_CLOSED_DAYS = 30;

    private final LeadService leadService;
    private final DealRepository dealRepository;
    private final InteractionRepository interactionRepository;
    private final TaskRepository taskRepository;
    private final LeadStatusChangeRepository statusChangeRepository;
    private final EntityManager entityManager;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    BoardService(
            LeadService leadService,
            DealRepository dealRepository,
            InteractionRepository interactionRepository,
            TaskRepository taskRepository,
            LeadStatusChangeRepository statusChangeRepository,
            EntityManager entityManager,
            OrganizationContext organizationContext,
            Clock clock) {
        this.leadService = leadService;
        this.dealRepository = dealRepository;
        this.interactionRepository = interactionRepository;
        this.taskRepository = taskRepository;
        this.statusChangeRepository = statusChangeRepository;
        this.entityManager = entityManager;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    /** {@code stageEnteredAt}: when the lead reached its current stage. */
    public record Card(
            LeadEntity lead, DealEntity deal, InteractionEntity lastInteraction, TaskEntity nextTask,
            Instant stageEnteredAt) {}

    /**
     * {@code totalValue}: the deal value summed over every lead in the stage,
     * not only the cards shown. {@code recentDays}: the final columns' window;
     * null on the stages in progress.
     */
    public record Column(
            PipelineStages.Stage stage, long total, BigDecimal totalValue, Integer recentDays, List<Card> cards) {}

    /** Optional filters applied to every column. */
    public record Filters(String search, String segment, String city) {}

    @Transactional(readOnly = true)
    public List<Column> board(Filters filters) {
        UUID organizationId = organizationContext.requireOrganizationId();
        Instant closedSince = clock.instant().minus(Duration.ofDays(RECENT_CLOSED_DAYS));
        var pageable = PageRequest.of(0, CARDS_PER_STAGE, Sort.by(Sort.Direction.DESC, LeadEntity::getUpdatedAt).and(Sort.by(LeadEntity::getId)));

        List<PipelineStages.Stage> stages = PipelineStages.DEFAULT;
        List<LeadSpecifications.Criteria> criteria = new ArrayList<>(stages.size());
        List<Page<LeadEntity>> pages = new ArrayList<>(stages.size());
        List<UUID> leadIds = new ArrayList<>();
        for (PipelineStages.Stage stage : stages) {
            var stageCriteria = new LeadSpecifications.Criteria(
                    stage.key(), null, filters.segment(), filters.city(), filters.search(), null, null, null,
                    stage.terminal() ? closedSince : null);
            Page<LeadEntity> page = leadService.list(stageCriteria, pageable);
            criteria.add(stageCriteria);
            pages.add(page);
            page.forEach(lead -> leadIds.add(lead.getId()));
        }

        Map<UUID, DealEntity> deals = leadIds.isEmpty() ? Map.of()
                : byLead(dealRepository.findByOrganizationIdAndLeadIdIn(organizationId, leadIds), DealEntity::getLeadId);
        Map<UUID, InteractionEntity> interactions = leadIds.isEmpty() ? Map.of()
                : byLead(interactionRepository.findLatestByLead(organizationId, leadIds), InteractionEntity::getLeadId);
        Map<UUID, TaskEntity> tasks = leadIds.isEmpty() ? Map.of()
                : byLead(taskRepository.findNextOpenByLead(organizationId, leadIds), TaskEntity::getLeadId);
        Map<UUID, Instant> entries = leadIds.isEmpty() ? Map.of()
                : statusChangeRepository.findLatestByLead(organizationId, leadIds).stream()
                        .collect(Collectors.toMap(LeadStatusChangeEntity::getLeadId, LeadStatusChangeEntity::getChangedAt));

        List<Column> columns = new ArrayList<>(stages.size());
        for (int i = 0; i < stages.size(); i++) {
            PipelineStages.Stage stage = stages.get(i);
            Page<LeadEntity> page = pages.get(i);
            columns.add(new Column(
                    stage,
                    page.getTotalElements(),
                    page.getTotalElements() == 0 ? BigDecimal.ZERO : stageValue(organizationId, criteria.get(i)),
                    stage.terminal() ? RECENT_CLOSED_DAYS : null,
                    page.stream()
                            .map(lead -> new Card(
                                    lead,
                                    deals.get(lead.getId()),
                                    interactions.get(lead.getId()),
                                    tasks.get(lead.getId()),
                                    entries.getOrDefault(lead.getId(), lead.getCreatedAt())))
                            .toList()));
        }
        return columns;
    }

    /**
     * The estimated deal value summed over the leads matching the stage's
     * criteria. The same filters as the listing — and the organisation scope —
     * apply here, through the same {@code Specification}.
     */
    private BigDecimal stageValue(UUID organizationId, LeadSpecifications.Criteria criteria) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> query = builder.createQuery(BigDecimal.class);
        Root<LeadEntity> lead = query.from(LeadEntity.class);
        Root<DealEntity> deal = query.from(DealEntity.class);
        query.select(builder.sum(deal.<BigDecimal>get("estimatedValue"))).where(
                LeadSpecifications.matching(organizationId, criteria).toPredicate(lead, query, builder),
                builder.equal(deal.get("organizationId"), organizationId),
                builder.equal(deal.get("leadId"), lead.get("id")));
        BigDecimal value = entityManager.createQuery(query).getSingleResult();
        return value == null ? BigDecimal.ZERO : value;
    }

    private static <T> Map<UUID, T> byLead(List<T> items, Function<T, UUID> leadId) {
        return items.stream().collect(Collectors.toMap(leadId, Function.identity(), (first, second) -> first));
    }
}
