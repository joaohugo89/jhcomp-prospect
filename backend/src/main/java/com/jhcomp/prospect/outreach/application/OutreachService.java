package com.jhcomp.prospect.outreach.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhcomp.prospect.analysis.application.AnalysisService;
import com.jhcomp.prospect.analysis.domain.Criterion;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.crm.InteractionChannel;
import com.jhcomp.prospect.crm.InteractionDirection;
import com.jhcomp.prospect.crm.InteractionService;
import com.jhcomp.prospect.crm.InteractionType;
import com.jhcomp.prospect.identity.application.OrganizationQueryService;
import com.jhcomp.prospect.lead.application.LeadService;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;
import com.jhcomp.prospect.outreach.domain.AIProvider;
import com.jhcomp.prospect.outreach.domain.AIProviderException;
import com.jhcomp.prospect.outreach.domain.AIResponse;
import com.jhcomp.prospect.outreach.domain.MessageContext;
import com.jhcomp.prospect.outreach.domain.MessageDraft;
import com.jhcomp.prospect.outreach.domain.MessageGenerationRequest;
import com.jhcomp.prospect.outreach.domain.MessageStatus;
import com.jhcomp.prospect.outreach.domain.OutreachChannel;
import com.jhcomp.prospect.outreach.infrastructure.persistence.OutreachMessageEntity;
import com.jhcomp.prospect.outreach.infrastructure.persistence.OutreachMessageRepository;
import com.jhcomp.prospect.shared.audit.AuditAction;
import com.jhcomp.prospect.shared.audit.AuditOutcome;
import com.jhcomp.prospect.shared.audit.AuditService;
import com.jhcomp.prospect.shared.audit.AuditService.AuditEntry;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.error.TooManyAttemptsException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Mensagens de abordagem: gerar, revisar, aprovar e registrar envio
 * (RF-050 a RF-052, doc 06 §8, D26).
 *
 * <p>Nenhum caminho daqui envia mensagem: {@code recordSend} registra um
 * envio que o operador ja fez (regras 1 e 5). O bloqueio de contato e
 * conferido ao gerar, ao aprovar e ao registrar envio (regra 4).
 */
@Service
public class OutreachService {

    private static final Logger log = LoggerFactory.getLogger(OutreachService.class);

    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    /**
     * Descricoes fixas dos achados que sao oportunidade de servico. Criterios
     * de qualificacao interna (segmento, area atendida, cadastro) nao sao
     * assunto da mensagem e ficam fora do mapa.
     */
    private static final Map<Criterion, String> OPPORTUNITIES = new EnumMap<>(Map.of(
            Criterion.SITE_ABSENT, "a empresa não tem site",
            Criterion.NO_HTTPS, "o site não abre com conexão segura (HTTPS)",
            Criterion.NO_MOBILE_VIEWPORT, "a página inicial do site não está adaptada para celular",
            Criterion.NO_CONTACT_LINK, "a página inicial do site não tem link direto de contato",
            Criterion.NO_ONLINE_BOOKING_OR_CATALOG, "a empresa não tem agendamento ou catálogo online"));

    private final OutreachMessageRepository repository;
    private final SuppressionService suppressionService;
    private final LeadService leadService;
    private final AnalysisService analysisService;
    private final InteractionService interactionService;
    private final OrganizationQueryService organizationQueryService;
    private final AIProvider aiProvider;
    private final OutreachPrompt prompt;
    private final GeneratedOutputParser outputParser;
    private final GenerationRateLimiter rateLimiter;
    private final AuditService auditService;
    private final OrganizationContext organizationContext;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    OutreachService(
            OutreachMessageRepository repository,
            SuppressionService suppressionService,
            LeadService leadService,
            AnalysisService analysisService,
            InteractionService interactionService,
            OrganizationQueryService organizationQueryService,
            AIProvider aiProvider,
            OutreachPrompt prompt,
            GeneratedOutputParser outputParser,
            GenerationRateLimiter rateLimiter,
            AuditService auditService,
            OrganizationContext organizationContext,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.repository = repository;
        this.suppressionService = suppressionService;
        this.leadService = leadService;
        this.analysisService = analysisService;
        this.interactionService = interactionService;
        this.organizationQueryService = organizationQueryService;
        this.aiProvider = aiProvider;
        this.prompt = prompt;
        this.outputParser = outputParser;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.organizationContext = organizationContext;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    /** Destinatario de um canal e se ele esta bloqueado. {@code recipient} nulo: a empresa nao tem contato valido. */
    public record ChannelOption(OutreachChannel channel, String recipient, boolean blocked) {}

    /** O que a tela de composicao mostra antes de gerar (doc 08 §9). */
    public record ComposeContext(
            String senderOrganization,
            String senderName,
            LeadStatus leadStatus,
            String companyName,
            String segment,
            String city,
            String state,
            List<String> confirmedFindings,
            List<ChannelOption> channels) {}

    @Transactional(readOnly = true)
    public ComposeContext composeContext(UUID leadId) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        LeadEntity lead = leadService.find(leadId);
        CompanyEntity company = lead.getCompany();
        Set<OutreachChannel> blocked = suppressionService.blockedChannels(lead);
        List<ChannelOption> channels = Arrays.stream(OutreachChannel.values())
                .map(channel -> new ChannelOption(
                        channel,
                        SuppressionService.recipientOf(company, channel).isPresent() ? displayRecipient(company, channel) : null,
                        blocked.contains(channel)))
                .toList();
        return new ComposeContext(
                organizationQueryService.currentOrganizationName(), user.displayName(), lead.getStatus(),
                company.getName(), company.getSegment(), company.getCity(), company.getState(),
                findings(analysisService.confirmedCriteria(lead.getId())), channels);
    }

