package com.jhcomp.prospect.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jhcomp.prospect.analysis.domain.WebsiteObservation.Outcome;

/** Findings and score (doc 06 §6 and §7, product rule 3). A unit test, with no network. */
@DisplayName("FindingsBuilder and ScoreFormula")
class FindingsBuilderTest {

    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final ServiceArea AREA = new ServiceArea(Set.of("barbershop"), Set.of("mg"));
    private static final CompanyProfile WITH_SITE =
            new CompanyProfile("loja.example.test", "BARBERSHOP", "MG", true, false, false);

    private static WebsiteObservation page(boolean https, boolean viewport, boolean contact) {
        return new WebsiteObservation(Outcome.PAGE_READ, "https://loja.example.test", "https://loja.example.test/",
                https, 200, viewport, contact, "a".repeat(64), null, NOW);
    }

    private static Map<Criterion, FindingDraft> byCriterion(List<FindingDraft> findings) {
        return findings.stream().collect(Collectors.toMap(FindingDraft::criterion, Function.identity()));
    }

    private static int score(List<FindingDraft> findings) {
        Map<Criterion, FindingStatus> statuses = new EnumMap<>(Criterion.class);
        findings.forEach(finding -> statuses.put(finding.criterion(), finding.status()));
        return ScoreFormula.score(statuses);
    }

    @Test
    @DisplayName("the criteria add up to exactly 100, as the table in doc 06 §7 does")
    void maxIsHundred() {
        assertThat(Arrays.stream(Criterion.values()).mapToInt(Criterion::maxPoints).sum()).isEqualTo(100);
    }

    @Test
    @DisplayName("one finding per criterion, always")
    void oneFindingPerCriterion() {
        assertThat(byCriterion(FindingsBuilder.build(WITH_SITE, page(true, true, true), AREA, NOW)))
                .containsOnlyKeys(Criterion.values());
    }

