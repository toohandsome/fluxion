package io.github.fluxion.engine.api;

import java.util.Map;

public record FluxionEngineExecutionRequest(
        String caseId,
        Map<String, Object> model,
        Map<String, Object> trigger,
        Map<String, Object> resources
) {
}
