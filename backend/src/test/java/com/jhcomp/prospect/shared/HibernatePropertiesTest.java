package com.jhcomp.prospect.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManagerFactory;

/**
 * Propriedades do Hibernate chegam com o nome exato (RNF-009).
 *
 * <p>Protege a escrita das chaves no YAML: um nome alterado na ligacao seria
 * ignorado pelo Hibernate em silencio, e o fuso continuaria parecendo certo
 * porque a JVM ja roda em UTC.
 */
@DisplayName("Configuracao do Hibernate")
class HibernatePropertiesTest extends PostgresIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("fuso UTC do JDBC e recusa de paginacao sobre colecao chegam ao Hibernate")
    void propertiesKeepTheirNames() {
        var properties = entityManagerFactory.getProperties();

        assertThat(properties.get("hibernate.jdbc.time_zone")).isEqualTo("UTC");
        assertThat(properties.get("hibernate.query.fail_on_pagination_over_collection_fetch")).isEqualTo("true");
    }

    @Test
    @DisplayName("agrupamento de instrucoes chega ao Hibernate, sem reordenacao")
    void batchingPropertiesKeepTheirNames() {
        var properties = entityManagerFactory.getProperties();

        // Um nome errado aqui nao quebra nada: o Hibernate so deixa de agrupar,
        // e a importacao volta a uma ida ao banco por linha sem aviso nenhum.
        assertThat(properties.get("hibernate.jdbc.batch_size")).isEqualTo("50");

        // order_inserts agrupa por tipo de entidade e pode gravar a linha
        // filha antes da pai: a confirmacao da importacao passou a responder
        // 409 por violacao de chave estrangeira. Ficar de fora e proposital.
        assertThat(properties).doesNotContainKey("hibernate.order_inserts");
    }
}
