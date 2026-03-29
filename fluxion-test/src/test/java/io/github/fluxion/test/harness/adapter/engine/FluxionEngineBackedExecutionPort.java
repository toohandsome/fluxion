package io.github.fluxion.test.harness.adapter.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.fluxion.engine.api.FluxionNodeAttemptDetail;
import io.github.fluxion.engine.api.FluxionEngineExecutionEntry;
import io.github.fluxion.engine.api.FluxionEngineExecutionRequest;
import io.github.fluxion.engine.api.FluxionEngineExecutionResult;
import io.github.fluxion.engine.api.FluxionNodeExecutionResult;
import io.github.fluxion.engine.reference.InMemoryFluxionEngineExecutionEntry;
import io.github.fluxion.test.harness.HarnessJson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FluxionEngineBackedExecutionPort implements RealEngineExecutionPort {

    private final FluxionEngineExecutionEntry executionEntry;

    public FluxionEngineBackedExecutionPort() {
        this(new InMemoryFluxionEngineExecutionEntry());
    }

    public FluxionEngineBackedExecutionPort(FluxionEngineExecutionEntry executionEntry) {
        this.executionEntry = executionEntry;
    }

    @Override
    public RealEngineExecutionResult execute(RealEngineExecutionRequest request) {
        Map<String, Object> model = HarnessJson.MAPPER.convertValue(request.model(), new TypeReference<>() {
        });
        Map<String, Object> trigger = request.trigger() == null || request.trigger().isMissingNode()
                ? Map.of()
                : HarnessJson.MAPPER.convertValue(request.trigger(), new TypeReference<>() {
                });
        Map<String, Object> resources = toResourceMap(request.resources());
        FluxionEngineExecutionResult result = executionEntry.execute(
                new FluxionEngineExecutionRequest(request.caseId(), model, trigger, resources)
        );
        return new RealEngineExecutionResult(
                result.instanceStatus(),
                result.errorCode(),
                result.flowOutput(),
                toNodeStatuses(result.nodeResults()),
                toSkipReasons(result.nodeResults()),
                toNodeErrorCodes(result.nodeResults()),
                toAttemptCounts(result.nodeResults()),
                toAttemptDetails(result.nodeResults()),
                result.missingNodes()
        );
    }

    private Map<String, Object> toResourceMap(EngineFixtureResources resources) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("http", resources == null || resources.http() == null ? Map.of() : resources.http());
        result.put("dbSchemas", resources == null || resources.dbSchemas() == null ? Map.of() : resources.dbSchemas());
        result.put("dbSeeds", resources == null || resources.dbSeeds() == null ? Map.of() : resources.dbSeeds());
        result.put(
                "resourcePermits",
                resources == null || resources.resourcePermits() == null ? Map.of() : resources.resourcePermits()
        );
        return result;
    }

    private Map<String, String> toNodeStatuses(Map<String, FluxionNodeExecutionResult> nodeResults) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, FluxionNodeExecutionResult> entry : nodeResults.entrySet()) {
            result.put(entry.getKey(), entry.getValue().status());
        }
        return result;
    }

    private Map<String, String> toSkipReasons(Map<String, FluxionNodeExecutionResult> nodeResults) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, FluxionNodeExecutionResult> entry : nodeResults.entrySet()) {
            if (entry.getValue().skipReason() != null) {
                result.put(entry.getKey(), entry.getValue().skipReason());
            }
        }
        return result;
    }

    private Map<String, String> toNodeErrorCodes(Map<String, FluxionNodeExecutionResult> nodeResults) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, FluxionNodeExecutionResult> entry : nodeResults.entrySet()) {
            if (entry.getValue().errorCode() != null) {
                result.put(entry.getKey(), entry.getValue().errorCode());
            }
        }
        return result;
    }

    private Map<String, Integer> toAttemptCounts(Map<String, FluxionNodeExecutionResult> nodeResults) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, FluxionNodeExecutionResult> entry : nodeResults.entrySet()) {
            result.put(entry.getKey(), entry.getValue().attemptCount());
        }
        return result;
    }

    private Map<String, List<Map<String, Object>>> toAttemptDetails(Map<String, FluxionNodeExecutionResult> nodeResults) {
        LinkedHashMap<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map.Entry<String, FluxionNodeExecutionResult> entry : nodeResults.entrySet()) {
            List<Map<String, Object>> details = new java.util.ArrayList<>();
            for (FluxionNodeAttemptDetail detail : entry.getValue().attemptDetails()) {
                LinkedHashMap<String, Object> item = new LinkedHashMap<>();
                item.put("attempt", detail.attempt());
                item.put("status", detail.status());
                item.put("errorCode", detail.errorCode());
                item.put("errorMessage", detail.errorMessage());
                item.put("durationMs", detail.durationMs());
                details.add(item);
            }
            result.put(entry.getKey(), details);
        }
        return result;
    }
}
