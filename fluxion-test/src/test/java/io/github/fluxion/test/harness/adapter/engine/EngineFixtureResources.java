package io.github.fluxion.test.harness.adapter.engine;

import java.util.Map;

public record EngineFixtureResources(
        Map<String, Map<String, Object>> http,
        Map<String, String> dbSchemas,
        Map<String, String> dbSeeds,
        Map<String, Map<String, Object>> resourcePermits
) {
}
