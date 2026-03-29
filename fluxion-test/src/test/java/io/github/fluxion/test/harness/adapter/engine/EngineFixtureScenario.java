package io.github.fluxion.test.harness.adapter.engine;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.List;

public record EngineFixtureScenario(
        String caseId,
        String suite,
        String description,
        JsonNode model,
        JsonNode trigger,
        EngineFixtureResources resources,
        EngineFixtureExpectation expect,
        List<String> docRefs,
        Path fixturePath
) {
}
