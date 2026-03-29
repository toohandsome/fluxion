package io.github.fluxion.test.harness.adapter.persistence;

import java.util.List;
import java.util.Map;

public record RealPersistenceExecutionResult(
        String instanceStatus,
        Map<String, String> nodeStatuses,
        Map<String, String> skipReasons,
        Map<String, Integer> nodeAttemptCounts,
        Map<String, List<Map<String, Object>>> attempts
) {
}
