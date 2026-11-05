package com.jhcomp.prospect.discovery;

import static com.jhcomp.prospect.shared.persistence.SqlValues.count;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.company.application.CompanyService;
import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity.CompanyData;
import com.jhcomp.prospect.company.infrastructure.persistence.LeadSourceEntity.SourceData;
import com.jhcomp.prospect.lead.application.LeadService;
import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;
import com.jhcomp.prospect.shared.audit.AuditAction;
import com.jhcomp.prospect.shared.audit.AuditOutcome;
import com.jhcomp.prospect.shared.audit.AuditService;
import com.jhcomp.prospect.shared.audit.AuditService.AuditEntry;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;
import com.jhcomp.prospect.shared.text.Texts;
import com.jhcomp.prospect.shared.text.Hashes;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Importacao CSV em duas fases (RF-015, D14, D23).
 *
 * <p>Analisar le, valida e classifica o arquivo, e grava o relatorio sem criar
 * nenhuma empresa. Confirmar cria as empresas das linhas validas e das
 * duplicatas que o operador escolheu. Assim nenhum registro e mesclado nem
 * descartado em silencio (RF-014), e o operador revisa antes de persistir.
 *
 * <p>Importacao so cria empresas e leads novos: nunca altera registro
 * existente, e portanto nunca remove bloqueio de contato (doc 10 §6).
 */
@Service
public class ImportService {

    private static final TypeReference<List<ImportRowValidator.RowError>> ERRORS_TYPE = new TypeReference<>() {};
    private static final TypeReference<LinkedHashMap<String, String>> MAPPING_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ImportBatchRepository batchRepository;
    private final ImportRowRepository rowRepository;
    private final CompanyService companyService;
    private final LeadService leadService;
    private final AuditService auditService;
    private final OrganizationContext organizationContext;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    ImportService(
            ImportBatchRepository batchRepository,
            ImportRowRepository rowRepository,
            CompanyService companyService,
            LeadService leadService,
            AuditService auditService,
            OrganizationContext organizationContext,
            ObjectMapper objectMapper,
            Clock clock) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.companyService = companyService;
        this.leadService = leadService;
        this.auditService = auditService;
        this.organizationContext = organizationContext;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Pedido de analise. {@code mapping} nulo usa o reconhecimento automatico de cabecalhos. */
    public record AnalyzeCommand(
            String fileName, byte[] content, String sourceName, String datasetNote, Map<String, String> mapping) {}

    @Transactional
    public ImportBatchEntity analyze(AnalyzeCommand command) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        CsvTable table = CsvTable.parse(command.content());
        Map<ImportField, Integer> columns = resolveMapping(table.headers(), command.mapping());

        UUID importId = UUID.randomUUID();
        List<ImportRowEntity> rows = new ArrayList<>(table.records().size());
        for (CsvTable.Row record : table.records()) {
            Map<ImportField, String> raw = new EnumMap<>(ImportField.class);
            columns.forEach((field, column) -> raw.put(field, record.cell(column)));

            var result = ImportRowValidator.validate(raw, record.extraCells());
            ImportRowEntity row = new ImportRowEntity(user.organizationId(), importId, record.rowNumber(), result.values());
            if (!result.errors().isEmpty()) {
                row.reject(objectMapper.writeValueAsString(result.errors()));
            }
            rows.add(row);
        }

        int duplicates = DuplicateClassifier.classify(rows, companyService, true);
        int rejected = (int) rows.stream().filter(row -> row.getStatus() == ImportRowEntity.Status.REJECTED).count();

        List<String> ignored = new ArrayList<>(table.headers());
        columns.values().forEach(column -> ignored.remove(table.headers().get(column)));

