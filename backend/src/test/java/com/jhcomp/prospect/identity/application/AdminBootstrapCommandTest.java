package com.jhcomp.prospect.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

/** The first administrator in production: single-use and visibly failing (D19, D27). */
@DisplayName("Production bootstrap command")
class AdminBootstrapCommandTest {

    private static final String STRONG_PASSWORD = "senha-forte-ficticia";

    private final InitialSetupService setup = mock(InitialSetupService.class);

    private void run(String email, String password) {
        new AdminBootstrapCommand(setup, email, password, "JH.COMP").run(new DefaultApplicationArguments());
    }

    @Test
    @DisplayName("creates the organisation and the administrator with a normalised e-mail")
    void createsWhenEmpty() {
        run(" Admin@Example.Test ", STRONG_PASSWORD);

        verify(setup).create("JH.COMP", "jh-comp", "admin@example.test", STRONG_PASSWORD);
    }

    @Test
    @DisplayName("refuses to run again once an organisation exists")
    void refusesWhenAlreadyInitialized() {
        when(setup.hasOrganization()).thenReturn(true);

        assertThatThrownBy(() -> run("admin@example.test", STRONG_PASSWORD)).hasMessageContaining("only once");
        verify(setup, never()).create(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("refuses a short password and a missing e-mail, creating nothing")
    void refusesWeakInput() {
        assertThatThrownBy(() -> run("admin@example.test", "curta")).hasMessageContaining("at least 12");
        assertThatThrownBy(() -> run("", STRONG_PASSWORD)).hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL");
        verify(setup, never()).create(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a slug without accents, lower case, with letters, digits and hyphens only")
    void slug() {
        assertThat(AdminBootstrapCommand.slugOf("Padaria São José")).isEqualTo("padaria-sao-jose");
        assertThat(AdminBootstrapCommand.slugOf("  JH.COMP  ")).isEqualTo("jh-comp");
        assertThat(AdminBootstrapCommand.slugOf("***")).isEqualTo("org");
    }
}
