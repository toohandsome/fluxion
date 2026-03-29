package io.github.fluxion.persistence.harness.api;

public record HarnessPersistenceNodeExecutionRow(
        long instanceId,
        String nodeId,
        String nodeType,
        String status,
        long durationMs,
        int attemptCount,
        String skipReason,
        String errorCode,
        String errorMessage,
        String outputJson
) {
}