        ImportBatchEntity batch = batchRepository.save(new ImportBatchEntity(
                importId,
                user.organizationId(),
                cleanFileName(command.fileName()),
                Hashes.sha256Hex(command.content()),
                sourceName(command),
                Texts.blankToNull(command.datasetNote()),
                objectMapper.writeValueAsString(toMappingJson(table.headers(), columns)),
                objectMapper.writeValueAsString(ignored),
                rows.size() - rejected - duplicates,
                rejected,
                duplicates,
                user.userId(),
                clock.instant()));
        rowRepository.saveAll(rows);
        return batch;
    }

    /**
     * Importa as linhas validas e as duplicatas escolhidas.
     *
     * <p>Tudo numa transacao: ou o arquivo inteiro entra, ou nada entra, e a
     * importacao volta a aguardar confirmacao. A transicao de status e
     * atomica, entao duas confirmacoes simultaneas nao importam em dobro.
     *
     * <p>As linhas validas sao reclassificadas contra a base antes de gravar:
     * uma empresa cadastrada entre a analise e a confirmacao nao deve virar
     * duplicata sem que o operador tenha visto.
     */
    @Transactional
    public ImportBatchEntity confirm(
            UUID importId, Set<Integer> includeDuplicateRows, Set<Integer> mergeDuplicateRows, boolean createLeads) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        claim(importId, user.organizationId());

        List<ImportRowEntity> rows = rowRepository.findByOrganizationIdAndImportIdOrderByRowNumber(
                user.organizationId(), importId);
        validateChosenRows(rows, includeDuplicateRows, mergeDuplicateRows);

        // Depois da validacao acima: o conjunto incluido so contem duplicatas
        // vistas na analise, nunca as que surgirem nesta reclassificacao.
        int newlyDuplicated = DuplicateClassifier.classify(rows, companyService, false);

        ImportBatchEntity batch = find(importId);
        int imported = 0;
        int merged = 0;
        int leads = 0;

        for (ImportRowEntity row : rows) {
            boolean duplicate = row.getStatus() == ImportRowEntity.Status.DUPLICATE;
            if (duplicate && mergeDuplicateRows.contains(row.getRowNumber())) {
                if (completeExisting(batch, row, createLeads)) {
                    merged++;
                    leads += row.getLeadId() == null ? 0 : 1;
                }
            } else if (row.getStatus() == ImportRowEntity.Status.VALID
                    || (duplicate && includeDuplicateRows.contains(row.getRowNumber()))) {
                importAsNew(batch, row, createLeads);
                imported++;
                leads += row.getLeadId() == null ? 0 : 1;
            } else if (duplicate) {
                row.markSkipped();
            }
        }

        batch.complete(imported, merged, leads, newlyDuplicated, clock.instant());

        auditService.record(new AuditEntry(
                user.organizationId(),
                user.userId(),
                AuditAction.IMPORT_CONFIRM,
                AuditOutcome.SUCCESS,
                "import",
                importId.toString(),
                null,
                "imported=" + imported + " merged=" + merged + " leads=" + leads));
        return batch;
    }

    private void importAsNew(ImportBatchEntity batch, ImportRowEntity row, boolean createLead) {
        SourceData source = sourceFor(batch, row);
        CompanyEntity company = companyService.create(toCompanyData(row.values()), source);
        UUID leadId = createLead ? leadService.create(company.getId(), LeadPriority.MEDIUM, source).getId() : null;
        row.markImported(company.getId(), leadId);
    }

    /**
     * Completa a empresa de que a linha e duplicata.
     *
     * @return {@code false} se a empresa foi arquivada depois da analise; ela
     *     nao e alterada e a linha fica como nao importada
     */
    private boolean completeExisting(ImportBatchEntity batch, ImportRowEntity row, boolean createLead) {
        SourceData source = sourceFor(batch, row);
        UUID companyId = row.getDuplicateOfCompanyId();
        var filled = companyService.completeFromImport(companyId, toCompanyData(row.values()), source);
        if (filled.isEmpty()) {
            row.markSkipped();
            return false;
        }
        UUID leadId = createLead
                ? leadService.createIfNoneOpen(companyId, LeadPriority.MEDIUM, source).map(LeadEntity::getId).orElse(null)
                : null;
        row.markMerged(companyId, leadId, objectMapper.writeValueAsString(filled.get()));
        return true;
    }

    /** Descarta sem importar e apaga os valores das linhas (minimizacao, doc 07 §2). */
    @Transactional
    public ImportBatchEntity discard(UUID importId) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        claim(importId, user.organizationId());

        rowRepository.findByOrganizationIdAndImportIdOrderByRowNumber(user.organizationId(), importId)
                .forEach(ImportRowEntity::clearValues);
        ImportBatchEntity batch = find(importId);
        batch.discard(clock.instant());

        auditService.record(new AuditEntry(
                user.organizationId(),
                user.userId(),
                AuditAction.IMPORT_DISCARD,
                AuditOutcome.SUCCESS,
                "import",
                importId.toString(),
                null,
                null));
        return batch;
    }

    @Transactional(readOnly = true)
    public ImportBatchEntity find(UUID importId) {
        return batchRepository
                .findByIdAndOrganizationId(importId, organizationContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Import"));
    }

    @Transactional(readOnly = true)
    public Page<ImportBatchEntity> list(ImportBatchEntity.Status status, Pageable pageable) {
        UUID organizationId = organizationContext.requireOrganizationId();
        return status == null
                ? batchRepository.findByOrganizationIdOrderByCreatedAtDescIdAsc(organizationId, pageable)
                : batchRepository.findByOrganizationIdAndStatusOrderByCreatedAtDescIdAsc(organizationId, status, pageable);
    }

    /** Quantas importacoes aguardam revisao, valem, foram desfeitas ou descartadas, e quantas empresas trouxeram. */
    public record Summary(long pendingReview, long completed, long undone, long discarded, long companiesImported) {}

    @Transactional(readOnly = true)
    public Summary summary() {
        Object[] row = batchRepository.summarize(organizationContext.requireOrganizationId()).getFirst();
        return new Summary(count(row[0]), count(row[1]), count(row[2]), count(row[3]), count(row[4]));
    }

    @Transactional(readOnly = true)
    public Page<ImportRowEntity> report(UUID importId, ImportRowEntity.Status status, Pageable pageable) {
        ImportBatchEntity batch = find(importId);
        return status == null
                ? rowRepository.findByOrganizationIdAndImportIdOrderByRowNumber(
                        batch.getOrganizationId(), batch.getId(), pageable)
                : rowRepository.findByOrganizationIdAndImportIdAndStatusOrderByRowNumber(
                        batch.getOrganizationId(), batch.getId(), status, pageable);
    }

    public List<ImportRowValidator.RowError> errorsOf(ImportRowEntity row) {
        return row.getErrors() == null ? List.of() : objectMapper.readValue(row.getErrors(), ERRORS_TYPE);
    }

    public List<String> mergedFieldsOf(ImportRowEntity row) {
        return row.getMergedFields() == null ? null : objectMapper.readValue(row.getMergedFields(), STRING_LIST_TYPE);
    }

    public List<String> ignoredColumnsOf(ImportBatchEntity batch) {
        return objectMapper.readValue(batch.getIgnoredColumns(), STRING_LIST_TYPE);
    }

    /** Importacao anterior, nao descartada, do mesmo arquivo na mesma organizacao (doc 10 §4). */
    @Transactional(readOnly = true)
    public UUID previousImportOf(ImportBatchEntity batch) {
        return batchRepository
                .findFirstByOrganizationIdAndFileSha256AndIdNotAndCreatedAtBeforeAndStatusNotOrderByCreatedAtDesc(
                        batch.getOrganizationId(),
                        batch.getFileSha256(),
                        batch.getId(),
                        batch.getCreatedAt(),
                        ImportBatchEntity.Status.DISCARDED)
                .map(ImportBatchEntity::getId)
                .orElse(null);
    }

    public Map<String, String> mappingOf(ImportBatchEntity batch) {
        return objectMapper.readValue(batch.getColumnMapping(), MAPPING_TYPE);
    }

    /** Toma a importacao para confirmar ou descartar; 404 de outra organizacao, 409 se ja processada. */
    private void claim(UUID importId, UUID organizationId) {
        find(importId);
        int claimed = batchRepository.transition(
                importId, organizationId, ImportBatchEntity.Status.ANALYZED, ImportBatchEntity.Status.IMPORTING);
        if (claimed == 0) {
            throw new ConflictException("Import is not awaiting confirmation");
        }
    }

    /**
     * Valida as escolhas do operador.
     *
     * <p>Importar como nova vale para qualquer possivel duplicata; completar a
     * existente, so para duplicata de empresa ja cadastrada, porque duplicata
     * de outra linha do arquivo nao tem empresa a completar. A mesma linha nao
     * pode receber as duas escolhas.
     */
    private static void validateChosenRows(List<ImportRowEntity> rows, Set<Integer> include, Set<Integer> merge) {
        Set<Integer> duplicates = new TreeSet<>();
        Set<Integer> ofExistingCompany = new TreeSet<>();
        for (ImportRowEntity row : rows) {
            if (row.getStatus() == ImportRowEntity.Status.DUPLICATE) {
                duplicates.add(row.getRowNumber());
                if (row.getDuplicateOfCompanyId() != null) {
                    ofExistingCompany.add(row.getRowNumber());
                }
            }
        }

        Set<Integer> notDuplicates = new TreeSet<>(include);
        notDuplicates.addAll(merge);
        notDuplicates.removeAll(duplicates);
        if (!notDuplicates.isEmpty()) {
            throw new InvalidRequestException("Rows are not possible duplicates of this import: " + notDuplicates);
        }

        Set<Integer> notMergeable = new TreeSet<>(merge);
        notMergeable.removeAll(ofExistingCompany);
        if (!notMergeable.isEmpty()) {
            throw new InvalidRequestException(
                    "Rows do not duplicate an existing company and cannot be merged: " + notMergeable);
        }

        Set<Integer> both = new TreeSet<>(include);
        both.retainAll(merge);
        if (!both.isEmpty()) {
            throw new InvalidRequestException("Rows cannot be both imported and merged: " + both);
        }
    }

    /**
     * Cabecalho por campo. Com mapeamento informado, ele e seguido como veio e
     * validado; sem, cada cabecalho e reconhecido pelos apelidos do campo.
     */
    private static Map<ImportField, Integer> resolveMapping(List<String> headers, Map<String, String> mapping) {
        Map<ImportField, Integer> columns = new EnumMap<>(ImportField.class);

        if (mapping == null || mapping.isEmpty()) {
            for (int column = 0; column < headers.size(); column++) {
                int index = column;
                ImportField.guess(headers.get(column)).ifPresent(field -> columns.putIfAbsent(field, index));
            }
        } else {
            Set<Integer> used = new HashSet<>();
            mapping.forEach((key, header) -> {
                ImportField field = ImportField.fromKey(key)
                        .orElseThrow(() -> new InvalidRequestException("Unknown import field: " + key));
                int column = headers.indexOf(header == null ? "" : header.strip());
                if (column < 0) {
                    throw new InvalidRequestException("Column not found in file: " + header);
                }
                if (!used.add(column)) {
                    throw new InvalidRequestException("Column mapped to more than one field: " + header);
                }
                columns.put(field, column);
            });
        }

        List<String> missing = new ArrayList<>();
        for (ImportField field : ImportField.values()) {
            if (field.required() && !columns.containsKey(field)) {
                missing.add(field.key());
            }
        }
        if (!missing.isEmpty()) {
            throw new InvalidRequestException("Required fields without a column: " + String.join(", ", missing));
        }
        return columns;
    }

    private static Map<String, String> toMappingJson(List<String> headers, Map<ImportField, Integer> columns) {
        Map<String, String> json = new LinkedHashMap<>();
        columns.forEach((field, column) -> json.put(field.key(), headers.get(column)));
        return json;
    }

    private static SourceData sourceFor(ImportBatchEntity batch, ImportRowEntity row) {
        return new SourceData(
                SourceType.CSV_IMPORT,
                batch.getSourceName(),
                "Importacao " + batch.getId() + ", linha " + row.getRowNumber(),
                null,
                batch.getCreatedAt(),
                batch.getDatasetNote());
    }

    private static CompanyData toCompanyData(Map<ImportField, String> values) {
        return new CompanyData(
                values.get(ImportField.NAME),
                values.get(ImportField.TRADE_NAME),
                values.get(ImportField.SEGMENT),
                values.get(ImportField.CITY),
                values.get(ImportField.STATE),
                values.get(ImportField.WEBSITE_URL),
                values.get(ImportField.INSTAGRAM_URL),
                values.get(ImportField.PHONE),
                values.get(ImportField.BUSINESS_EMAIL),
                values.get(ImportField.ADDRESS),
                values.get(ImportField.NOTES));
    }

    private static String sourceName(AnalyzeCommand command) {
        String name = Texts.blankToNull(command.sourceName());
        return name != null ? name : "Importacao CSV: " + cleanFileName(command.fileName());
    }

    /** So o nome, sem caminho que alguns navegadores enviam. */
    private static String cleanFileName(String fileName) {
        String name = Optional.ofNullable(fileName).orElse("arquivo.csv");
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).strip();
        if (name.isEmpty()) {
            return "arquivo.csv";
        }
        return name.length() > 200 ? name.substring(0, 200) : name;
    }
}
