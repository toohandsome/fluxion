package io.github.fluxion.test.harness.adapter.engine;

import java.util.List;
import java.util.Map;

public record EngineFixtureExpectation(
        String instanceStatus,
        String errorCode,
        Map<String, Object> flowOutput,
        Map<String, String> nodeStatuses,
        Map<String, String> skipReasons,
        Map<String, String> nodeErrorCodes,
        Map<String, Integer> attemptCounts,
        Map<String, List<Map<String, Object>>> attemptDetails,
        List<String> missingNodes
) {
}
