package com.jhcomp.prospect.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManagerFactory;

/**
 * Hibernate properties arrive under the exact name (RNF-009).
 *
 * <p>It protects how the keys are written in the YAML: a name changed in the
 * binding would be ignored by Hibernate silently, and the time zone would
 * keep looking right because the JVM already runs in UTC.
 */
@DisplayName("Hibernate configuration")
class HibernatePropertiesTest extends PostgresIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("the JDBC UTC time zone and the refusal to paginate over a collection reach Hibernate")
    void propertiesKeepTheirNames() {
        var properties = entityManagerFactory.getProperties();

        assertThat(properties.get("hibernate.jdbc.time_zone")).isEqualTo("UTC");
        assertThat(properties.get("hibernate.query.fail_on_pagination_over_collection_fetch")).isEqualTo("true");
    }

    @Test
    @DisplayName("statement batching reaches Hibernate, with no reordering")
    void batchingPropertiesKeepTheirNames() {
        var properties = entityManagerFactory.getProperties();

        // A wrong name here breaks nothing: Hibernate simply stops batching,
        // and the import goes back to one round trip per row with no warning
        // at all.
        assertThat(properties.get("hibernate.jdbc.batch_size")).isEqualTo("50");

        // order_inserts groups by entity type and can write the child row
        // before the parent: the import confirm started answering 409 through
        // a foreign key violation. Leaving it out is deliberate.
        assertThat(properties).doesNotContainKey("hibernate.order_inserts");
    }
}
