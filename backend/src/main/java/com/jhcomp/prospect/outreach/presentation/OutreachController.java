package com.jhcomp.prospect.outreach.presentation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.outreach.application.OutreachService;
import com.jhcomp.prospect.outreach.application.SuppressionService;
import com.jhcomp.prospect.outreach.domain.IdentifierType;
import com.jhcomp.prospect.outreach.domain.MessageStatus;
import com.jhcomp.prospect.outreach.domain.OutreachChannel;
import com.jhcomp.prospect.outreach.infrastructure.persistence.OutreachMessageEntity;
import com.jhcomp.prospect.outreach.infrastructure.persistence.SuppressionEntryEntity;
import com.jhcomp.prospect.shared.web.InputPatterns;
import com.jhcomp.prospect.shared.web.PageRequests;
import com.jhcomp.prospect.shared.web.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Outreach e lista de nao contato (doc 05 §10, D26).
 *
 * <p>{@code record-send} registra envio ja feito pelo operador; nao existe
 * rota que dispare mensagem.
 */
@RestController
public class OutreachController {

    private final OutreachService outreachService;
    private final SuppressionService suppressionService;

    OutreachController(OutreachService outreachService, SuppressionService suppressionService) {
        this.outreachService = outreachService;
        this.suppressionService = suppressionService;
    }

    // ---- Mensagens ----------------------------------------------------------

    @GetMapping("/leads/{leadId}/outreach-context")
    public ComposeContextResponse composeContext(@PathVariable UUID leadId) {
        return ComposeContextResponse.from(outreachService.composeContext(leadId));
    }

    @PostMapping("/leads/{leadId}/messages/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse generate(@PathVariable UUID leadId, @Valid @RequestBody GenerateRequest request) {
        return MessageResponse.from(outreachService.generate(leadId, request.channel()));
    }

    @GetMapping("/leads/{leadId}/messages")
    public PageResponse<MessageResponse> messages(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.from(outreachService.list(leadId, pageable), MessageResponse::from);
    }

    @PatchMapping("/messages/{messageId}")
    public MessageResponse edit(@PathVariable UUID messageId, @Valid @RequestBody EditRequest request) {
        return MessageResponse.from(outreachService.edit(messageId, request.subject(), request.body()));
    }

    @PostMapping("/messages/{messageId}/approve")
    public MessageResponse approve(
            @PathVariable UUID messageId, @RequestBody(required = false) ApproveRequest request) {
        return MessageResponse.from(outreachService.approve(
                messageId, request != null && Boolean.TRUE.equals(request.unverifiedClaimsReviewed())));
    }

    @PostMapping("/messages/{messageId}/record-send")
    public MessageResponse recordSend(@PathVariable UUID messageId, @Valid @RequestBody RecordSendRequest request) {
        return MessageResponse.from(outreachService.recordSend(
                messageId, request.result() == SendResult.SENT, request.sentAt(), request.notes(),
                Boolean.TRUE.equals(request.markLeadContacted())));
    }

    @PostMapping("/messages/{messageId}/cancel")
    public MessageResponse cancel(@PathVariable UUID messageId) {
        return MessageResponse.from(outreachService.cancel(messageId));
    }

    // ---- Nao contato --------------------------------------------------------

    /** Qualquer membro registra: o pedido precisa valer na hora (doc 06 §9). */
    @PostMapping("/suppression-entries")
    @ResponseStatus(HttpStatus.CREATED)
    public SuppressionResponse registerSuppression(@Valid @RequestBody SuppressionRequest request) {
        return SuppressionResponse.from(suppressionService.register(
                request.identifierType(), request.identifier(), request.channel(), request.reason(),
                request.requestedAt()));
    }

    /** Acesso restrito (doc 07 §10): a lista reune dados de contato de quem pediu para nao ser contatado. */
    @GetMapping("/suppression-entries")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<SuppressionResponse> suppressionEntries(
            @RequestParam(required = false) IdentifierType identifierType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.from(suppressionService.list(identifierType, search, pageable), SuppressionResponse::from);
    }

