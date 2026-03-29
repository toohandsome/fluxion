package io.github.fluxion.test.harness.adapter.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EngineFixtureMapperTest {

    @Test
    void shouldLoadEngineFixtureIntoJavaScenarioModel() throws Exception {
        EngineFixtureMapper mapper = new EngineFixtureMapper();
        Path repoRoot = discoverRepoRoot();

        EngineFixtureScenario scenario = mapper.load(repoRoot, "engine/branch-skipped");

        assertThat(scenario.caseId()).isEqualTo("engine/branch-skipped");
        assertThat(scenario.suite()).isEqualTo("engine-scenarios");
        assertThat(scenario.model().path("flowCode").asText()).isEqualTo("engine_branch_skipped");
        assertThat(scenario.expect().instanceStatus()).isEqualTo("SUCCESS");
        assertThat(scenario.expect().nodeStatuses()).containsEntry("node_log_true", "SKIPPED");
        assertThat(scenario.fixturePath().toString().replace('\\', '/'))
                .isEqualTo("fixtures/engine/branch-skipped.scenario.json");
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
