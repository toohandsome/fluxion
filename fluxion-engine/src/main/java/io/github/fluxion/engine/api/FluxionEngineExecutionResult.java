package io.github.fluxion.engine.api;

import java.util.List;
import java.util.Map;

public record FluxionEngineExecutionResult(
        String instanceStatus,
        String errorCode,
        Map<String, Object> flowOutput,
        Map<String, FluxionNodeExecutionResult> nodeResults,
        List<String> missingNodes
) {
}
