package com.jhcomp.prospect.discovery;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.jhcomp.prospect.company.domain.DuplicateSignal;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.web.PageRequests;
import com.jhcomp.prospect.shared.web.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Importacao CSV (RF-015, doc 05 §6, D14, D23).
 *
 * <p>Fluxo: {@code POST /imports/csv} analisa e devolve o relatorio;
 * {@code POST /imports/{id}/confirm} grava; {@code POST /imports/{id}/discard}
 * desiste. Nada vira empresa sem a confirmacao.
 */
@RestController
@RequestMapping("/imports")
public class ImportController {

    private static final TypeReference<LinkedHashMap<String, String>> MAPPING_TYPE = new TypeReference<>() {};

    private final ImportService importService;
    private final ObjectMapper objectMapper;

    ImportController(ImportService importService, ObjectMapper objectMapper) {
        this.importService = importService;
        this.objectMapper = objectMapper;
    }

    /**
     * Analisa o arquivo.
     *
     * @param mapping JSON opcional {@code {"name": "Cabecalho", ...}}; ausente,
     *     os cabecalhos sao reconhecidos automaticamente
     */
    @PostMapping(path = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResponse analyze(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String sourceName,
            @RequestParam(required = false) String datasetNote,
            @RequestParam(required = false) String mapping) throws IOException {

        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".csv") && !lower.endsWith(".txt")) {
            // Planilha .xlsx enviada por engano e o caso mais comum.
            throw new InvalidRequestException("File must be a CSV (save the spreadsheet as CSV UTF-8)");
        }
        if (file.getSize() > CsvTable.MAX_BYTES) {
            throw new InvalidRequestException("File exceeds the 2 MB limit");
        }
        if (sourceName != null && sourceName.length() > 120) {
            throw new InvalidRequestException("sourceName must have at most 120 characters");
        }
        if (datasetNote != null && datasetNote.length() > 1000) {
            throw new InvalidRequestException("datasetNote must have at most 1000 characters");
        }

        var analysis = importService.analyze(new ImportService.AnalyzeCommand(
                fileName, file.getBytes(), sourceName, datasetNote, parseMapping(mapping)));
        return ImportResponse.from(analysis.batch(), importService.mappingOf(analysis.batch()),
                analysis.ignoredColumns(), analysis.previousImportId());
    }

    @GetMapping
    public PageResponse<ImportResponse> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.from(importService.list(pageable),
                batch -> ImportResponse.from(batch, importService.mappingOf(batch), null, null));
    }

    @GetMapping("/{importId}")
    public ImportResponse get(@PathVariable UUID importId) {
        var batch = importService.find(importId);
        return ImportResponse.from(batch, importService.mappingOf(batch), null, null);
    }

    /** Resultado por linha (aceite do RF-015), filtravel por status. */
    @GetMapping("/{importId}/report")
    public PageResponse<ImportRowResponse> report(
            @PathVariable UUID importId,
            @RequestParam(required = false) ImportRowEntity.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.from(importService.report(importId, status, pageable),
                row -> ImportRowResponse.from(row, importService.errorsOf(row)));
    }

    @PostMapping("/{importId}/confirm")
    public ImportResponse confirm(@PathVariable UUID importId, @Valid @RequestBody ConfirmImportRequest request) {
        var batch = importService.confirm(
                importId,
                request.includeDuplicateRows() == null ? Set.of() : request.includeDuplicateRows(),
                Boolean.TRUE.equals(request.createLeads()));
        return ImportResponse.from(batch, importService.mappingOf(batch), null, null);
    }

    @PostMapping("/{importId}/discard")
    public ImportResponse discard(@PathVariable UUID importId) {
        var batch = importService.discard(importId);
        return ImportResponse.from(batch, importService.mappingOf(batch), null, null);
    }

    private Map<String, String> parseMapping(String mapping) {
        if (mapping == null || mapping.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(mapping, MAPPING_TYPE);
        } catch (JacksonException exception) {
            throw new InvalidRequestException("mapping must be a JSON object of field to column");
        }
    }

    /**
     * Confirmacao.
     *
     * @param includeDuplicateRows linhas marcadas como possivel duplicata que o
     *     operador decidiu importar mesmo assim
     * @param createLeads abre um lead {@code NEW} para cada empresa importada
     */
    public record ConfirmImportRequest(@Size(max = 2000) Set<Integer> includeDuplicateRows, Boolean createLeads) {}

    public record ImportResponse(
            UUID id,
            String fileName,
            String sourceName,
            String datasetNote,
            ImportBatchEntity.Status status,
            Map<String, String> columnMapping,
            int totalRows,
            int validRows,
            int rejectedRows,
            int duplicateRows,
            int importedRows,
            int leadsCreated,
            Instant createdAt,
            Instant completedAt,
            List<String> ignoredColumns,
            UUID previousImportId) {

        static ImportResponse from(
                ImportBatchEntity batch, Map<String, String> mapping, List<String> ignoredColumns, UUID previousImportId) {
            return new ImportResponse(
                    batch.getId(),
                    batch.getFileName(),
                    batch.getSourceName(),
                    batch.getDatasetNote(),
                    batch.getStatus(),
                    mapping,
                    batch.getTotalRows(),
                    batch.getValidRows(),
                    batch.getRejectedRows(),
                    batch.getDuplicateRows(),
                    batch.getImportedRows(),
                    batch.getLeadsCreated(),
                    batch.getCreatedAt(),
                    batch.getCompletedAt(),
                    ignoredColumns,
                    previousImportId);
        }
    }

    public record ImportRowResponse(
            int rowNumber,
            ImportRowEntity.Status status,
            Map<String, String> values,
            List<ImportRowValidator.RowError> errors,
            UUID duplicateOfCompanyId,
            Integer duplicateOfRowNumber,
            Set<DuplicateSignal> duplicateSignals,
            UUID companyId,
            UUID leadId) {

        static ImportRowResponse from(ImportRowEntity row, List<ImportRowValidator.RowError> errors) {
            Map<String, String> values = new LinkedHashMap<>();
            row.values().forEach((field, value) -> values.put(field.key(), value));
            return new ImportRowResponse(
                    row.getRowNumber(),
                    row.getStatus(),
                    values,
                    errors,
                    row.getDuplicateOfCompanyId(),
                    row.getDuplicateOfRowNumber(),
                    DuplicateClassifier.parseSignals(row.getDuplicateSignals()),
                    row.getCompanyId(),
                    row.getLeadId());
        }
    }
}
