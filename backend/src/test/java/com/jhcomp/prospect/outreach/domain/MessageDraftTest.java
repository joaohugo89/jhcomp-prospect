package com.jhcomp.prospect.outreach.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Message draft: sanitising and per-channel limits")
class MessageDraftTest {

    @Test
    @DisplayName("accepts a body at the channel limit and refuses one character more")
    void enforcesChannelLimit() {
        String atLimit = "a".repeat(OutreachChannel.WHATSAPP.maxBodyLength());

        assertThat(MessageDraft.of(OutreachChannel.WHATSAPP, null, atLimit, List.of()).draft()).isPresent();
        assertThat(MessageDraft.of(OutreachChannel.WHATSAPP, null, atLimit + "a", List.of()).violation())
                .contains("1000");
        assertThat(MessageDraft.of(OutreachChannel.EMAIL, null, atLimit + "a", List.of()).draft()).isPresent();
    }

    @Test
    @DisplayName("refuses a body that is empty, or only spaces and control characters")
    void rejectsEmptyBody() {
        assertThat(MessageDraft.of(OutreachChannel.EMAIL, null, " \n\u0000 ", List.of()).draft()).isEmpty();
        assertThat(MessageDraft.of(OutreachChannel.EMAIL, null, null, List.of()).draft()).isEmpty();
    }

    @Test
    @DisplayName("a subject only for e-mail")
    void subjectOnlyForEmail() {
        assertThat(MessageDraft.of(OutreachChannel.WHATSAPP, "Assunto", "Corpo", List.of()).violation())
                .contains("EMAIL");
        assertThat(MessageDraft.of(OutreachChannel.EMAIL, "Assunto", "Corpo", List.of()).draft())
                .get().extracting(MessageDraft::subject).isEqualTo("Assunto");
        // A blank subject is equivalent to an absent one, outside e-mail too.
        assertThat(MessageDraft.of(OutreachChannel.WHATSAPP, "  ", "Corpo", List.of()).draft()).isPresent();
    }

    @Test
    @DisplayName("removes control characters and preserves line breaks")
    void stripsControlCharacters() {
        MessageDraft draft = MessageDraft.of(
                OutreachChannel.EMAIL, "Linha\r\nquebrada", "Ola\u0007\r\nmundo\u001b[31m", List.of()).draft().orElseThrow();

        assertThat(draft.body()).isEqualTo("Ola\nmundo[31m");
        assertThat(draft.subject()).isEqualTo("Linha quebrada");
    }

    @Test
    @DisplayName("unverified claims become one line each, with no empty ones, under a cap")
    void normalizesClaims() {
        MessageDraft draft = MessageDraft.of(
                OutreachChannel.EMAIL, null, "Corpo", List.of("  primeira\nlinha ", "", "segunda")).draft().orElseThrow();
        assertThat(draft.unverifiedClaims()).containsExactly("primeira linha", "segunda");

        List<String> tooMany = Collections.nCopies(MessageDraft.MAX_CLAIMS + 1, "afirmacao");
        assertThat(MessageDraft.of(OutreachChannel.EMAIL, null, "Corpo", tooMany).draft()).isEmpty();
        assertThat(MessageDraft.of(OutreachChannel.EMAIL, null, "Corpo",
                List.of("x".repeat(MessageDraft.MAX_CLAIM_LENGTH + 1))).draft()).isEmpty();
    }
}
