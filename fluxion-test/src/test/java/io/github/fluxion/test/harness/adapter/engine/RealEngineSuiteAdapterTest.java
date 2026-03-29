package io.github.fluxion.test.harness.adapter.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fluxion.test.harness.HarnessCommandRunner;
import io.github.fluxion.test.harness.adapter.HarnessAdapterRequest;
import io.github.fluxion.test.harness.adapter.HarnessAdapterResponse;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RealEngineSuiteAdapterTest {

    @Test
    void shouldExecuteAgainstInjectedExecutionPort() throws Exception {
        HarnessCommandRunner runner = new HarnessCommandRunner(discoverRepoRoot());
        RealEngineSuiteAdapter adapter = new RealEngineSuiteAdapter(
                request -> new RealEngineExecutionResult(
                        "SUCCESS",
                        null,
                        Map.of("branch", "false"),
                        Map.of(
                                "node_condition_1", "SUCCESS",
                                "node_log_true", "SKIPPED",
                                "node_log_false", "SUCCESS"
                        ),
                        Map.of("node_log_true", "BRANCH_NOT_MATCHED"),
                        Map.of(),
                        Map.of(
                                "node_condition_1", 1,
                                "node_log_true", 0,
                                "node_log_false", 1
                        ),
                        Map.of(
                                "node_condition_1", java.util.List.of(Map.of("attempt", 1, "status", "SUCCESS")),
                                "node_log_true", java.util.List.of(),
                                "node_log_false", java.util.List.of(Map.of("attempt", 1, "status", "SUCCESS"))
                        ),
                        java.util.List.of()
                ),
                new EngineFixtureMapper(),
                new RealEngineResultComparator()
        );

        HarnessAdapterResponse response = adapter.execute(
                HarnessAdapterRequest.of("engine-real", "engine/branch-skipped"),
                runner
        );

        assertThat(response.success()).isTrue();
        assertThat(response.observedCases()).containsExactly("engine/branch-skipped");
        assertThat(response.resultsPath()).contains("fixtures/engine/branch-skipped.scenario.json");
    }

    @Test
    void shouldExecuteAgainstFluxionEngineBackedPort() throws Exception {
        HarnessCommandRunner runner = new HarnessCommandRunner(discoverRepoRoot());
        RealEngineSuiteAdapter adapter = new RealEngineSuiteAdapter(
                new FluxionEngineBackedExecutionPort(),
                new EngineFixtureMapper(),
                new RealEngineResultComparator()
        );

        HarnessAdapterResponse response = adapter.execute(
                HarnessAdapterRequest.of("engine-real", "engine/db-seed-update-or-in-order-limit"),
                runner
        );

        assertThat(response.success())
                .withFailMessage(response.output())
                .isTrue();
        assertThat(response.observedCases()).containsExactly("engine/db-seed-update-or-in-order-limit");
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
