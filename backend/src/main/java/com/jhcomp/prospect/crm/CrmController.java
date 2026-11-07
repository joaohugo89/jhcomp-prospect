package com.jhcomp.prospect.crm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.lead.domain.LeadStatusTransitions;
import com.jhcomp.prospect.shared.web.InputPatterns;
import com.jhcomp.prospect.shared.web.PageRequests;
import com.jhcomp.prospect.shared.web.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * CRM: pipeline, board, deal, interactions and tasks (RF-040 to RF-043,
 * doc 05 §9).
 *
 * <p>Moving a card is {@code PATCH /leads/{id}/status} (D2); there is no deal
 * stage route.
 */
@RestController
public class CrmController {

    private final BoardService boardService;
    private final DealService dealService;
    private final InteractionService interactionService;
    private final TaskService taskService;

    CrmController(
            BoardService boardService,
            DealService dealService,
            InteractionService interactionService,
            TaskService taskService) {
        this.boardService = boardService;
        this.dealService = dealService;
        this.interactionService = interactionService;
        this.taskService = taskService;
    }

    // ---- Pipeline ---------------------------------------------------------

    @GetMapping("/pipeline-stages")
    public List<StageResponse> stages() {
        return PipelineStages.DEFAULT.stream().map(StageResponse::from).toList();
    }

    @GetMapping("/pipeline/board")
    public List<ColumnResponse> board(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String city) {
        return boardService.board(new BoardService.Filters(search, segment, city)).stream()
                .map(ColumnResponse::from)
                .toList();
    }

    // ---- Deal -------------------------------------------------------------

    @GetMapping("/leads/{leadId}/deal")
    public DealResponse deal(@PathVariable UUID leadId) {
        return DealResponse.from(dealService.find(leadId));
    }

    @PutMapping("/leads/{leadId}/deal")
    public DealResponse updateDeal(@PathVariable UUID leadId, @Valid @RequestBody DealRequest request) {
        return DealResponse.from(dealService.update(leadId, request.service(), request.estimatedValue(), request.lossReason()));
    }

    // ---- Interactions -----------------------------------------------------

    @PostMapping("/leads/{leadId}/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    public InteractionResponse recordInteraction(
            @PathVariable UUID leadId, @Valid @RequestBody InteractionRequest request) {
        return InteractionResponse.from(interactionService.record(leadId, new InteractionService.NewInteraction(
                request.type(), request.channel(), request.direction(), request.occurredAt(), request.outcome(),
                request.notes())));
    }

    @GetMapping("/leads/{leadId}/interactions")
    public PageResponse<InteractionResponse> interactions(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.from(interactionService.list(leadId, pageable), InteractionResponse::from);
    }

    // ---- Tasks ------------------------------------------------------------

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(@Valid @RequestBody NewTaskRequest request) {
        return TaskResponse.from(taskService.create(
                request.leadId(), request.title(), request.dueAt(), request.assignedUserId()));
    }

    @PatchMapping("/tasks/{taskId}")
    public TaskResponse updateTask(@PathVariable UUID taskId, @Valid @RequestBody UpdateTaskRequest request) {
        return TaskResponse.from(taskService.update(
                taskId, request.title(), request.dueAt(), request.assignedUserId(), request.status()));
    }

    @GetMapping("/leads/{leadId}/tasks")
    public PageResponse<TaskResponse> tasks(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.from(taskService.list(leadId, pageable), TaskResponse::from);
    }

    // ---- Contracts --------------------------------------------------------

    public record StageResponse(LeadStatus key, String name, int position, boolean terminal) {
        static StageResponse from(PipelineStages.Stage stage) {
            return new StageResponse(stage.key(), stage.name(), stage.position(), stage.terminal());
        }
    }