    /**
     * Gera um rascunho (RF-050).
     *
     * <p>Sem transacao durante a chamada ao provedor: o contexto e lido numa
     * transacao curta, a IA responde fora dela e o rascunho e gravado em
     * outra, depois de conferir de novo o bloqueio, que pode ter mudado
     * durante a espera.
     */
    public OutreachMessageEntity generate(UUID leadId, OutreachChannel channel) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        MessageContext context = readOnly.execute(status -> {
            LeadEntity lead = leadService.find(leadId);
            requireApproachable(lead, channel);
            CompanyEntity company = lead.getCompany();
            return new MessageContext(
                    channel, organizationQueryService.currentOrganizationName(), user.displayName(),
                    company.getName(), company.getSegment(), company.getCity(), company.getState(),
                    findings(analysisService.confirmedCriteria(lead.getId())));
        });

        if (!rateLimiter.tryAcquire(user.userId())) {
            throw new TooManyAttemptsException();
        }

        AIResponse response;
        try {
            response = aiProvider.generateMessage(
                    new MessageGenerationRequest(OutreachPrompt.VERSION, prompt.instructions(), context));
        } catch (AIProviderException exception) {
            log.warn("AI provider failed for lead {}: {}", leadId, exception.getMessage());
            throw new AIGenerationFailedException();
        }
        MessageDraft.Result parsed = outputParser.parse(channel, response.content());
        if (parsed.draft().isEmpty()) {
            // So o motivo: o conteudo da resposta nao vai para log (doc 07 §6).
            log.warn("AI output rejected for lead {}: {}", leadId, parsed.violation());
            throw new AIGenerationFailedException();
        }

