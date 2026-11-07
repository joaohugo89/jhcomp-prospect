package com.jhcomp.prospect.crm;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.company.application.CompanyService;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity.CompanyData;
import com.jhcomp.prospect.shared.audit.AuditAction;
import com.jhcomp.prospect.shared.audit.AuditOutcome;
import com.jhcomp.prospect.shared.audit.AuditService;
import com.jhcomp.prospect.shared.audit.AuditService.AuditEntry;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Merging a duplicate (RF-014, D32): the source company becomes the target
 * one. Leads, provenance and import rows change company; the target's empty
 * fields are filled from the source without overwriting anything (RF-023); the
 * source is archived, never deleted.
 *
 * <p>It does not join two open cycles: a company has only one open opportunity
 * at a time (doc 06 §3). In that case the operator closes one of them
 * first.
 */
@Service
public class CompanyMergeService {

    public record MergeResult(CompanyEntity target, List<String> filledFields, int movedLeads) {}

    private final CompanyService companyService;
    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;
    private final AuditService auditService;
    private final Clock clock;

    CompanyMergeService(
            CompanyService companyService,
            NamedParameterJdbcTemplate jdbc,
            OrganizationContext organizationContext,
            AuditService auditService,
            Clock clock) {
        this.companyService = companyService;
        this.jdbc = jdbc;
        this.organizationContext = organizationContext;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public MergeResult merge(UUID targetId, UUID sourceId) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        if (targetId.equals(sourceId)) {
            throw new InvalidRequestException("A company cannot be merged into itself");
        }
        MapSqlParameterSource params = new MapSqlParameterSource("organizationId", user.organizationId())
                .addValue("target", targetId)
                .addValue("source", sourceId);
        // Locks both rows, always in the same order, before reading the state:
        // two simultaneous merges (the same source into two targets, or A into
        // B while B goes to C) wait for each other instead of deciding over a
        // state the other one is changing.
        jdbc.query("""
                select id from company
                 where organization_id = :organizationId and id in (:target, :source)
                 order by id for update
                """, params, (row, rowNumber) -> row.getObject("id", UUID.class));
        // Read after the lock. Both from the session's organisation: another organisation is 404.
        CompanyEntity target = companyService.requireActive(targetId);
        CompanyEntity source = companyService.find(sourceId);
        // Merged once, it stays: changing the target would erase the mark that protects the first one.
        if (source.getMergedIntoCompanyId() != null || target.getMergedIntoCompanyId() != null) {
            throw new ConflictException("Company was already merged");
        }

        Integer withOpenLead = jdbc.queryForObject("""
                select count(distinct company_id) from lead
                 where organization_id = :organizationId and company_id in (:target, :source) and closed_at is null
                """, params, Integer.class);
        if (withOpenLead != null && withOpenLead > 1) {
            throw new ConflictException("Both companies have an open lead; close one before merging");
        }

        int movedLeads = jdbc.update("""
                update lead set company_id = :target
                 where organization_id = :organizationId and company_id = :source
                """, params);
        jdbc.update("""
                update lead_source set company_id = :target
                 where organization_id = :organizationId and company_id = :source
                """, params);
        // The row that created the source now points at a company it did not
        // create, so it becomes MERGED. Without that, undoing that import would
        // delete the target, which existed before it.
        jdbc.update("""
                update import_row
                   set company_id = :target, status = case when status = 'IMPORTED' then 'MERGED' else status end
                 where organization_id = :organizationId and company_id = :source
                """, params);
        jdbc.update("""
                update import_row set duplicate_of_company_id = :target
                 where organization_id = :organizationId and duplicate_of_company_id = :source
                """, params);

        Instant now = clock.instant();
        List<String> filled = target.fillBlanks(new CompanyData(
                source.getName(),
                source.getTradeName(),
                source.getSegment(),
                source.getCity(),
                source.getState(),
                source.getWebsiteUrl(),
                source.getInstagramUrl(),
                source.getPhone(),
                source.getBusinessEmail(),
                source.getAddress(),
                source.getNotes()), now);
        // Only marks when nobody marked it before: the lock already guarantees it, the condition makes it explicit.
        int marked = jdbc.update("""
                update company
                   set merged_into_company_id = :target, archived_at = coalesce(archived_at, :now), updated_at = :now
                 where organization_id = :organizationId and id = :source and merged_into_company_id is null
                """, params.addValue("now", Timestamp.from(now)));
        if (marked != 1) {
            throw new ConflictException("Company was already merged");
        }

        auditService.record(new AuditEntry(user.organizationId(), user.userId(), AuditAction.COMPANY_MERGE,
                AuditOutcome.SUCCESS, "company", targetId.toString(), null, "source=" + sourceId));
        return new MergeResult(target, filled, movedLeads);
    }
}