    /**
     * A compact Kanban card (doc 08 §8). {@code stageEnteredAt}: when the lead
     * reached its current stage, which drives the time-in-stage signal.
     */
    public record CardResponse(
            UUID leadId,
            String companyName,
            String segment,
            String city,
            String state,
            LeadStatus status,
            LeadPriority priority,
            Integer score,
            String service,
            BigDecimal estimatedValue,
            InteractionType lastInteractionType,
            Instant lastInteractionAt,
            String nextTaskTitle,
            Instant nextTaskDueAt,
            Instant stageEnteredAt,
            List<LeadStatus> allowedTransitions) {

        static CardResponse from(BoardService.Card card) {
            var lead = card.lead();
            var company = lead.getCompany();
            return new CardResponse(
                    lead.getId(),
                    company.getName(),
                    company.getSegment(),
                    company.getCity(),
                    company.getState(),
                    lead.getStatus(),
                    lead.getPriority(),
                    lead.getScore(),
                    card.deal() == null ? null : card.deal().getService(),
                    card.deal() == null ? null : card.deal().getEstimatedValue(),
                    card.lastInteraction() == null ? null : card.lastInteraction().getType(),
                    card.lastInteraction() == null ? null : card.lastInteraction().getOccurredAt(),
                    card.nextTask() == null ? null : card.nextTask().getTitle(),
                    card.nextTask() == null ? null : card.nextTask().getDueAt(),
                    card.stageEnteredAt(),
                    LeadStatusTransitions.allowedFrom(lead.getStatus()).stream()
                            .sorted(Comparator.naturalOrder())
                            .toList());
        }
    }

    /**
     * A board column. {@code totalValue}: the deals of the whole stage summed,
     * in BRL. {@code recentDays}: on the final columns, the closing window
     * shown; absent on the stages in progress.
     */
    public record ColumnResponse(
            StageResponse stage, long total, BigDecimal totalValue, Integer recentDays, List<CardResponse> cards) {
        static ColumnResponse from(BoardService.Column column) {
            return new ColumnResponse(
                    StageResponse.from(column.stage()),
                    column.total(),
                    column.totalValue(),
                    column.recentDays(),
                    column.cards().stream().map(CardResponse::from).toList());
        }
    }

    /** Money with two decimal places, never floating point (product rule 10). */
    public record DealRequest(
            @Size(max = 200) String service,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal estimatedValue,
            @Size(max = 500) String lossReason) {}

    /**
     * A deal. {@code outcome} is derived from the lead's status (D2):
     * {@code OPEN}, {@code WON} or {@code LOST}.
     */
    public record DealResponse(
            UUID leadId, String service, BigDecimal estimatedValue, String currency, String lossReason,
            String outcome, Instant updatedAt) {

        static DealResponse from(DealService.DealView view) {
            var deal = view.deal();
            String outcome = switch (view.leadStatus()) {
                case WON -> "WON";
                case LOST -> "LOST";
                default -> "OPEN";
            };
            return new DealResponse(deal.getLeadId(), deal.getService(), deal.getEstimatedValue(), deal.getCurrency(),
                    deal.getLossReason(), outcome, deal.getUpdatedAt());
        }
    }

    public record InteractionRequest(
            @NotNull InteractionType type,
            @NotNull InteractionChannel channel,
            @NotNull InteractionDirection direction,
            @NotNull Instant occurredAt,
            @Size(max = 200) String outcome,
            @Size(max = 2000) String notes) {}

    public record InteractionResponse(
            UUID id, InteractionType type, InteractionChannel channel, InteractionDirection direction,
            Instant occurredAt, String outcome, String notes, UUID createdByUserId) {

        static InteractionResponse from(InteractionEntity interaction) {
            return new InteractionResponse(
                    interaction.getId(), interaction.getType(), interaction.getChannel(), interaction.getDirection(),
                    interaction.getOccurredAt(), interaction.getOutcome(), interaction.getNotes(),
                    interaction.getCreatedByUserId());
        }
    }

    public record NewTaskRequest(
            @NotNull UUID leadId,
            @NotBlank @Size(max = 200) String title,
            Instant dueAt,
            UUID assignedUserId) {}

    /** The task's complete state: an absent field is cleared, not kept. */
    public record UpdateTaskRequest(
            @NotBlank @Size(max = 200) @Pattern(regexp = InputPatterns.NOT_BLANK_WHEN_PRESENT) String title,
            Instant dueAt,
            UUID assignedUserId,
            @NotNull TaskStatus status) {}

    public record TaskResponse(
            UUID id, UUID leadId, String title, Instant dueAt, TaskStatus status, UUID assignedUserId,
            Instant createdAt, Instant completedAt) {

        static TaskResponse from(TaskEntity task) {
            return new TaskResponse(task.getId(), task.getLeadId(), task.getTitle(), task.getDueAt(), task.getStatus(),
                    task.getAssignedUserId(), task.getCreatedAt(), task.getCompletedAt());
        }
    }
}
