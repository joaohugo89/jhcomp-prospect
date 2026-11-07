package com.jhcomp.prospect.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.identity.domain.UserStatus;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberRepository;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationRepository;
import com.jhcomp.prospect.identity.infrastructure.persistence.UserEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.UserRepository;
import com.jhcomp.prospect.shared.audit.AuditAction;
import com.jhcomp.prospect.shared.audit.AuditOutcome;
import com.jhcomp.prospect.shared.audit.AuditService.AuditEntry;
import com.jhcomp.prospect.shared.audit.AuditService;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Administration of the active organisation's members (RF-003, D28).
 *
 * <p>No invitations: there is no e-mail channel in the MVP. The system
 * generates a temporary password, returned exactly once to the administrator,
 * who hands it to the member out of band; the member must change it on first
 * access.
 *
 * <p>An administrator cannot change their own membership — role, access or
 * temporary password — which is what stops an organisation from accidentally
 * losing its last administrator. A PostgreSQL advisory lock per organisation
 * serialises concurrent changes by two administrators. It is deliberately not
 * {@code SELECT ... FOR UPDATE} on the organisation: auditing writes in its
 * own transaction, with a foreign key to the organisation, and would wait
 * forever for the lock held by the transaction that called it.
 */
@Service
public class MemberService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    MemberService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository memberRepository,
            JdbcTemplate jdbc,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            OrganizationContext organizationContext,
            Clock clock) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    /** The member created, and the temporary password in clear text, which is stored nowhere. */
    public record CreatedMember(OrganizationMemberEntity member, String temporaryPassword) {}

    /**
     * Registers a new member. An e-mail already registered is refused, in any
     * organisation: attaching an existing account would let this
     * organisation's administrator set the password of someone who also
     * belongs to another.
     */
    @Transactional
    public CreatedMember create(String rawEmail, String displayName, OrganizationRole role) {
        AuthenticatedUser admin = organizationContext.requireCurrentUser();
        OrganizationEntity organization = lockOrganization(admin);
        String email = MembershipUserDetailsService.normalizeEmail(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("E-mail already registered");
        }

        Instant now = clock.instant();
        String temporaryPassword = TemporaryPasswords.generate();
        UserEntity user = userRepository.save(new UserEntity(
                UUID.randomUUID(), email, passwordEncoder.encode(temporaryPassword), displayName.strip(),
                UserStatus.ACTIVE, true, now));
        OrganizationMemberEntity member = memberRepository.save(new OrganizationMemberEntity(
                UUID.randomUUID(), organization, user, role, MemberStatus.ACTIVE, now));

        audit(admin, AuditAction.MEMBER_CREATE, member, role.name());
        return new CreatedMember(member, temporaryPassword);
    }

    /** Changes role and/or access. A null field changes nothing. */
    @Transactional
    public OrganizationMemberEntity update(UUID memberId, OrganizationRole role, MemberStatus status) {
        AuthenticatedUser admin = organizationContext.requireCurrentUser();
        lockOrganization(admin);
        OrganizationMemberEntity member = findOtherMember(admin, memberId);

        List<String> changes = new ArrayList<>();
        if (role != null && role != member.getRole()) {
            member.changeRole(role);
            changes.add("ROLE_" + role.name());
        }
        if (status != null && status != member.getStatus()) {
            member.changeStatus(status);
            changes.add("STATUS_" + status.name());
        }
        // Defence in depth: with one's own membership untouchable the case
        // cannot arise, but the rule matters more than the path that enforces
        // it today.
        memberRepository.flush();
        if (memberRepository.countActiveAdmins(admin.organizationId()) == 0) {
            throw new ConflictException("Organization must keep at least one active administrator");
        }
        // After the rules: auditing writes in its own transaction and would
        // not be rolled back if the change were refused.
        if (!changes.isEmpty()) {
            audit(admin, AuditAction.MEMBER_UPDATE, member, String.join(",", changes));
        }
        return member;
    }

    /**
     * A new temporary password for the member. Their open sessions end on the
     * next request (D28).
     */
    @Transactional
    public CreatedMember resetPassword(UUID memberId) {
        AuthenticatedUser admin = organizationContext.requireCurrentUser();
        lockOrganization(admin);
        OrganizationMemberEntity member = findOtherMember(admin, memberId);
        if (memberRepository.existsInOtherOrganization(member.getUser().getId(), admin.organizationId())) {
            // Resetting here would change their access in another organisation.
            throw new ConflictException("Member also belongs to another organization");
        }

        String temporaryPassword = TemporaryPasswords.generate();
        member.getUser().replacePassword(passwordEncoder.encode(temporaryPassword), true, clock.instant());
        audit(admin, AuditAction.PASSWORD_RESET, member, null);
        return new CreatedMember(member, temporaryPassword);
    }

    /** An advisory lock until the end of the transaction, per organisation. */
    private OrganizationEntity lockOrganization(AuthenticatedUser admin) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
                (ResultSetExtractor<Void>) resultSet -> null, "members:" + admin.organizationId());
        return organizationRepository.findById(admin.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    }

    /** A member of the session's organisation (404 outside it), and not the caller. */
    private OrganizationMemberEntity findOtherMember(AuthenticatedUser admin, UUID memberId) {
        OrganizationMemberEntity member = memberRepository.findByIdAndOrganization(memberId, admin.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Member"));
        if (member.getUser().getId().equals(admin.userId())) {
            throw new ConflictException("Administrators cannot change their own membership");
        }
        return member;
    }

    /** No e-mail and no password in the detail: the member's id is enough for the trail. */
    private void audit(AuthenticatedUser admin, AuditAction action, OrganizationMemberEntity member, String detail) {
        auditService.record(new AuditEntry(admin.organizationId(), admin.userId(), action, AuditOutcome.SUCCESS,
                "OrganizationMember", member.getId().toString(), null, detail));
    }
}
