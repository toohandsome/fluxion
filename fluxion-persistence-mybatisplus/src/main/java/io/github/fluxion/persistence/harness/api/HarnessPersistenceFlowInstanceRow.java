package io.github.fluxion.persistence.harness.api;

public record HarnessPersistenceFlowInstanceRow(
        long instanceId,
        String flowCode,
        String status,
        String errorCode,
        String errorMessage,
        String flowOutputJson,
        long durationMs
) {
}
