package com.jhcomp.prospect.analysis.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.analysis.domain.AnalysisStatus;
import com.jhcomp.prospect.analysis.domain.Criterion;
import com.jhcomp.prospect.analysis.domain.EvidenceType;
import com.jhcomp.prospect.analysis.domain.FindingStatus;
import com.jhcomp.prospect.analysis.domain.ScoreFormula;
import com.jhcomp.prospect.analysis.infrastructure.persistence.AnalysisEvidenceEntity;
import com.jhcomp.prospect.analysis.infrastructure.persistence.AnalysisEvidenceRepository;
import com.jhcomp.prospect.analysis.infrastructure.persistence.AnalysisFindingEntity;
import com.jhcomp.prospect.analysis.infrastructure.persistence.AnalysisFindingRepository;
import com.jhcomp.prospect.analysis.infrastructure.persistence.LeadAnalysisEntity;
import com.jhcomp.prospect.analysis.infrastructure.persistence.LeadAnalysisRepository;
import com.jhcomp.prospect.lead.application.LeadService;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.jobs.JobQueue;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Requesting, reading and reviewing analyses (RF-030 to RF-033).
 *
 * <p>Execution lives in {@link AnalysisJobHandler}, in the background: the
 * request only writes the analysis and the job, in the same transaction.
 */
@Service
public class AnalysisService {

    static final int MAX_ATTEMPTS = 3;

