package io.matheusms.lastro;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LastroApplicationIT {

    @Autowired
    private Flyway flyway;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Test
    void shouldApplyAllMigrationsOnStartup() {
        var info = flyway.info();

        assertThat(info.pending()).isEmpty();
        assertThat(info.applied()).isNotEmpty();
    }

    @Test
    void shouldReportHealthUpWhenDatabaseIsReachable() {
        assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
    }
}
