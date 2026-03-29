package io.github.fluxion.test.harness.adapter.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.fluxion.test.harness.HarnessJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PersistenceFixtureMapper {

    public PersistenceFixtureScenario load(Path repoRoot, String caseId) throws IOException {
        Path fixturesDir = repoRoot.resolve("fixtures").resolve("persistence");
        List<Path> matches = new ArrayList<>();
        try (var stream = Files.walk(fixturesDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".case.json"))
                    .forEach(path -> {
                        try {
                            JsonNode tree = HarnessJson.MAPPER.readTree(path.toFile());
                            if (caseId.equals(tree.path("caseId").asText())) {
                                matches.add(path);
                            }
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No persistence fixture found for caseId=" + caseId);
        }
        Path fixturePath = matches.get(0);
        JsonNode tree = HarnessJson.MAPPER.readTree(fixturePath.toFile());
        return new PersistenceFixtureScenario(
                tree.path("caseId").asText(),
                tree.path("suite").asText("persistence-integration"),
                tree.path("description").asText(""),
                tree.path("model"),
                tree.path("trigger"),
                HarnessJson.MAPPER.convertValue(tree.path("resources"), PersistenceFixtureResources.class),
                HarnessJson.MAPPER.convertValue(tree.path("expect"), PersistenceFixtureExpectation.class),
                repoRoot.relativize(fixturePath)
        );
    }

    public RealPersistenceExecutionRequest toExecutionRequest(PersistenceFixtureScenario scenario) {
        return new RealPersistenceExecutionRequest(
                scenario.caseId(),
                scenario.model(),
                scenario.trigger(),
                scenario.resources(),
                scenario.expect(),
                scenario.fixturePath()
        );
    }
}
