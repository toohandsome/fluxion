package io.github.fluxion.test.harness.adapter.engine;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.fluxion.test.harness.HarnessJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class EngineFixtureMapper {

    public EngineFixtureScenario load(Path repoRoot, String caseId) throws IOException {
        Path fixturesDir = repoRoot.resolve("fixtures").resolve("engine");
        List<Path> matches = new ArrayList<>();
        try (var stream = Files.walk(fixturesDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".scenario.json"))
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
            throw new IllegalArgumentException("No engine fixture found for caseId=" + caseId);
        }
        Path fixturePath = matches.get(0);
        JsonNode tree = HarnessJson.MAPPER.readTree(fixturePath.toFile());
        return new EngineFixtureScenario(
                tree.path("caseId").asText(),
                tree.path("suite").asText("engine-scenarios"),
                tree.path("description").asText(""),
                tree.path("model"),
                tree.path("trigger"),
                HarnessJson.MAPPER.convertValue(tree.path("resources"), EngineFixtureResources.class),
                HarnessJson.MAPPER.convertValue(tree.path("expect"), EngineFixtureExpectation.class),
                HarnessJson.MAPPER.convertValue(tree.path("docRefs"), HarnessJson.MAPPER.getTypeFactory().constructCollectionType(List.class, String.class)),
                repoRoot.relativize(fixturePath)
        );
    }

    public RealEngineExecutionRequest toExecutionRequest(EngineFixtureScenario scenario) {
        return new RealEngineExecutionRequest(
                scenario.caseId(),
                scenario.model(),
                scenario.trigger(),
                scenario.resources(),
                scenario.expect(),
                scenario.fixturePath()
        );
    }
}