    @Test
    @DisplayName("a site with no HTTPS, no viewport and no contact scores the problems observed")
    void observedProblemsScore() {
        var findings = byCriterion(FindingsBuilder.build(WITH_SITE, page(false, false, false), AREA, NOW));

        assertThat(findings.get(Criterion.SITE_ABSENT).status()).isEqualTo(FindingStatus.CONTRADICTED);
        assertThat(findings.get(Criterion.NO_HTTPS).status()).isEqualTo(FindingStatus.CONFIRMED);
        assertThat(findings.get(Criterion.NO_MOBILE_VIEWPORT).status()).isEqualTo(FindingStatus.CONFIRMED);
        assertThat(findings.get(Criterion.NO_CONTACT_LINK).status()).isEqualTo(FindingStatus.CONFIRMED);
        // An HTML signal: medium confidence, and the human review decides.
        assertThat(findings.get(Criterion.NO_CONTACT_LINK).confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(findings.get(Criterion.NO_CONTACT_LINK).evidence().getFirst().contentHash()).hasSize(64);
    }

    @Test
    @DisplayName("a site with no problems and a served company sums only the positive criteria")
    void healthySite() {
        var findings = FindingsBuilder.build(WITH_SITE, page(true, true, true), AREA, NOW);

        // Business information 15 + segment 10 + location 10.
        assertThat(score(findings)).isEqualTo(35);
    }

    /** Rule 3: not found is not proof of absence. */
    @Test
    @DisplayName("with no website on file, the absence stays unverified and scores nothing")
    void noWebsiteIsNotConfirmedAbsence() {
        var profile = new CompanyProfile(null, "BARBERSHOP", "MG", true, false, false);
        var findings = byCriterion(FindingsBuilder.build(profile, WebsiteObservation.noWebsite(NOW), AREA, NOW));

        assertThat(findings.get(Criterion.SITE_ABSENT).status()).isEqualTo(FindingStatus.NOT_VERIFIED);
        assertThat(findings.get(Criterion.SITE_ABSENT).points()).isZero();
        assertThat(findings.get(Criterion.NO_HTTPS).status()).isEqualTo(FindingStatus.NOT_APPLICABLE);
        assertThat(findings.get(Criterion.NO_MOBILE_VIEWPORT).status()).isEqualTo(FindingStatus.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("a site that is down confirms neither absence nor a technical problem")
    void unreachableSiteScoresNothing() {
        var unreachable = new WebsiteObservation(Outcome.UNREACHABLE, "https://loja.example.test", null, null, null,
                false, false, null, "DNS", NOW);
        var findings = FindingsBuilder.build(WITH_SITE, unreachable, AREA, NOW);
        var byCriterion = byCriterion(findings);

        assertThat(byCriterion.get(Criterion.SITE_ABSENT).status()).isEqualTo(FindingStatus.NOT_VERIFIED);
        assertThat(byCriterion.get(Criterion.NO_HTTPS).status()).isEqualTo(FindingStatus.NOT_VERIFIED);
        assertThat(byCriterion.get(Criterion.NO_CONTACT_LINK).status()).isEqualTo(FindingStatus.NOT_VERIFIED);
        assertThat(byCriterion.get(Criterion.SITE_ABSENT).evidence().getFirst().description()).contains("DNS");
        // Only the criteria that do not depend on the site.
        assertThat(score(findings)).isEqualTo(35);
    }

    @Test
    @DisplayName("robots.txt forbidding the page leaves the page signals unverified")
    void robotsDisallowed() {
        var robots = new WebsiteObservation(Outcome.ROBOTS_DISALLOWED, "https://loja.example.test", null, true, null,
                false, false, null, null, NOW);
        var findings = byCriterion(FindingsBuilder.build(WITH_SITE, robots, AREA, NOW));

        assertThat(findings.get(Criterion.NO_MOBILE_VIEWPORT).status()).isEqualTo(FindingStatus.NOT_VERIFIED);
        assertThat(findings.get(Criterion.NO_MOBILE_VIEWPORT).evidence().getFirst().type())
                .isEqualTo(EvidenceType.ROBOTS_TXT);
        // HTTPS is a fact about the connection, not about reading the page: it stays verifiable.
        assertThat(findings.get(Criterion.NO_HTTPS).status()).isEqualTo(FindingStatus.CONTRADICTED);
    }

    @Test
    @DisplayName("booking or catalogue is never asserted automatically")
    void bookingIsManual() {
        var findings = byCriterion(FindingsBuilder.build(WITH_SITE, page(true, true, true), AREA, NOW));

        assertThat(findings.get(Criterion.NO_ONLINE_BOOKING_OR_CATALOG).status()).isEqualTo(FindingStatus.NOT_VERIFIED);
    }

    @Test
    @DisplayName("an incomplete record and a location outside the area served score nothing, without penalising")
    void positiveCriteria() {
        var outside = new CompanyProfile("loja.example.test", null, "SP", false, false, false);
        var findings = byCriterion(FindingsBuilder.build(outside, page(true, true, true), AREA, NOW));

        assertThat(findings.get(Criterion.BUSINESS_INFO_SUFFICIENT).status()).isEqualTo(FindingStatus.NOT_FOUND);
        assertThat(findings.get(Criterion.SERVICE_FIT).status()).isEqualTo(FindingStatus.NOT_FOUND);
        assertThat(findings.get(Criterion.LOCATION_SERVED).status()).isEqualTo(FindingStatus.CONTRADICTED);
        assertThat(findings.values()).allSatisfy(finding -> assertThat(finding.points()).isZero());
    }

    @Test
    @DisplayName("with no area configured, service fit and location are left for review")
    void noServiceAreaConfigured() {
        var empty = new ServiceArea(Set.of(), Set.of());
        var findings = byCriterion(FindingsBuilder.build(WITH_SITE, page(true, true, true), empty, NOW));

        assertThat(findings.get(Criterion.SERVICE_FIT).status()).isEqualTo(FindingStatus.NOT_VERIFIED);
        assertThat(findings.get(Criterion.LOCATION_SERVED).status()).isEqualTo(FindingStatus.NOT_VERIFIED);
    }

    @Test
    @DisplayName("the score is a pure function of the states: only CONFIRMED scores")
    void onlyConfirmedScores() {
        for (FindingStatus status : FindingStatus.values()) {
            int expected = status == FindingStatus.CONFIRMED ? 25 : 0;
            assertThat(ScoreFormula.pointsFor(Criterion.SITE_ABSENT, status)).describedAs(status.name()).isEqualTo(expected);
        }
    }
}
