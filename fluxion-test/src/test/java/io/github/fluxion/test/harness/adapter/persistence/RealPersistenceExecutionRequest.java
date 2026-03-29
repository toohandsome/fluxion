package io.github.fluxion.test.harness.adapter.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;

public record RealPersistenceExecutionRequest(
        String caseId,
        JsonNode model,
        JsonNode trigger,
        PersistenceFixtureResources resources,
        PersistenceFixtureExpectation expect,
        Path fixturePath
) {
}
