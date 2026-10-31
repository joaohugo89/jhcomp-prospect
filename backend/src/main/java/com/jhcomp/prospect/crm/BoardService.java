package com.jhcomp.prospect.crm;

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
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Quadro Kanban (RF-040, doc 08 §8): uma coluna por etapa, com o necessario
 * para os cards.
 *
 * <p>Cada coluna traz no maximo {@link #CARDS_PER_STAGE} cards e o total; o
 * restante fica na listagem de leads, que e paginada (RNF-002). Ultima
 * interacao, proxima tarefa e servico vem em tres consultas para o quadro
 * inteiro, nao uma por card.
 */
@Service
public class BoardService {

    static final int CARDS_PER_STAGE = 50;

    private final LeadService leadService;
    private final DealRepository dealRepository;
    private final InteractionRepository interactionRepository;
    private final TaskRepository taskRepository;
    private final OrganizationContext organizationContext;

    BoardService(
            LeadService leadService,
            DealRepository dealRepository,
            InteractionRepository interactionRepository,
            TaskRepository taskRepository,
            OrganizationContext organizationContext) {
        this.leadService = leadService;
        this.dealRepository = dealRepository;
        this.interactionRepository = interactionRepository;
        this.taskRepository = taskRepository;
        this.organizationContext = organizationContext;
    }

    public record Card(LeadEntity lead, DealEntity deal, InteractionEntity lastInteraction, TaskEntity nextTask) {}

    public record Column(PipelineStages.Stage stage, long total, List<Card> cards) {}

    /** Filtros opcionais aplicados a todas as colunas. */
    public record Filters(String search, String segment, String city) {}

    @Transactional(readOnly = true)
    public List<Column> board(Filters filters) {
        UUID organizationId = organizationContext.requireOrganizationId();
        var pageable = PageRequest.of(0, CARDS_PER_STAGE, Sort.by(Sort.Direction.DESC, LeadEntity::getUpdatedAt).and(Sort.by(LeadEntity::getId)));

        List<PipelineStages.Stage> stages = PipelineStages.DEFAULT;
        List<Page<LeadEntity>> pages = new ArrayList<>(stages.size());
        List<UUID> leadIds = new ArrayList<>();
        for (PipelineStages.Stage stage : stages) {
            Page<LeadEntity> page = leadService.list(new LeadSpecifications.Criteria(
                    stage.key(), null, filters.segment(), filters.city(), filters.search(), null, null, null), pageable);
            pages.add(page);
            page.forEach(lead -> leadIds.add(lead.getId()));
        }

        Map<UUID, DealEntity> deals = leadIds.isEmpty() ? Map.of()
                : byLead(dealRepository.findByOrganizationIdAndLeadIdIn(organizationId, leadIds), DealEntity::getLeadId);
        Map<UUID, InteractionEntity> interactions = leadIds.isEmpty() ? Map.of()
                : byLead(interactionRepository.findLatestByLead(organizationId, leadIds), InteractionEntity::getLeadId);
        Map<UUID, TaskEntity> tasks = leadIds.isEmpty() ? Map.of()
                : byLead(taskRepository.findNextOpenByLead(organizationId, leadIds), TaskEntity::getLeadId);

        List<Column> columns = new ArrayList<>(stages.size());
        for (int i = 0; i < stages.size(); i++) {
            Page<LeadEntity> page = pages.get(i);
            columns.add(new Column(stages.get(i), page.getTotalElements(), page.stream()
                    .map(lead -> new Card(lead, deals.get(lead.getId()), interactions.get(lead.getId()), tasks.get(lead.getId())))
                    .toList()));
        }
        return columns;
    }

    private static <T> Map<UUID, T> byLead(List<T> items, Function<T, UUID> leadId) {
        return items.stream().collect(Collectors.toMap(leadId, Function.identity(), (first, second) -> first));
    }
}
