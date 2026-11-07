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
 * Queries over the active organisation's members (RF-003).
 *
 * <p>The organisation comes from {@link OrganizationContext}, never from a
 * parameter: it is this service, not the controller, that guarantees the scope
 * (RNF-001).
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
     * The administration screen: a null {@code status} returns both active and
     * revoked members; {@code search} looks in name and e-mail, ignoring
     * case.
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
     * A member by id, restricted to the active organisation.
     *
     * <p>A valid id from another organisation yields 404, not 403: saying the
     * resource exists but belongs to another organisation is already a
     * leak.
     */
    @Transactional(readOnly = true)
    public OrganizationMemberEntity findMember(UUID memberId) {
        return memberRepository
                .findByIdAndOrganization(memberId, organizationContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Member"));
    }

    /** The user is an active member of the active organisation. */
    @Transactional(readOnly = true)
    public boolean isActiveMember(UUID userId) {
        return memberRepository.existsActiveMember(organizationContext.requireOrganizationId(), userId);
    }
}
