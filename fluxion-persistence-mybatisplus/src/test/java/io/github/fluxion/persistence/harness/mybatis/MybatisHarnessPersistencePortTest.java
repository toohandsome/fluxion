package io.github.fluxion.persistence.harness.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fluxion.persistence.harness.api.HarnessPersistencePort;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceFlowInstanceRow;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceNodeAttemptRow;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceNodeExecutionRow;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceSnapshot;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceWriteRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class MybatisHarnessPersistencePortTest {

    @Test
    void shouldPersistAndReadSnapshotThroughMybatisMappers() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=jdbc:h2:mem:fluxion_persistence_module_test_" + UUID.randomUUID()
                                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "fluxion.harness.persistence.enabled=true",
                        "fluxion.harness.persistence.initialize-schema=true"
                )
                .run()) {
            HarnessPersistencePort port = context.getBean(HarnessPersistencePort.class);

            HarnessPersistenceSnapshot snapshot = port.persist(new HarnessPersistenceWriteRequest(
                    new HarnessPersistenceFlowInstanceRow(
                            1001L,
                            "demo_flow",
                            "SUCCESS",
                            null,
                            null,
                            "{\"stage\":\"done\"}",
                            210L
                    ),
                    List.of(
                            new HarnessPersistenceNodeExecutionRow(
                                    1001L,
                                    "node_http",
                                    "http",
                                    "SUCCESS",
                                    210L,
                                    2,
                                    null,
                                    null,
                                    null,
                                    "{\"stage\":\"done\"}"
                            )
                    ),
                    List.of(
                            new HarnessPersistenceNodeAttemptRow(
                                    1001L,
                                    "node_http",
                                    1,
                                    "FAILED",
                                    180L,
                                    "NODE_TIMEOUT",
                                    "node exceeded timeoutMs=100"
                            ),
                            new HarnessPersistenceNodeAttemptRow(
                                    1001L,
                                    "node_http",
                                    2,
                                    "SUCCESS",
                                    30L,
                                    null,
                                    null
                            )
                    )
            ));

            assertThat(snapshot.flowInstance().flowCode()).isEqualTo("demo_flow");
            assertThat(snapshot.nodeExecutions()).hasSize(1);
            assertThat(snapshot.nodeExecutions().get(0).attemptCount()).isEqualTo(2);
            assertThat(snapshot.nodeAttempts())
                    .extracting(HarnessPersistenceNodeAttemptRow::attemptNo)
                    .containsExactly(1, 2);
            assertThat(snapshot.nodeAttempts().get(0).errorCode()).isEqualTo("NODE_TIMEOUT");
        }
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
