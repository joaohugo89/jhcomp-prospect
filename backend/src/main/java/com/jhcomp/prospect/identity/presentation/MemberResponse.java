package com.jhcomp.prospect.identity.presentation;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberEntity;

/**
 * A member of the organisation (RF-003).
 *
 * <p>It exposes neither the password hash nor the organisation's id: whoever
 * is reading already operates inside it.
 */
public record MemberResponse(
        UUID memberId,
        UUID userId,
        String email,
        String displayName,
        OrganizationRole role,
        MemberStatus status,
        /** A temporary password not yet changed: the person has not signed in for the first time. */
        boolean passwordChangeRequired,
        /** The last login in this organisation; absent when they never signed in, or when it was not queried. */
        Instant lastLoginAt,
        Instant createdAt) {

    public static MemberResponse from(OrganizationMemberEntity member) {
        return from(member, null);
    }

    public static MemberResponse from(OrganizationMemberEntity member, Instant lastLoginAt) {
        return new MemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getDisplayName(),
                member.getRole(),
                member.getStatus(),
                member.getUser().isPasswordChangeRequired(),
                lastLoginAt,
                member.getCreatedAt());
    }
}
