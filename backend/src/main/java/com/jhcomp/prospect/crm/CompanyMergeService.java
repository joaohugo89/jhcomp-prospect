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
 * Mesclar duplicata (RF-014, D32): a empresa de origem passa a ser a de
 * destino. Leads, proveniencia e linhas de importacao mudam de empresa; os
 * campos vazios do destino sao completados com os da origem, sem sobrescrever
 * nada (RF-023); a origem e arquivada, nunca apagada.
 *
 * <p>Nao junta dois ciclos abertos: uma empresa so tem uma oportunidade aberta
 * por vez (doc 06 §3). Nesse caso o operador encerra um deles antes.
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
        // Trava as duas linhas, sempre na mesma ordem, antes de ler o estado: duas mesclagens
        // simultaneas (a mesma origem em dois destinos, ou A em B enquanto B vai para C) esperam
        // uma pela outra em vez de decidir sobre um estado que a outra esta mudando.
        jdbc.query("""
                select id from company
                 where organization_id = :organizationId and id in (:target, :source)
                 order by id for update
                """, params, (row, rowNumber) -> row.getObject("id", UUID.class));
        // Lidas depois da trava. As duas da organizacao da sessao: outra organizacao e 404.
        CompanyEntity target = companyService.requireActive(targetId);
        CompanyEntity source = companyService.find(sourceId);
        // Mesclada uma vez, fica: trocar o destino apagaria a marca que protege o primeiro destino.
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
        // A linha que criou a origem passa a apontar para uma empresa que ela nao criou: vira MERGED.
        // Sem isso, desfazer aquela importacao apagaria o destino, que existia antes dela.
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
        // So marca se ninguem marcou antes: a trava ja garante, a condicao deixa isso explicito.
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