    private static final EnumSet<AnalysisStatus> ACTIVE = EnumSet.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING);

    private final LeadAnalysisRepository analysisRepository;
    private final AnalysisFindingRepository findingRepository;
    private final AnalysisEvidenceRepository evidenceRepository;
    private final LeadService leadService;
    private final JobQueue jobQueue;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    AnalysisService(
            LeadAnalysisRepository analysisRepository,
            AnalysisFindingRepository findingRepository,
            AnalysisEvidenceRepository evidenceRepository,
            LeadService leadService,
            JobQueue jobQueue,
            OrganizationContext organizationContext,
            Clock clock) {
        this.analysisRepository = analysisRepository;
        this.findingRepository = findingRepository;
        this.evidenceRepository = evidenceRepository;
        this.leadService = leadService;
        this.jobQueue = jobQueue;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    /** An analysis with its findings and evidence, for showing the score breakdown. */
    public record AnalysisDetail(
            LeadAnalysisEntity analysis,
            List<AnalysisFindingEntity> findings,
            Map<UUID, List<AnalysisEvidenceEntity>> evidenceByFinding) {}

    /**
     * Requests the lead's analysis (RF-030).
     *
     * <p>Idempotent: with one already running it returns that one rather than
     * enqueuing another. A lead marked do-not-contact is not analysed: an
     * analysis exists only to prepare an approach, and processing the data of
     * someone who asked not to be contacted serves no purpose (doc 07 §2).
     */
    @Transactional
    public LeadAnalysisEntity request(UUID leadId) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        LeadEntity lead = leadService.find(leadId);
        if (lead.getStatus() == LeadStatus.DO_NOT_CONTACT) {
            throw new ConflictException("Lead is marked as do not contact");
        }

        var active = analysisRepository.findFirstByOrganizationIdAndLeadIdAndStatusIn(
                user.organizationId(), lead.getId(), ACTIVE);
        if (active.isPresent()) {
            return active.get();
        }

        LeadAnalysisEntity analysis = analysisRepository.save(
                new LeadAnalysisEntity(user.organizationId(), lead.getId(), user.userId(), clock.instant()));
        jobQueue.enqueue(
                user.organizationId(),
                AnalysisJobHandler.JOB_TYPE,
                AnalysisJobHandler.payloadFor(analysis.getId()),
                MAX_ATTEMPTS,
                analysis.getId().toString());
        return analysis;
    }

    @Transactional(readOnly = true)
    public Page<LeadAnalysisEntity> list(UUID leadId, Pageable pageable) {
        LeadEntity lead = leadService.find(leadId);
        return analysisRepository.findByOrganizationIdAndLeadIdOrderByCreatedAtDescIdAsc(
                lead.getOrganizationId(), lead.getId(), pageable);
    }

    /**
     * The confirmed criteria of the latest completed analysis (D3), in a fixed
     * order. Empty when there is no analysis: what has not been confirmed
     * cannot be asserted in a message (doc 06 §8).
     */
    @Transactional(readOnly = true)
    public List<Criterion> confirmedCriteria(UUID leadId) {
        LeadEntity lead = leadService.find(leadId);
        return analysisRepository
                .findFirstByOrganizationIdAndLeadIdAndStatusOrderByCompletedAtDescIdAsc(
                        lead.getOrganizationId(), lead.getId(), AnalysisStatus.COMPLETED)
                .map(analysis -> findingRepository.findByOrganizationIdAndAnalysisId(
                                lead.getOrganizationId(), analysis.getId()).stream()
                        .filter(finding -> finding.getStatus() == FindingStatus.CONFIRMED)
                        .map(AnalysisFindingEntity::getCriterion)
                        .sorted()
                        .toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public AnalysisDetail find(UUID analysisId) {
        return detail(requireAnalysis(analysisId));
    }

    /**
     * Reviews a finding (RF-033) and recomputes the score (D3).
     *
     * <p>The finding is looked up by the session's organisation. The composite
     * keys from V8 guarantee that finding, analysis and lead belong to the same
     * organisation, so that one check covers the whole chain (D8).
     */
    @Transactional
    public AnalysisDetail review(UUID findingId, FindingStatus newStatus, String note) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        AnalysisFindingEntity finding = findingRepository
                .findByIdAndOrganizationId(findingId, user.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Finding"));
        LeadAnalysisEntity analysis = requireAnalysis(finding.getAnalysisId());
        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            throw new ConflictException("Only completed analyses can be reviewed");
        }

        Instant now = clock.instant();
        FindingStatus previous = finding.getStatus();
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        finding.review(newStatus, cleanNote, user.userId(), now);

        if (cleanNote != null || previous != newStatus) {
            evidenceRepository.save(new AnalysisEvidenceEntity(
                    user.organizationId(),
                    finding.getId(),
                    EvidenceType.OPERATOR_NOTE,
                    null,
                    cleanNote != null ? cleanNote : "Revisão do operador: " + previous + " para " + newStatus + ".",
                    now,
                    "OPERATOR:" + user.userId(),
                    null));
        }

        rescore(analysis);
        return detail(analysis);
    }

    /**
     * Recomputes the analysis from the current states and, if it is the lead's
     * latest completed one, propagates to the lead. Same states, same score.
     */
    private void rescore(LeadAnalysisEntity analysis) {
        Map<Criterion, FindingStatus> statuses = new EnumMap<>(Criterion.class);
        findingRepository.findByOrganizationIdAndAnalysisId(analysis.getOrganizationId(), analysis.getId())
                .forEach(finding -> statuses.put(finding.getCriterion(), finding.getStatus()));
        int score = ScoreFormula.score(statuses);
        analysis.rescore(score);

        boolean latest = analysisRepository
                .findFirstByOrganizationIdAndLeadIdAndStatusOrderByCompletedAtDescIdAsc(
                        analysis.getOrganizationId(), analysis.getLeadId(), AnalysisStatus.COMPLETED)
                .map(last -> last.getId().equals(analysis.getId()))
                .orElse(false);
        if (latest) {
            leadService.applyScore(analysis.getOrganizationId(), analysis.getLeadId(), score, ScoreFormula.VERSION);
        }
    }

    private LeadAnalysisEntity requireAnalysis(UUID analysisId) {
        return analysisRepository
                .findByIdAndOrganizationId(analysisId, organizationContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Analysis"));
    }

    private AnalysisDetail detail(LeadAnalysisEntity analysis) {
        List<AnalysisFindingEntity> findings = findingRepository
                .findByOrganizationIdAndAnalysisId(analysis.getOrganizationId(), analysis.getId())
                .stream()
                // The formula's order, which is the order of the table in doc 06 §7.
                .sorted(Comparator.comparing(AnalysisFindingEntity::getCriterion))
                .toList();
        Map<UUID, List<AnalysisEvidenceEntity>> evidence = findings.isEmpty()
                ? Map.of()
                : evidenceRepository
                        .findByOrganizationIdAndFindingIdInOrderByObservedAtAscIdAsc(
                                analysis.getOrganizationId(),
                                findings.stream().map(AnalysisFindingEntity::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(AnalysisEvidenceEntity::getFindingId));
        return new AnalysisDetail(analysis, findings, evidence);
    }
}
