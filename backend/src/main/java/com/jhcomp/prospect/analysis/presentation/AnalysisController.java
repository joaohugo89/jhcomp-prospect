package com.jhcomp.prospect.analysis.presentation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.analysis.application.AnalysisService;
import com.jhcomp.prospect.analysis.application.AnalysisService.AnalysisDetail;
import com.jhcomp.prospect.analysis.domain.AnalysisStatus;
import com.jhcomp.prospect.analysis.domain.Confidence;
import com.jhcomp.prospect.analysis.domain.Criterion;
import com.jhcomp.prospect.analysis.domain.EvidenceType;
import com.jhcomp.prospect.analysis.domain.FindingStatus;
import com.jhcomp.prospect.analysis.domain.ReviewStatus;
import com.jhcomp.prospect.analysis.infrastructure.persistence.AnalysisEvidenceEntity;
import com.jhcomp.prospect.analysis.infrastructure.persistence.AnalysisFindingEntity;
import com.jhcomp.prospect.analysis.infrastructure.persistence.LeadAnalysisEntity;
import com.jhcomp.prospect.shared.web.PageRequests;
import com.jhcomp.prospect.shared.web.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Analises de presenca digital (RF-030 a RF-033, doc 05 §8).
 *
 * <p>Pedir analise responde {@code 202 Accepted}: a verificacao roda em
 * segundo plano, e o cliente acompanha pelo status.
 */
@RestController
public class AnalysisController {

    private final AnalysisService analysisService;

    AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/leads/{leadId}/analyses")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnalysisSummary request(@PathVariable UUID leadId) {
        return AnalysisSummary.from(analysisService.request(leadId));
    }

    @GetMapping("/leads/{leadId}/analyses")
    public PageResponse<AnalysisSummary> list(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.from(analysisService.list(leadId, pageable), AnalysisSummary::from);
    }

    @GetMapping("/analyses/{analysisId}")
    public AnalysisDetailResponse get(@PathVariable UUID analysisId) {
        return AnalysisDetailResponse.from(analysisService.find(analysisId));
    }

    /** Revisao humana (RF-033): mantem ou corrige o estado; a nota vira evidencia. */
    @PatchMapping("/findings/{findingId}/review")
    public AnalysisDetailResponse review(@PathVariable UUID findingId, @Valid @RequestBody ReviewRequest request) {
        return AnalysisDetailResponse.from(analysisService.review(findingId, request.status(), request.note()));
    }

    public record ReviewRequest(@NotNull FindingStatus status, @Size(max = 500) String note) {}

    /**
     * Analise sem achados.
     *
     * <p>{@code score} so existe com a analise concluida: pendente ou com
     * falha nao e "score zero" (D7).
     */
    public record AnalysisSummary(
            UUID id,
            UUID leadId,
            AnalysisStatus status,
            Integer score,
            String scoreVersion,
            String failureReason,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt) {

        static AnalysisSummary from(LeadAnalysisEntity analysis) {
            return new AnalysisSummary(
                    analysis.getId(),
                    analysis.getLeadId(),
                    analysis.getStatus(),
                    analysis.getScore(),
                    analysis.getScoreVersion(),
                    analysis.getFailureReason(),
                    analysis.getCreatedAt(),
                    analysis.getStartedAt(),
                    analysis.getCompletedAt());
        }
    }

    public record EvidenceResponse(
            EvidenceType type, String referenceUrl, String description, Instant observedAt, String capturedBy,
            String contentHash) {

        static EvidenceResponse from(AnalysisEvidenceEntity evidence) {
            return new EvidenceResponse(
                    evidence.getType(),
                    evidence.getReferenceUrl(),
                    evidence.getDescription(),
                    evidence.getObservedAt(),
                    evidence.getCapturedBy(),
                    evidence.getContentHash());
        }
    }

    /** Achado com a decomposicao dos pontos (RF-032) e a revisao (RF-033). */
    public record FindingResponse(
            UUID id,
            Criterion criterion,
            FindingStatus status,
            FindingStatus originalStatus,
            String description,
            Confidence confidence,
            int pointsAwarded,
            int maxPoints,
            ReviewStatus reviewStatus,
            UUID reviewedByUserId,
            Instant reviewedAt,
            String reviewNote,
            List<EvidenceResponse> evidence) {

        static FindingResponse from(AnalysisFindingEntity finding, List<AnalysisEvidenceEntity> evidence) {
            return new FindingResponse(
                    finding.getId(),
                    finding.getCriterion(),
                    finding.getStatus(),
                    finding.getOriginalStatus(),
                    finding.getDescription(),
                    finding.getConfidence(),
                    finding.getPointsAwarded(),
                    finding.getMaxPoints(),
                    finding.getReviewStatus(),
                    finding.getReviewedByUserId(),
                    finding.getReviewedAt(),
                    finding.getReviewNote(),
                    evidence.stream().map(EvidenceResponse::from).toList());
        }
    }

    public record AnalysisDetailResponse(AnalysisSummary analysis, List<FindingResponse> findings) {

        static AnalysisDetailResponse from(AnalysisDetail detail) {
            return new AnalysisDetailResponse(
                    AnalysisSummary.from(detail.analysis()),
                    detail.findings().stream()
                            .map(finding -> FindingResponse.from(
                                    finding, detail.evidenceByFinding().getOrDefault(finding.getId(), List.of())))
                            .toList());
        }
    }
}
