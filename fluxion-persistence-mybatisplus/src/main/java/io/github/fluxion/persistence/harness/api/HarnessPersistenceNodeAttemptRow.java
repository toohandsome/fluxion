package io.github.fluxion.persistence.harness.api;

public record HarnessPersistenceNodeAttemptRow(
        long instanceId,
        String nodeId,
        int attemptNo,
        String status,
        long durationMs,
        String errorCode,
        String errorMessage
) {
}
