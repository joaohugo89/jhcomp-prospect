package com.jhcomp.prospect.analysis.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhcomp.prospect.analysis.domain.CompanyProfile;
import com.jhcomp.prospect.analysis.domain.Criterion;
import com.jhcomp.prospect.analysis.domain.FindingDraft;
import com.jhcomp.prospect.analysis.domain.FindingStatus;
import com.jhcomp.prospect.analysis.domain.FindingsBuilder;
import com.jhcomp.prospect.analysis.domain.ScoreFormula;
import com.jhcomp.prospect.analysis.domain.ServiceArea;
import com.jhcomp.prospect.analysis.domain.WebsiteObservation;
import com.jhcomp.prospect.analysis.infrastructure.persistence.AnalysisEvidenceEntity;
import com.jhcomp.prospect.analysis.infrastructure.persistence.AnalysisEvidenceRepository;
import com.jhcomp.prospect.analysis.infrastructure.persistence.AnalysisFindingEntity;
import com.jhcomp.prospect.analysis.infrastructure.persistence.AnalysisFindingRepository;
import com.jhcomp.prospect.analysis.infrastructure.persistence.LeadAnalysisEntity;
import com.jhcomp.prospect.analysis.infrastructure.persistence.LeadAnalysisRepository;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.lead.application.LeadService;
import com.jhcomp.prospect.shared.jobs.JobEntity;
import com.jhcomp.prospect.shared.jobs.JobHandler;

import tools.jackson.databind.ObjectMapper;

/**
 * Executa a analise de presenca digital em segundo plano (RF-030, D15).
 *
 * <p>Tres passos, e so o primeiro e o ultimo em transacao: ler o cadastro,
 * verificar o site sem transacao aberta (chamada de rede, regra do projeto) e
 * gravar achados, score e status de uma vez.
 *
 * <p>A organizacao vem do job, nunca do payload, que so carrega o id da
 * analise; a analise e buscada com essa organizacao.
 */
@Component
public class AnalysisJobHandler implements JobHandler {

    public static final String JOB_TYPE = "LEAD_ANALYSIS";

    private record Payload(UUID analysisId) {}

    private record Snapshot(LeadAnalysisEntity analysis, CompanyProfile profile) {}

    private final LeadAnalysisRepository analysisRepository;
    private final AnalysisFindingRepository findingRepository;
    private final AnalysisEvidenceRepository evidenceRepository;
    private final LeadService leadService;
    private final WebsiteChecker websiteChecker;
    private final TransactionTemplate transaction;
    private final ObjectMapper objectMapper;
    private final ServiceArea serviceArea;
    private final Clock clock;

    AnalysisJobHandler(
            LeadAnalysisRepository analysisRepository,
            AnalysisFindingRepository findingRepository,
            AnalysisEvidenceRepository evidenceRepository,
            LeadService leadService,
            WebsiteChecker websiteChecker,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            @Value("${jhcomp.analysis.served-segments:}") String servedSegments,
            @Value("${jhcomp.analysis.served-states:MG}") String servedStates,
            Clock clock) {
        this.analysisRepository = analysisRepository;
        this.findingRepository = findingRepository;
        this.evidenceRepository = evidenceRepository;
        this.leadService = leadService;
        this.websiteChecker = websiteChecker;
        this.transaction = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
        this.serviceArea = new ServiceArea(split(servedSegments), split(servedStates));
        this.clock = clock;
    }

    static String payloadFor(UUID analysisId) {
        return "{\"analysisId\":\"" + analysisId + "\"}";
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public void handle(JobEntity job) {
        UUID analysisId = objectMapper.readValue(job.getPayload(), Payload.class).analysisId();
        UUID organizationId = job.getOrganizationId();

        Optional<Snapshot> snapshot = transaction.execute(status -> start(organizationId, analysisId));
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }

        WebsiteObservation observation = websiteChecker.inspect(snapshot.get().profile().websiteUrl());

        transaction.executeWithoutResult(status -> finish(organizationId, analysisId, snapshot.get().profile(), observation));
    }

    @Override
    public void onGiveUp(JobEntity job) {
        UUID analysisId = objectMapper.readValue(job.getPayload(), Payload.class).analysisId();
        transaction.executeWithoutResult(status -> analysisRepository
                .findByIdAndOrganizationId(analysisId, job.getOrganizationId())
                .filter(LeadAnalysisEntity::isActive)
                .ifPresent(analysis -> analysis.fail("A verificação não pôde ser concluída.", clock.instant())));
    }

    /** Marca o inicio e le o cadastro. Vazio se a analise ja terminou (job repetido). */
    private Optional<Snapshot> start(UUID organizationId, UUID analysisId) {
        Optional<LeadAnalysisEntity> found = analysisRepository
                .findByIdAndOrganizationId(analysisId, organizationId)
                .filter(LeadAnalysisEntity::isActive);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        LeadAnalysisEntity analysis = found.get();
        analysis.start(clock.instant());
        CompanyEntity company = leadService.findInOrganization(organizationId, analysis.getLeadId())
                .orElseThrow(() -> new IllegalStateException("Lead of analysis not found"))
                .getCompany();
        return Optional.of(new Snapshot(analysis, new CompanyProfile(
                company.getWebsiteUrl(),
                company.getSegment(),
                company.getState(),
                company.getPhone() != null,
                company.getBusinessEmail() != null,
                company.getInstagramUrl() != null)));
    }

    private void finish(UUID organizationId, UUID analysisId, CompanyProfile profile, WebsiteObservation observation) {
        LeadAnalysisEntity analysis = analysisRepository.findByIdAndOrganizationId(analysisId, organizationId)
                .filter(LeadAnalysisEntity::isActive)
                .orElse(null);
        if (analysis == null) {
            return;
        }
        Instant now = clock.instant();
        Map<Criterion, FindingStatus> statuses = new EnumMap<>(Criterion.class);

        for (FindingDraft draft : FindingsBuilder.build(profile, observation, serviceArea, now)) {
            AnalysisFindingEntity finding = findingRepository.save(new AnalysisFindingEntity(
                    organizationId, analysisId, draft.criterion(), draft.status(), draft.description(),
                    draft.confidence()));
            for (FindingDraft.Evidence evidence : draft.evidence()) {
                evidenceRepository.save(new AnalysisEvidenceEntity(
                        organizationId, finding.getId(), evidence.type(), evidence.referenceUrl(),
                        evidence.description(), evidence.observedAt(), evidence.capturedBy(),
                        evidence.contentHash()));
            }
            statuses.put(draft.criterion(), draft.status());
        }

        int score = ScoreFormula.score(statuses);
        analysis.complete(score, now);
        leadService.applyScore(organizationId, analysis.getLeadId(), score, ScoreFormula.VERSION);
    }

    private static Set<String> split(String values) {
        return Arrays.stream(values.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }
}
