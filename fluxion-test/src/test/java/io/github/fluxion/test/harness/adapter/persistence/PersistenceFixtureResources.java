package io.github.fluxion.test.harness.adapter.persistence;

import java.util.Map;

public record PersistenceFixtureResources(
        Map<String, Map<String, Object>> http,
        Map<String, String> dbSchemas,
        Map<String, String> dbSeeds,
        Map<String, Map<String, Object>> resourcePermits
) {
}
