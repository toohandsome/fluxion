package io.github.fluxion.test.harness.adapter.persistence;

import java.util.List;
import java.util.Map;

public record PersistenceFixtureExpectation(
        String instanceStatus,
        Map<String, String> nodeStatuses,
        Map<String, String> skipReason,
        Map<String, Integer> nodeAttemptCounts,
        Map<String, List<Map<String, Object>>> attempts
) {
}