        return transaction.execute(status -> {
            LeadEntity lead = leadService.find(leadId);
            requireApproachable(lead, channel);
            return repository.save(new OutreachMessageEntity(
                    lead.getOrganizationId(), lead.getId(), channel, parsed.draft().get(), response.generatedBy(),
                    OutreachPrompt.VERSION, user.userId(), clock.instant()));
        });
    }

    @Transactional(readOnly = true)
    public Page<OutreachMessageEntity> list(UUID leadId, Pageable pageable) {
        LeadEntity lead = leadService.find(leadId);
        return repository.findByOrganizationIdAndLeadIdOrderByCreatedAtDescIdAsc(
                lead.getOrganizationId(), lead.getId(), pageable);
    }

    /** Edicao do operador, com as mesmas regras da saida da IA (RF-051). */
    @Transactional
    public OutreachMessageEntity edit(UUID messageId, String subject, String body) {
        OutreachMessageEntity message = find(messageId);
        if (message.getStatus().isFinal()) {
            throw new ConflictException("Message can no longer be edited: " + message.getStatus());
        }
        MessageDraft.Result result = MessageDraft.of(message.getChannel(), subject, body, message.getUnverifiedClaims());
        message.edit(result.draft().orElseThrow(() -> new InvalidRequestException(result.violation())), clock.instant());
        return message;
    }

    /**
     * Aprova (RF-051). Rascunho com afirmacoes nao verificadas exige que o
     * operador confirme ter revisado cada uma.
     */
    @Transactional
    public OutreachMessageEntity approve(UUID messageId, boolean unverifiedClaimsReviewed) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        OutreachMessageEntity message = find(messageId);
        if (!message.getStatus().isAwaitingApproval()) {
            throw new ConflictException("Message cannot be approved: " + message.getStatus());
        }
        if (message.getStatus() == MessageStatus.NEEDS_REVIEW && !unverifiedClaimsReviewed) {
            throw new InvalidRequestException("Unverified claims must be reviewed before approval");
        }
        requireApproachable(leadService.find(message.getLeadId()), message.getChannel());

        message.approve(user.userId(), clock.instant());
        audit(user, AuditAction.MESSAGE_APPROVE, message, message.getChannel().name());
        return message;
    }

    /**
     * Registra um envio que o operador ja fez fora do sistema (RF-052).
     *
     * <p>Envio confirmado vira interacao na timeline. O lead so vai a
     * {@code CONTACTED} quando o operador pede, nunca por padrao (regra 6).
     */
    @Transactional
    public OutreachMessageEntity recordSend(
            UUID messageId, boolean delivered, Instant sentAt, String notes, boolean markLeadContacted) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        Instant now = clock.instant();
        OutreachMessageEntity message = find(messageId);
        if (message.getStatus() != MessageStatus.APPROVED) {
            throw new ConflictException("Only approved messages can be recorded as sent");
        }
        if (sentAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new InvalidRequestException("sentAt must not be in the future");
        }
        // Minuto da aprovacao, nao o instante: o campo de data da tela nao tem
        // segundos, e envio registrado no mesmo minuto da aprovacao e legitimo.
        if (sentAt.isBefore(message.getApprovedAt().truncatedTo(ChronoUnit.MINUTES))) {
            throw new InvalidRequestException("sentAt must not be before the approval");
        }
        if (markLeadContacted && !delivered) {
            throw new InvalidRequestException("A failed send cannot mark the lead as contacted");
        }
        LeadEntity lead = leadService.find(message.getLeadId());
        requireApproachable(lead, message.getChannel());

        message.recordSend(delivered, sentAt, user.userId(), now);
        if (delivered) {
            interactionService.record(lead.getId(), new InteractionService.NewInteraction(
                    InteractionType.CONTACT, interactionChannel(message.getChannel()), InteractionDirection.OUTBOUND,
                    sentAt, "Mensagem enviada pelo operador", notes));
        }
        if (markLeadContacted && lead.getStatus() != LeadStatus.CONTACTED) {
            leadService.changeStatus(lead.getId(), LeadStatus.CONTACTED, "Envio de mensagem registrado", true);
        }
        audit(user, AuditAction.MESSAGE_SEND_RECORD, message, message.getStatus().name());
        return message;
    }

    @Transactional
    public OutreachMessageEntity cancel(UUID messageId) {
        OutreachMessageEntity message = find(messageId);
        if (message.getStatus().isFinal()) {
            throw new ConflictException("Message is already " + message.getStatus());
        }
        message.cancel(clock.instant());
        return message;
    }

    private OutreachMessageEntity find(UUID messageId) {
        return repository.findByIdAndOrganizationId(messageId, organizationContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Message"));
    }

    /** Bloqueio primeiro: dizer que falta o contato de quem pediu para nao ser contatado e irrelevante. */
    private void requireApproachable(LeadEntity lead, OutreachChannel channel) {
        if (suppressionService.isBlocked(lead, channel)) {
            throw new ConflictException("Contact is blocked by a do-not-contact request");
        }
        if (SuppressionService.recipientOf(lead.getCompany(), channel).isEmpty()) {
            throw new ConflictException("Company has no valid " + channel.recipientType() + " for " + channel);
        }
    }

    private void audit(AuthenticatedUser user, AuditAction action, OutreachMessageEntity message, String detail) {
        auditService.record(new AuditEntry(user.organizationId(), user.userId(), action, AuditOutcome.SUCCESS,
                "OutreachMessage", message.getId().toString(), null, detail));
    }

    private static String displayRecipient(CompanyEntity company, OutreachChannel channel) {
        return switch (channel.recipientType()) {
            case PHONE -> company.getPhone();
            case EMAIL -> company.getBusinessEmail();
            case INSTAGRAM -> company.getInstagramUrl();
        };
    }

    private static InteractionChannel interactionChannel(OutreachChannel channel) {
        return switch (channel) {
            case WHATSAPP -> InteractionChannel.WHATSAPP;
            case EMAIL -> InteractionChannel.EMAIL;
            case INSTAGRAM -> InteractionChannel.INSTAGRAM;
            case PHONE_CALL -> InteractionChannel.PHONE;
        };
    }

    private static List<String> findings(List<Criterion> criteria) {
        return criteria.stream().filter(OPPORTUNITIES::containsKey).map(OPPORTUNITIES::get).toList();
    }
}
