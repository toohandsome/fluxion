package io.github.fluxion.engine.api;

import java.util.List;
import java.util.Map;

public record FluxionNodeExecutionResult(
        String nodeId,
        String nodeType,
        String status,
        Map<String, Object> output,
        String errorCode,
        String errorMessage,
        String skipReason,
        Boolean branchResult,
        int attemptCount,
        int durationMs,
        List<FluxionNodeAttemptDetail> attemptDetails
) {
}
