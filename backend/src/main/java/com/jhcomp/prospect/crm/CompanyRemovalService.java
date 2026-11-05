package com.jhcomp.prospect.crm;

import static com.jhcomp.prospect.shared.persistence.SqlValues.instant;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.shared.audit.AuditAction;
import com.jhcomp.prospect.shared.audit.AuditOutcome;
import com.jhcomp.prospect.shared.audit.AuditService;
import com.jhcomp.prospect.shared.audit.AuditService.AuditEntry;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Excluir empresa cadastrada por engano e desfazer importacao (D32).
 *
 * <p>Arquivar continua sendo o caminho normal (RF-010). Excluir so vale para
 * empresa <em>sem atividade comercial</em>: no maximo o lead aberto junto com
 * ela, ainda em {@code NEW}, sem interacao, tarefa, negocio, mensagem nem
 * analise. Quem pode: quem cadastrou, ate {@value #GRACE_DAYS} dias depois; o
 * administrador, a qualquer tempo.
 *
 * <p>Fica no modulo de CRM porque a regra de "sem atividade" olha para tabelas
 * de lead, CRM, outreach e analise. Bloqueios de contato nunca sao apagados
 * (regra 4): nao estao ligados a empresa, e sim ao identificador.
 */
@Service
public class CompanyRemovalService {

    /** Prazo para quem cadastrou desfazer o proprio engano sem pedir ao administrador. */
    static final int GRACE_DAYS = 7;

    /** Por que a empresa nao pode ser excluida. */
    public enum Blocker {
        /** Ja tem historico comercial: arquivar, nunca apagar. */
        HAS_ACTIVITY,
        /** Nao foi quem cadastrou, ou o prazo de {@value #GRACE_DAYS} dias passou. */
        NOT_ALLOWED
    }

    /** Se a exclusao seria aceita, e ate quando para quem nao e administrador. */
    public record Eligibility(boolean allowed, Blocker blocker, Instant allowedUntil) {}

    /** O que desfazer a importacao faz (previa) ou fez. */
    public record UndoResult(int removed, int keptWithActivity, int mergedUntouched, Instant undoneAt) {}

    /**
     * Empresa com atividade comercial, sobre a empresa de alias {@code c}. Mais
     * de um lead tambem conta: um ciclo encerrado ja e historia.
     */
    private static final String HAS_ACTIVITY = """
            ((select count(*) from lead l where l.organization_id = c.organization_id and l.company_id = c.id) > 1
             -- Origem e destino de mesclagem nunca sao "limpos": a marca na origem protege o destino.
             or c.merged_into_company_id is not null
             or exists (select 1 from company m
                         where m.organization_id = c.organization_id and m.merged_into_company_id = c.id)
             or exists (
                select 1 from lead l
                 where l.organization_id = c.organization_id and l.company_id = c.id
                   and (l.status <> 'NEW'
                        or (select count(*) from lead_status_change s
                             where s.organization_id = l.organization_id and s.lead_id = l.id) > 1
                        or exists (select 1 from interaction x where x.organization_id = l.organization_id and x.lead_id = l.id)
                        or exists (select 1 from task x where x.organization_id = l.organization_id and x.lead_id = l.id)
                        or exists (select 1 from deal x where x.organization_id = l.organization_id and x.lead_id = l.id)
                        or exists (select 1 from outreach_message x
                                    where x.organization_id = l.organization_id and x.lead_id = l.id)
                        or exists (select 1 from lead_analysis x
                                    where x.organization_id = l.organization_id and x.lead_id = l.id))))""";

    private final NamedParameterJdbcTemplate jdbc;
    private final OrganizationContext organizationContext;
    private final AuditService auditService;
    private final Clock clock;

    CompanyRemovalService(
            NamedParameterJdbcTemplate jdbc,
            OrganizationContext organizationContext,
            AuditService auditService,
            Clock clock) {
        this.jdbc = jdbc;
        this.organizationContext = organizationContext;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Eligibility eligibility(UUID companyId) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        return eligibility(user, load(user.organizationId(), companyId));
    }

    /**
     * Exclui a empresa, o lead aberto com ela e a proveniencia. As linhas de
     * importacao que a citavam ficam no lote, sem a empresa.
     *
     * @param confirmationName o nome digitado pelo operador; precisa ser o da empresa
     */
    @Transactional
    public void delete(UUID companyId, String confirmationName) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        CompanyRow company = load(user.organizationId(), companyId);
        if (confirmationName == null || !normalize(confirmationName).equals(normalize(company.name()))) {
            throw new InvalidRequestException("confirmationName must match the company name");
        }
        Eligibility eligibility = eligibility(user, company);
        if (eligibility.blocker() == Blocker.HAS_ACTIVITY) {
            throw new ConflictException("Company has commercial activity; archive it instead");
        }
        if (eligibility.blocker() == Blocker.NOT_ALLOWED) {
            throw new AccessDeniedException("Only the creator, within " + GRACE_DAYS + " days, or an administrator");
        }
        remove(user.organizationId(), List.of(companyId));
        // So o id: o nome da empresa nao vai para a auditoria.
        auditService.record(new AuditEntry(user.organizationId(), user.userId(), AuditAction.COMPANY_DELETE,
                AuditOutcome.SUCCESS, "company", companyId.toString(), null, null));
    }

    /** O que {@link #undoImport} faria agora, sem mudar nada. */
    @Transactional(readOnly = true)
    public UndoResult previewUndo(UUID importId) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        BatchRow batch = loadBatch(user.organizationId(), importId);
        UndoPlan plan = plan(user.organizationId(), importId);
        return new UndoResult(plan.removable().size(), plan.kept(), plan.merged(), batch.undoneAt());
    }

    /**
     * Exclui as empresas que a importacao criou e que continuam sem atividade.
     * As que ja tem historico ficam; as que a importacao so completou
     * (MERGED) nao sao tocadas, porque existiam antes dela.
     */
    @Transactional
    public UndoResult undoImport(UUID importId) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        UUID organizationId = user.organizationId();
        BatchRow batch = loadBatch(organizationId, importId);
        if (!"COMPLETED".equals(batch.status()) || batch.undoneAt() != null) {
            throw new ConflictException("Only a completed import can be undone, once");
        }
        Instant now = clock.instant();
        boolean inGrace = batch.createdBy().equals(user.userId())
                && batch.completedAt().plus(Duration.ofDays(GRACE_DAYS)).isAfter(now);
        if (user.role() != OrganizationRole.ADMIN && !inGrace) {
            throw new AccessDeniedException("Only the importer, within " + GRACE_DAYS + " days, or an administrator");
        }
        UndoPlan plan = plan(organizationId, importId);
        remove(organizationId, plan.removable());
        jdbc.update("update import_batch set undone_at = :now where organization_id = :organizationId and id = :id",
                new MapSqlParameterSource("now", Timestamp.from(now))
                        .addValue("organizationId", organizationId)
                        .addValue("id", importId));
        auditService.record(new AuditEntry(organizationId, user.userId(), AuditAction.IMPORT_UNDO,
                AuditOutcome.SUCCESS, "import", importId.toString(), null,
                "removed=" + plan.removable().size() + " kept=" + plan.kept()));
        return new UndoResult(plan.removable().size(), plan.kept(), plan.merged(), now);
    }

    private Eligibility eligibility(AuthenticatedUser user, CompanyRow company) {
        if (company.hasActivity()) {
            return new Eligibility(false, Blocker.HAS_ACTIVITY, null);
        }
        if (user.role() == OrganizationRole.ADMIN) {
            return new Eligibility(true, null, null);
        }
        Instant until = company.createdAt().plus(Duration.ofDays(GRACE_DAYS));
        boolean creator = user.userId().equals(company.createdBy());
        if (creator && until.isAfter(clock.instant())) {
            return new Eligibility(true, null, until);
        }
        return new Eligibility(false, Blocker.NOT_ALLOWED, null);
    }

    private record CompanyRow(String name, Instant createdAt, UUID createdBy, boolean hasActivity) {}

    /** Quem cadastrou fica na empresa e nao muda com mesclagem (V15). Outra organizacao e 404. */
    private CompanyRow load(UUID organizationId, UUID companyId) {
        return jdbc.query("""
                select c.name, c.created_at, c.created_by_user_id as created_by, %s as has_activity
                  from company c
                 where c.organization_id = :organizationId and c.id = :companyId
                """.formatted(HAS_ACTIVITY),
                new MapSqlParameterSource("organizationId", organizationId).addValue("companyId", companyId),
                (row, rowNumber) -> new CompanyRow(
                        row.getString("name"),
                        row.getTimestamp("created_at").toInstant(),
                        row.getObject("created_by", UUID.class),
                        row.getBoolean("has_activity")))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Company"));
    }

    private record BatchRow(String status, UUID createdBy, Instant completedAt, Instant undoneAt) {}

    private BatchRow loadBatch(UUID organizationId, UUID importId) {
        return jdbc.query("""
                select status, created_by_user_id, completed_at, undone_at from import_batch
                 where organization_id = :organizationId and id = :importId
                """, new MapSqlParameterSource("organizationId", organizationId).addValue("importId", importId),
                (row, rowNumber) -> new BatchRow(
                        row.getString("status"),
                        row.getObject("created_by_user_id", UUID.class),
                        instant(row.getTimestamp("completed_at")),
                        instant(row.getTimestamp("undone_at"))))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Import"));
    }

    private record CreatedCompany(UUID id, boolean hasActivity) {}

    private record UndoPlan(List<UUID> removable, int kept, int merged) {}

    private UndoPlan plan(UUID organizationId, UUID importId) {
        MapSqlParameterSource params =
                new MapSqlParameterSource("organizationId", organizationId).addValue("importId", importId);
        // Linhas do mesmo lote podem apontar para a mesma empresa so se uma criou e outra completou;
        // distinct por garantia.
        List<CreatedCompany> created = jdbc.query("""
                select distinct c.id, %s as has_activity from import_row r
                  join import_batch b on b.organization_id = r.organization_id and b.id = r.import_id
                  join company c on c.organization_id = r.organization_id and c.id = r.company_id
                 where r.organization_id = :organizationId and r.import_id = :importId and r.status = 'IMPORTED'
                   -- Criada por este lote: pelo autor da importacao, depois que ela comecou.
                   and c.created_by_user_id = b.created_by_user_id and c.created_at >= b.created_at
                """.formatted(HAS_ACTIVITY), params, (row, rowNumber) -> new CreatedCompany(
                        row.getObject("id", UUID.class), row.getBoolean("has_activity")));
        List<UUID> removable = created.stream().filter(company -> !company.hasActivity()).map(CreatedCompany::id)
                .toList();
        Integer merged = jdbc.queryForObject("""
                select count(*) from import_row
                 where organization_id = :organizationId and import_id = :importId and status = 'MERGED'
                """, params, Integer.class);
        return new UndoPlan(removable, created.size() - removable.size(),
                merged == null ? 0 : merged);
    }

    /**
     * Apaga as empresas e o que so existe por causa delas, na ordem das chaves
     * estrangeiras. Uma interacao gravada em paralelo faz a chave recusar a
     * exclusao do lead: vira conflito, nunca perda de historico.
     */
    private void remove(UUID organizationId, List<UUID> companyIds) {
        if (companyIds.isEmpty()) {
            return;
        }
        MapSqlParameterSource params =
                new MapSqlParameterSource("organizationId", organizationId).addValue("ids", Set.copyOf(companyIds));
        try {
            // A linha do lote continua contando o que trouxe; so perde o vinculo.
            jdbc.update("""
                    update import_row
                       set status = case when status in ('IMPORTED', 'MERGED') then 'REMOVED' else status end,
                           merged_fields = null, company_id = null, lead_id = null
                     where organization_id = :organizationId and company_id in (:ids)
                    """, params);
            jdbc.update("""
                    update import_row set duplicate_of_company_id = null
                     where organization_id = :organizationId and duplicate_of_company_id in (:ids)
                    """, params);
            jdbc.update("delete from lead_source where organization_id = :organizationId and company_id in (:ids)",
                    params);
            jdbc.update("""
                    delete from lead_status_change
                     where organization_id = :organizationId
                       and lead_id in (select id from lead where organization_id = :organizationId and company_id in (:ids))
                    """, params);
            jdbc.update("delete from lead where organization_id = :organizationId and company_id in (:ids)", params);
            jdbc.update("delete from company where organization_id = :organizationId and id in (:ids)", params);
        } catch (DataIntegrityViolationException concurrentActivity) {
            throw new ConflictException("Company received activity while being removed; archive it instead");
        }
    }

    private static String normalize(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
