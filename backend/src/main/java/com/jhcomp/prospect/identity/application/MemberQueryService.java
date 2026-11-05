package com.jhcomp.prospect.identity.application;

import static com.jhcomp.prospect.shared.persistence.SqlValues.count;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberRepository;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.persistence.LikePatterns;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Consultas de membros da organizacao ativa (RF-003).
 *
 * <p>A organizacao vem do {@link OrganizationContext}, nunca de parametro: e
 * este servico, e nao o controller, que garante o escopo (RNF-001).
 */
@Service
public class MemberQueryService {

    private final OrganizationMemberRepository memberRepository;
    private final OrganizationContext organizationContext;

    MemberQueryService(OrganizationMemberRepository memberRepository, OrganizationContext organizationContext) {
        this.memberRepository = memberRepository;
        this.organizationContext = organizationContext;
    }

    @Transactional(readOnly = true)
    public Page<OrganizationMemberEntity> listActiveMembers(Pageable pageable) {
        return memberRepository.findByOrganization(
                organizationContext.requireOrganizationId(), MemberStatus.ACTIVE, pageable);
    }

    /**
     * Tela de administracao: {@code status} nulo traz ativos e removidos;
     * {@code search} procura no nome e no e-mail, sem distinguir caixa.
     */
    @Transactional(readOnly = true)
    public Page<OrganizationMemberEntity> search(MemberStatus status, String search, Pageable pageable) {
        Set<MemberStatus> statuses = status == null ? EnumSet.allOf(MemberStatus.class) : EnumSet.of(status);
        String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        return memberRepository.search(
                organizationContext.requireOrganizationId(), statuses, LikePatterns.contains(term), pageable);
    }

    public record TeamSummary(long active, long admins, long revoked, long awaitingFirstAccess) {}

    @Transactional(readOnly = true)
    public TeamSummary summary() {
        Object[] row = memberRepository.summarize(organizationContext.requireOrganizationId()).getFirst();
        return new TeamSummary(count(row[0]), count(row[1]), count(row[2]), count(row[3]));
    }

    /**
     * Membro por id, restrito a organizacao ativa.
     *
     * <p>Um id valido de outra organizacao resulta em 404, nao em 403: dizer
     * que o recurso existe mas pertence a outra organizacao ja e vazamento.
     */
    @Transactional(readOnly = true)
    public OrganizationMemberEntity findMember(UUID memberId) {
        return memberRepository
                .findByIdAndOrganization(memberId, organizationContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Member"));
    }

    /** Usuario e membro ativo da organizacao ativa. */
    @Transactional(readOnly = true)
    public boolean isActiveMember(UUID userId) {
        return memberRepository.existsActiveMember(organizationContext.requireOrganizationId(), userId);
    }
}
