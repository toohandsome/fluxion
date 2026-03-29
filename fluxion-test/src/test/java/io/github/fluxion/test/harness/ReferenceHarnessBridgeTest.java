package io.github.fluxion.test.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReferenceHarnessBridgeTest {

    private static HarnessCommandRunner runner;

    @BeforeAll
    static void setUp() {
        String repoRootProperty = System.getProperty("fluxion.repo.root");
        Path repoRoot = repoRootProperty != null && !repoRootProperty.isBlank()
                ? Path.of(repoRootProperty)
                : discoverRepoRoot();
        runner = new HarnessCommandRunner(repoRoot);
    }

    @Test
    @Order(1)
    void shouldBuildCatalogsAndPassDoctor() throws Exception {
        assertSuccessful(runner.runPython(List.of("tools/contracts/build_catalogs.py")));
        assertSuccessful(runner.runPython(List.of("tools/harness/doctor.py")));
    }

    @Test
    @Order(2)
    void shouldBridgeContractsSuiteThroughMaven() throws Exception {
        assertSuccessful(runner.runPython(List.of("tools/harness/run_contracts.py")));
        assertSuiteStats("contracts", ".artifacts/harness/contracts/results.json", 5, 0);
    }

    @Test
    @Order(3)
    void shouldBridgeEngineSuiteThroughMaven() throws Exception {
        assertSuccessful(runner.runPython(List.of("tools/harness/run_engine_cases.py")));
        assertSuiteStats("engine", ".artifacts/harness/engine/results.json", 10, 0);
    }

    @Test
    @Order(4)
    void shouldBridgeRuntimeApiSuiteThroughMaven() throws Exception {
        assertSuccessful(runner.runPython(List.of("tools/harness/run_runtime_api_cases.py")));
        assertSuiteStats("runtime-api", ".artifacts/harness/runtime-api/results.json", 3, 0);
    }

    @Test
    @Order(5)
    void shouldBridgeSchedulerSuiteThroughMaven() throws Exception {
        assertSuccessful(runner.runPython(List.of("tools/harness/run_scheduler_cases.py")));
        assertSuiteStats("scheduler", ".artifacts/harness/scheduler/results.json", 3, 0);
    }

    @Test
    @Order(6)
    void shouldBridgePersistenceSuiteThroughMaven() throws Exception {
        assertSuccessful(runner.runPython(List.of("tools/harness/run_persistence_cases.py")));
        assertSuiteStats("persistence", ".artifacts/harness/persistence/results.json", 3, 0);
    }

    @Test
    @Order(7)
    void shouldSupportSelectiveTestRouting() throws Exception {
        HarnessCommandRunner.CommandResult result = runner.runPython(List.of(
                "tools/harness/selective_tests.py",
                "--changed-file",
                "docs/phase-1/runtime-semantics.md"
        ));
        assertSuccessful(result);
        List<String> selectedSuites = HarnessJson.readSelectedSuites(result);
        assertThat(selectedSuites)
                .containsExactlyInAnyOrder("engine", "persistence", "runtime-api", "scheduler");
    }

    @Test
    @Order(8)
    void shouldAggregateHarnessArtifactsForJavaSideConsumption() throws Exception {
        assertSuccessful(runner.runPython(List.of("tools/harness/collect_results.py")));
        JsonNode summary = HarnessJson.readTree(runner, ".artifacts/harness/diagnostics.json");
        assertThat(summary.get("diagnostics")).hasSize(5);
        String markdown = runner.readText(Path.of(".artifacts/harness/summary.md"));
        assertThat(markdown).contains("## Diagnostics by ownerModule");
    }

    private void assertSuiteStats(String suiteName, String resultsPath, int expectedTotal, int expectedFailed)
            throws IOException {
        JsonNode results = HarnessJson.readTree(runner, resultsPath);
        assertThat(results.path("stats").path("total").asInt()).isEqualTo(expectedTotal);
        assertThat(results.path("stats").path("failed").asInt()).isEqualTo(expectedFailed);
        assertThat(results.path("tool").asText()).isNotBlank();
    }

    private void assertSuccessful(HarnessCommandRunner.CommandResult result) {
        assertThat(result.isSuccess())
                .withFailMessage("Command failed (%s)%n%s", String.join(" ", result.command()), result.output())
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
        throw new IllegalStateException("Cannot locate Fluxion repository root from current working directory.");
    }
}
