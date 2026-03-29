package io.github.fluxion.test.harness.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fluxion.test.harness.HarnessCommandRunner;
import io.github.fluxion.test.harness.adapter.HarnessAdapterRequest;
import io.github.fluxion.test.harness.adapter.HarnessAdapterResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RealPersistenceSuiteAdapterTest {

    @Test
    void shouldExecuteAgainstInjectedPersistencePort() throws Exception {
        HarnessCommandRunner runner = new HarnessCommandRunner(discoverRepoRoot());
        RealPersistenceSuiteAdapter adapter = new RealPersistenceSuiteAdapter(
                request -> new RealPersistenceExecutionResult(
                        "SUCCESS",
                        Map.of("node_log_1", "SUCCESS"),
                        Map.of(),
                        Map.of("node_log_1", 1),
                        Map.of("node_log_1", List.of(Map.of("attempt_no", 1, "status", "SUCCESS")))
                ),
                new PersistenceFixtureMapper(),
                new RealPersistenceResultComparator()
        );

        HarnessAdapterResponse response = adapter.execute(
                HarnessAdapterRequest.of("persistence-real", "persistence/instance-and-node-records"),
                runner
        );

        assertThat(response.success()).isTrue();
        assertThat(response.observedCases()).containsExactly("persistence/instance-and-node-records");
    }

    @Test
    void shouldExecuteAgainstJavaModuleBackedPort() throws Exception {
        HarnessCommandRunner runner = new HarnessCommandRunner(discoverRepoRoot());
        RealPersistenceSuiteAdapter adapter = new RealPersistenceSuiteAdapter(
                new FluxionPersistenceBackedExecutionPort(),
                new PersistenceFixtureMapper(),
                new RealPersistenceResultComparator()
        );

        HarnessAdapterResponse response = adapter.execute(
                HarnessAdapterRequest.of("persistence-real", "persistence/retry-attempt-snapshots"),
                runner
        );

        assertThat(response.success())
                .withFailMessage(response.output())
                .isTrue();
    }

    private static Path discoverRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (current.resolve("pom.xml").toFile().exists()
                    && current.resolve("README.md").toFile().exists()
                    && current.resolve("docs").toFile().exists()) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate Fluxion repository root.");
    }
}
