package com.jhcomp.prospect.lead.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Transicoes do ciclo do lead (doc 06 §2). Unitario, sem Spring. */
@DisplayName("LeadStatusTransitions")
class LeadStatusTransitionsTest {

    @Test
    @DisplayName("lead nasce NEW")
    void initialIsNew() {
        assertThat(LeadStatusTransitions.initial()).isEqualTo(LeadStatus.NEW);
    }

    @Test
    @DisplayName("caminho comercial completo e permitido")
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
    @DisplayName("nao pula etapas")
    void noSkipping() {
        assertThat(LeadStatusTransitions.isAllowed(LeadStatus.NEW, LeadStatus.CONTACTED)).isFalse();
        assertThat(LeadStatusTransitions.isAllowed(LeadStatus.NEW, LeadStatus.WON)).isFalse();
        assertThat(LeadStatusTransitions.isAllowed(LeadStatus.QUALIFIED, LeadStatus.CONTACTED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = LeadStatus.class, names = {"WON", "LOST", "DO_NOT_CONTACT"})
    @DisplayName("terminais e bloqueio nao tem saida")
    void noExit(LeadStatus from) {
        assertThat(LeadStatusTransitions.allowedFrom(from)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = LeadStatus.class, names = {"WON", "LOST", "DO_NOT_CONTACT"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("DO_NOT_CONTACT e alcancavel de todo estado aberto")
    void doNotContactFromAnyOpenState(LeadStatus from) {
        assertThat(LeadStatusTransitions.isAllowed(from, LeadStatus.DO_NOT_CONTACT)).isTrue();
    }

    @Test
    @DisplayName("contato prematuro pode voltar a qualificado")
    void contactedBackToQualified() {
        assertThat(LeadStatusTransitions.isAllowed(LeadStatus.CONTACTED, LeadStatus.QUALIFIED)).isTrue();
    }
}
