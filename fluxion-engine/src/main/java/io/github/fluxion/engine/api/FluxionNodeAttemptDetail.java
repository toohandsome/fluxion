package io.github.fluxion.engine.api;

public record FluxionNodeAttemptDetail(
        int attempt,
        String status,
        String errorCode,
        String errorMessage,
        int durationMs
) {
}
