package com.jhcomp.prospect.lead.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.company.application.CompanyService;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.company.infrastructure.persistence.LeadSourceEntity.SourceData;
import com.jhcomp.prospect.identity.application.MemberQueryService;
import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.lead.domain.LeadStatusTransitions;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadRepository;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadSpecifications;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadStatusChangeEntity;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadStatusChangeRepository;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadTimelineQuery;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Casos de uso de lead (RF-011, RF-012, RF-013; doc 06 §2).
 *
 * <p>{@code Lead.status} e a fonte unica do estado comercial (D2). Toda
 * mudanca de status grava ator, data e estado anterior na mesma transacao.
 */
@Service
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadStatusChangeRepository statusChangeRepository;
    private final LeadTimelineQuery timelineQuery;
    private final CompanyService companyService;
    private final MemberQueryService memberQueryService;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    LeadService(
            LeadRepository leadRepository,
            LeadStatusChangeRepository statusChangeRepository,
            LeadTimelineQuery timelineQuery,
            CompanyService companyService,
            MemberQueryService memberQueryService,
            OrganizationContext organizationContext,
            Clock clock) {
        this.leadRepository = leadRepository;
        this.statusChangeRepository = statusChangeRepository;
        this.timelineQuery = timelineQuery;
        this.companyService = companyService;
        this.memberQueryService = memberQueryService;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    /**
     * Abre uma oportunidade para a empresa (RF-011).
     *
     * <p>Lead nasce com status, origem e data de criacao, como exige o aceite.
     * A empresa precisa ser da organizacao ativa (senao 404) e nao estar
     * arquivada; e uma oportunidade aberta por empresa (doc 06 §3).
     */
    @Transactional
    public LeadEntity create(UUID companyId, LeadPriority priority, SourceData source) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        CompanyEntity company = companyService.requireActive(companyId);

        if (leadRepository.existsByOrganizationIdAndCompanyIdAndClosedAtIsNull(
                company.getOrganizationId(), company.getId())) {
            throw new ConflictException("Company already has an open lead");
        }

        Instant now = clock.instant();
        LeadEntity lead = leadRepository.save(new LeadEntity(
                UUID.randomUUID(), company, priority == null ? LeadPriority.MEDIUM : priority, now));

        recordStatusChange(lead, null, lead.getStatus(), null, user, now);
        companyService.recordLeadSource(company, lead.getId(), source);

        return lead;
    }

    /**
     * Abre um lead se a empresa nao tiver oportunidade aberta.
     *
     * <p>Para a importacao: uma empresa existente pode ja ter oportunidade em
     * andamento, inclusive com bloqueio de contato. Nesse caso nada e criado,
     * e a importacao segue, em vez de falhar inteira.
     */
    @Transactional
    public Optional<LeadEntity> createIfNoneOpen(UUID companyId, LeadPriority priority, SourceData source) {
        UUID organizationId = organizationContext.requireOrganizationId();
        if (leadRepository.existsByOrganizationIdAndCompanyIdAndClosedAtIsNull(organizationId, companyId)) {
            return Optional.empty();
        }
        return Optional.of(create(companyId, priority, source));
    }

    @Transactional(readOnly = true)
    public LeadEntity find(UUID leadId) {
        return leadRepository
                .findScoped(leadId, organizationContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead"));
    }

    /**
     * Lead com a empresa, por organizacao explicita.
     *
     * <p>Uso interno de jobs, que rodam sem sessao: a organizacao vem do job,
     * gravado a partir da sessao de quem pediu. Nunca chamar com organizacao
     * recebida do cliente (regra 2 do produto).
     */
    @Transactional(readOnly = true)
    public Optional<LeadEntity> findInOrganization(UUID organizationId, UUID leadId) {
        return leadRepository.findScoped(leadId, organizationId);
    }

    /**
     * Aplica o score de uma analise (D3). Mesma restricao de uso interno de
     * {@link #findInOrganization}.
     */
    @Transactional
    public void applyScore(UUID organizationId, UUID leadId, int score, String version) {
        leadRepository.findScoped(leadId, organizationId)
                .ifPresent(lead -> lead.applyScore(score, version, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Page<LeadEntity> list(LeadSpecifications.Criteria criteria, Pageable pageable) {
        return leadRepository.findAll(
                LeadSpecifications.matching(organizationContext.requireOrganizationId(), criteria), pageable);
    }

    /**
     * Muda o status (doc 06 §2).
     *
     * <p>{@code CONTACTED} exige confirmacao explicita do operador de que o
     * contato ocorreu: gerar ou aprovar mensagem nunca move o lead, e o campo
     * existe para que a interface nao consiga fazer isso por engano.
     */
    @Transactional
    public LeadEntity changeStatus(UUID leadId, LeadStatus newStatus, String reason, boolean contactConfirmed) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        LeadEntity lead = find(leadId);

        if (newStatus == LeadStatus.CONTACTED && lead.getStatus() != LeadStatus.CONTACTED && !contactConfirmed) {
            throw new InvalidRequestException("Moving to CONTACTED requires the operator to confirm the contact");
        }
        if (!LeadStatusTransitions.isAllowed(lead.getStatus(), newStatus)) {
            throw new ConflictException("Status transition not allowed: " + lead.getStatus() + " to " + newStatus);
        }

        LeadStatus previous = lead.getStatus();
        Instant now = clock.instant();
        if (lead.changeStatus(newStatus, now)) {
            recordStatusChange(lead, previous, newStatus, reason, user, now);
        }
        return lead;
    }

    @Transactional
    public LeadEntity changePriority(UUID leadId, LeadPriority priority) {
        LeadEntity lead = find(leadId);
        lead.changePriority(priority, clock.instant());
        return lead;
    }

    /**
     * Define o responsavel, ou remove com {@code null}.
     *
     * <p>O responsavel precisa ser membro ativo da mesma organizacao. Um id de
     * outra organizacao responde como inexistente, sem confirmar que existe.
     */
    @Transactional
    public LeadEntity assign(UUID leadId, UUID assignedUserId) {
        LeadEntity lead = find(leadId);
        if (assignedUserId != null && !memberQueryService.isActiveMember(assignedUserId)) {
            throw new ResourceNotFoundException("Member");
        }
        lead.assignTo(assignedUserId, clock.instant());
        return lead;
    }

    @Transactional(readOnly = true)
    public Page<LeadTimelineQuery.Entry> timeline(UUID leadId, Pageable pageable) {
        LeadEntity lead = find(leadId);
        return timelineQuery.find(lead.getOrganizationId(), lead.getId(), pageable);
    }

    private void recordStatusChange(
            LeadEntity lead, LeadStatus from, LeadStatus to, String reason, AuthenticatedUser user, Instant now) {
        statusChangeRepository.save(new LeadStatusChangeEntity(
                UUID.randomUUID(),
                lead.getOrganizationId(),
                lead.getId(),
                from,
                to,
                reason == null || reason.isBlank() ? null : reason.strip(),
                user.userId(),
                now));
    }
}
