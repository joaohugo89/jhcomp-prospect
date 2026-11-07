package com.jhcomp.prospect.lead.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The lead lifecycle's transitions (doc 06 §2). A unit test, without Spring. */
@DisplayName("LeadStatusTransitions")
class LeadStatusTransitionsTest {

    @Test
    @DisplayName("a lead starts NEW")
    void initialIsNew() {
        assertThat(LeadStatusTransitions.initial()).isEqualTo(LeadStatus.NEW);
    }

    @Test
    @DisplayName("the full commercial path is allowed")
    void happyPath() {
        LeadStatus[] path = {
            LeadStatus.NEW, LeadStatus.QUALIFIED, LeadStatus.CONTACT_APPROVED, LeadStatus.CONTACTED,
            LeadStatus.IN_CONVERSATION, LeadStatus.PROPOSAL_SENT, LeadStatus.WON
        };
        for (int i = 1; i < path.length; i++) {
            assertThat(LeadStatusTransitions.isAllowed(path[i - 1], path[i]))
                    .describedAs("%s -> %s", path[i - 1], path[i])
                    .isTrue();
        }
    }

    @Test
    @DisplayName("it does not skip stages")
    void noSkipping() {
        assertThat(LeadStatusTransitions.isAllowed(LeadStatus.NEW, LeadStatus.CONTACTED)).isFalse();
        assertThat(LeadStatusTransitions.isAllowed(LeadStatus.NEW, LeadStatus.WON)).isFalse();
        assertThat(LeadStatusTransitions.isAllowed(LeadStatus.QUALIFIED, LeadStatus.CONTACTED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = LeadStatus.class, names = {"WON", "LOST", "DO_NOT_CONTACT"})
    @DisplayName("terminal states and the block have no way out")
    void noExit(LeadStatus from) {
        assertThat(LeadStatusTransitions.allowedFrom(from)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = LeadStatus.class, names = {"WON", "LOST", "DO_NOT_CONTACT"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("DO_NOT_CONTACT is reachable from every open state")
    void doNotContactFromAnyOpenState(LeadStatus from) {
        assertThat(LeadStatusTransitions.isAllowed(from, LeadStatus.DO_NOT_CONTACT)).isTrue();
    }

    @Test
    @DisplayName("a premature contact can go back to qualified")
    void contactedBackToQualified() {
        assertThat(LeadStatusTransitions.isAllowed(LeadStatus.CONTACTED, LeadStatus.QUALIFIED)).isTrue();
    }
}
