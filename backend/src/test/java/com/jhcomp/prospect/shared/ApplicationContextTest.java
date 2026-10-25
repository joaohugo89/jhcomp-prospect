package com.jhcomp.prospect.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

@DisplayName("Contexto da aplicacao")
class ApplicationContextTest extends PostgresIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("sobe com PostgreSQL real e aplica as migrations")
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanNamesForType(javax.sql.DataSource.class)).isNotEmpty();
    }
}
