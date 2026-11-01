package com.jhcomp.prospect.identity.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberRepository;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
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

    /** Ativos e revogados, para o administrador poder reativar acesso. */
    @Transactional(readOnly = true)
    public Page<OrganizationMemberEntity> listAllMembers(Pageable pageable) {
        return memberRepository.findAllByOrganization(organizationContext.requireOrganizationId(), pageable);
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
