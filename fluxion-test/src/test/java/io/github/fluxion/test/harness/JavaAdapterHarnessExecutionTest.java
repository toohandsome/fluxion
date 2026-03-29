package io.github.fluxion.test.harness;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fluxion.test.harness.adapter.HarnessAdapterRegistry;
import io.github.fluxion.test.harness.adapter.HarnessAdapterRequest;
import io.github.fluxion.test.harness.adapter.HarnessAdapterResponse;
import io.github.fluxion.test.harness.adapter.HarnessSuiteAdapter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JavaAdapterHarnessExecutionTest {

    @Test
    void shouldExecuteSelectedSuiteThroughJavaAdapterRegistry() throws Exception {
        HarnessCommandRunner runner = new HarnessCommandRunner(discoverRepoRoot());
        HarnessAdapterRegistry registry = new HarnessAdapterRegistry();
        String targetSuite = System.getProperty("fluxion.adapter.suite", "engine");
        String targetCase = System.getProperty("fluxion.adapter.case");
        HarnessSuiteAdapter adapter = registry.find(targetSuite)
                .orElseThrow(() -> new IllegalStateException("No adapter registered for suite=" + targetSuite));

        HarnessAdapterResponse response = adapter.execute(HarnessAdapterRequest.of(targetSuite, targetCase), runner);
        assertThat(response.success())
                .withFailMessage("Adapter command failed: %s%n%s", response.command(), response.output())
                .isTrue();
        if (targetCase != null && !targetCase.isBlank()) {
            assertThat(response.observedCases()).contains(targetCase);
        }
        writeAdapterArtifact(runner.repoRoot(), response);
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

    private static void writeAdapterArtifact(Path repoRoot, HarnessAdapterResponse response) throws Exception {
        Path outputDir = repoRoot.resolve(".artifacts/harness/java-adapter");
        Files.createDirectories(outputDir);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("suite", response.suite());
        payload.put("caseId", response.caseId());
        payload.put("success", response.success());
        payload.put("command", response.command());
        payload.put("resultsPath", response.resultsPath());
        payload.put("observedCases", response.observedCases());
        payload.put("output", response.output());
        String json = HarnessJson.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        Files.writeString(outputDir.resolve(response.suite() + ".json"), json + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
