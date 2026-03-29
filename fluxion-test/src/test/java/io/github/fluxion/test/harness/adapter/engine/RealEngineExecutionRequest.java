package io.github.fluxion.test.harness.adapter.engine;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;

public record RealEngineExecutionRequest(
        String caseId,
        JsonNode model,
        JsonNode trigger,
        EngineFixtureResources resources,
        EngineFixtureExpectation expectation,
        Path fixturePath
) {
}
