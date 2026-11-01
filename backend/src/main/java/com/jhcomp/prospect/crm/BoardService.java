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
 * Quadro Kanban (RF-040, doc 08 §8): uma coluna por etapa, com o necessario
 * para os cards.
 *
 * <p>Cada coluna traz no maximo {@link #CARDS_PER_STAGE} cards, o total e o
 * valor dos negocios da etapa inteira; o restante fica na listagem de leads,
 * que e paginada (RNF-002). Ultima interacao, proxima tarefa, servico e
 * entrada na etapa vem numa consulta cada para o quadro inteiro, nao uma por
 * card. As colunas finais mostram so os encerrados nos ultimos
 * {@link #RECENT_CLOSED_DAYS} dias: sem isso cresceriam para sempre e
 * disputariam espaco com as etapas em andamento.
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

    /** {@code stageEnteredAt}: quando o lead chegou na etapa atual. */
    public record Card(
            LeadEntity lead, DealEntity deal, InteractionEntity lastInteraction, TaskEntity nextTask,
            Instant stageEnteredAt) {}

    /**
     * {@code totalValue}: soma do valor dos negocios de todos os leads da etapa,
     * nao so dos cards mostrados. {@code recentDays}: janela das colunas
     * finais; nulo nas etapas em andamento.
     */
    public record Column(
            PipelineStages.Stage stage, long total, BigDecimal totalValue, Integer recentDays, List<Card> cards) {}

    /** Filtros opcionais aplicados a todas as colunas. */
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
     * Soma do valor estimado dos negocios dos leads que atendem aos criterios
     * da etapa. Os mesmos filtros da listagem (e o escopo da organizacao)
     * valem aqui, pela mesma {@code Specification}.
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
