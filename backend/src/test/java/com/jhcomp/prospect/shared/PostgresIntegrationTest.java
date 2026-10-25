package com.jhcomp.prospect.shared;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base dos testes que precisam de PostgreSQL real (doc 10 §2).
 *
 * <p>Usa o padrao de container singleton: o container e iniciado uma vez por
 * JVM em bloco estatico e nunca e parado explicitamente. Nao usar
 * {@code @Testcontainers} com {@code @Container} aqui — essa extensao para o
 * container ao fim de cada classe de teste, enquanto o Spring reaproveita o
 * contexto em cache apontando para o container ja encerrado, o que faz as
 * classes seguintes falharem com "Failed to obtain JDBC Connection".
 *
 * <p>O encerramento fica a cargo do Ryuk, container acessorio que o
 * Testcontainers sobe para remover recursos ao fim da execucao.
 *
 * <p>A classe vem de {@code org.testcontainers.postgresql}, e nao do pacote
 * {@code org.testcontainers.containers}, que foi depreciado no Testcontainers
 * 2.x. A nova classe nao e generica, por isso nao leva {@code <?>}.
 *
 * <p>A imagem acompanha a do {@code docker-compose.yml}: testar contra uma
 * versao diferente da de desenvolvimento esconde problema de migration.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES;

    static {
        // O container vive por toda a JVM de teste de proposito, portanto nao e
        // fechado aqui. Fecha-lo invalidaria o contexto Spring em cache.
        @SuppressWarnings("resource")
        PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
                .withDatabaseName("jhcomp_prospect_test")
                .withUsername("jhcomp_test")
                .withPassword("jhcomp_test");

        container
                // RNF-009: o banco de teste opera em UTC, como o de producao.
                .withEnv("TZ", "UTC")
                .withEnv("PGTZ", "UTC")
                // Mesma collation do docker-compose, da qual a deduplicacao depende.
                .withEnv("POSTGRES_INITDB_ARGS", "--encoding=UTF8 --locale=C");

        POSTGRES = container;
        POSTGRES.start();
    }
}
