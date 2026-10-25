package com.jhcomp.prospect.identity.presentation;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberEntity;

/**
 * Membro da organizacao (RF-003).
 *
 * <p>Nao expoe hash de senha nem o id da organizacao: quem consulta ja opera
 * dentro dela.
 */
public record MemberResponse(
        UUID memberId,
        UUID userId,
        String email,
        String displayName,
        OrganizationRole role,
        MemberStatus status,
        Instant createdAt) {

    public static MemberResponse from(OrganizationMemberEntity member) {
        return new MemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getDisplayName(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt());
    }
}
