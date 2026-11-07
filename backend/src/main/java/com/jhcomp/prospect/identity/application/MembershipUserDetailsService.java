package com.jhcomp.prospect.identity.application;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.identity.domain.UserStatus;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberRepository;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;

/**
 * Loads the user and resolves their active organisation at login (RF-002).
 *
 * <p>It loads through the membership, not through the user: without an active
 * membership in an active organisation there is no context to operate in, so
 * no authentication is possible.
 *
 * <p>Every refusal throws {@link UsernameNotFoundException}, which the
 * authentication provider turns into invalid credentials. That keeps the
 * response identical for an unknown e-mail, a disabled user and a suspended
 * organisation, as RF-001's acceptance criterion requires.
 */
@Service
public class MembershipUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(MembershipUserDetailsService.class);

    private final OrganizationMemberRepository memberRepository;

    MembershipUserDetailsService(OrganizationMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByUsername(String username) {
        String email = normalizeEmail(username);

        List<OrganizationMemberEntity> memberships = memberRepository.findActiveMembershipsByEmail(email);

        if (memberships.isEmpty()) {
            // No e-mail in the log: it avoids recording personal data about
            // someone who may not even be a user of the system (RNF-007).
            throw new UsernameNotFoundException("No active membership");
        }

        if (memberships.size() > 1) {
            // In the MVP a user belongs to one organisation. Several active
            // memberships would make the organisation ambiguous, and letting
            // the client choose would violate RF-002; refusing is the safe
            // option.
            log.error(
                    "Usuario com {} associacoes ativas; esperado 1. Login recusado por ambiguidade.",
                    memberships.size());
            throw new UsernameNotFoundException("Ambiguous membership");
        }

        OrganizationMemberEntity membership = memberships.getFirst();

        if (membership.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new UsernameNotFoundException("User not active");
        }

        return new AuthenticatedUser(
                membership.getUser().getId(),
                membership.getOrganization().getId(),
                membership.getUser().getEmail(),
                membership.getUser().getDisplayName(),
                membership.getRole(),
                membership.getUser().isPasswordChangeRequired(),
                // Filled by the login, when the principal is written to the session.
                null,
                membership.getUser().getPasswordHash());
    }

    /** The e-mail is stored normalised; comparing without normalising would prevent login. */
    public static String normalizeEmail(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.strip().toLowerCase(Locale.ROOT);
    }
}