    /** Totais por tipo. Restrito como a lista: contagem por tipo ja e informacao da lista. */
    @GetMapping("/suppression-entries/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<IdentifierType, Long> suppressionSummary() {
        return suppressionService.countsByType();
    }

    /**
     * Qualquer membro confere se um contato esta bloqueado, antes de abordar
     * por fora do sistema. Por POST: o contato nao vai para a URL nem para log
     * de acesso. Responde so se esta bloqueado e em quais canais.
     */
    @PostMapping("/suppression-entries/check")
    public SuppressionService.CheckResult checkSuppression(@Valid @RequestBody SuppressionCheckRequest request) {
        return suppressionService.check(request.identifierType(), request.identifier());
    }

    public record SuppressionCheckRequest(
            @NotNull IdentifierType identifierType, @NotBlank @Size(max = 320) String identifier) {}

    // ---- Contratos ----------------------------------------------------------

    public enum SendResult {
        SENT,
        FAILED
    }

    public record GenerateRequest(@NotNull OutreachChannel channel) {}

    public record EditRequest(
            @Size(max = 200) String subject,
            @NotBlank @Size(max = 4000) String body) {}

    public record ApproveRequest(Boolean unverifiedClaimsReviewed) {}

    public record RecordSendRequest(
            @NotNull SendResult result,
            @NotNull Instant sentAt,
            @Size(max = 2000) String notes,
            Boolean markLeadContacted) {}

    public record SuppressionRequest(
            @NotNull IdentifierType identifierType,
            @NotBlank @Size(max = 320) String identifier,
            OutreachChannel channel,
            @NotBlank @Size(max = 500) @Pattern(regexp = InputPatterns.NOT_BLANK_WHEN_PRESENT) String reason,
            Instant requestedAt) {}

    public record MessageResponse(
            UUID id,
            UUID leadId,
            OutreachChannel channel,
            String subject,
            String body,
            List<String> unverifiedClaims,
            MessageStatus status,
            String generatedBy,
            String promptVersion,
            UUID createdByUserId,
            UUID approvedByUserId,
            Instant approvedAt,
            Instant sentAt,
            Instant createdAt,
            Instant updatedAt) {

        static MessageResponse from(OutreachMessageEntity message) {
            return new MessageResponse(
                    message.getId(), message.getLeadId(), message.getChannel(), message.getSubject(), message.getBody(),
                    message.getUnverifiedClaims(), message.getStatus(), message.getGeneratedBy(),
                    message.getPromptVersion(), message.getCreatedByUserId(), message.getApprovedByUserId(),
                    message.getApprovedAt(), message.getSentAt(), message.getCreatedAt(), message.getUpdatedAt());
        }
    }

    public record ChannelOptionResponse(OutreachChannel channel, String recipient, boolean blocked) {}

    public record ComposeContextResponse(
            String senderOrganization,
            String senderName,
            LeadStatus leadStatus,
            String companyName,
            String segment,
            String city,
            String state,
            List<String> confirmedFindings,
            List<ChannelOptionResponse> channels) {

        static ComposeContextResponse from(OutreachService.ComposeContext context) {
            return new ComposeContextResponse(
                    context.senderOrganization(), context.senderName(), context.leadStatus(), context.companyName(),
                    context.segment(), context.city(), context.state(), context.confirmedFindings(),
                    context.channels().stream()
                            .map(option -> new ChannelOptionResponse(option.channel(), option.recipient(), option.blocked()))
                            .toList());
        }
    }

    public record SuppressionResponse(
            UUID id,
            IdentifierType identifierType,
            String identifier,
            OutreachChannel channel,
            String reason,
            Instant requestedAt,
            UUID createdByUserId,
            Instant createdAt) {

        static SuppressionResponse from(SuppressionEntryEntity entry) {
            return new SuppressionResponse(
                    entry.getId(), entry.getIdentifierType(), entry.getIdentifier(), entry.getChannel(),
                    entry.getReason(), entry.getRequestedAt(), entry.getCreatedByUserId(), entry.getCreatedAt());
        }
    }
}
