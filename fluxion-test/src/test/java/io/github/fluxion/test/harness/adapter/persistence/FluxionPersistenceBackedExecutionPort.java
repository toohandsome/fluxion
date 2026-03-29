package io.github.fluxion.test.harness.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.fluxion.engine.api.FluxionEngineExecutionEntry;
import io.github.fluxion.engine.api.FluxionEngineExecutionRequest;
import io.github.fluxion.engine.api.FluxionEngineExecutionResult;
import io.github.fluxion.engine.api.FluxionNodeAttemptDetail;
import io.github.fluxion.engine.api.FluxionNodeExecutionResult;
import io.github.fluxion.engine.reference.InMemoryFluxionEngineExecutionEntry;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceFlowInstanceRow;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceNodeAttemptRow;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceNodeExecutionRow;
import io.github.fluxion.persistence.harness.api.HarnessPersistencePort;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceSnapshot;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceWriteRequest;
import io.github.fluxion.test.harness.HarnessJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FluxionPersistenceBackedExecutionPort implements RealPersistenceExecutionPort {

    private final FluxionEngineExecutionEntry engineExecutionEntry;
    private final HarnessPersistencePort persistencePort;
    private final SpringHarnessPersistencePortProvider springProvider;

    public FluxionPersistenceBackedExecutionPort() {
        this(new InMemoryFluxionEngineExecutionEntry(), new SpringHarnessPersistencePortProvider());
    }

    public FluxionPersistenceBackedExecutionPort(
            FluxionEngineExecutionEntry engineExecutionEntry,
            HarnessPersistencePort persistencePort
    ) {
        this.engineExecutionEntry = engineExecutionEntry;
        this.persistencePort = persistencePort;
        this.springProvider = null;
    }

    private FluxionPersistenceBackedExecutionPort(
            FluxionEngineExecutionEntry engineExecutionEntry,
            SpringHarnessPersistencePortProvider springProvider
    ) {
        this.engineExecutionEntry = engineExecutionEntry;
        this.springProvider = springProvider;
        this.persistencePort = springProvider.getObject();
    }

    @Override
    public RealPersistenceExecutionResult execute(RealPersistenceExecutionRequest request) {
        Map<String, Object> model = HarnessJson.MAPPER.convertValue(request.model(), new TypeReference<>() {
        });
        Map<String, Object> trigger = request.trigger() == null || request.trigger().isMissingNode()
                ? Map.of()
                : HarnessJson.MAPPER.convertValue(request.trigger(), new TypeReference<>() {
                });
        FluxionEngineExecutionResult engineResult = engineExecutionEntry.execute(
                new FluxionEngineExecutionRequest(request.caseId(), model, trigger, toResourceMap(request.resources()))
        );
        HarnessPersistenceSnapshot snapshot = persistencePort.persist(toWriteRequest(model, engineResult));
        return new RealPersistenceExecutionResult(
                snapshot.flowInstance().status(),
                toNodeStatuses(snapshot.nodeExecutions()),
                toSkipReasons(snapshot.nodeExecutions()),
                toAttemptCounts(snapshot.nodeExecutions()),
                toAttempts(snapshot.nodeExecutions(), snapshot.nodeAttempts())
        );
    }

    private Map<String, Object> toResourceMap(PersistenceFixtureResources resources) {
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

    @SuppressWarnings("unchecked")
    private HarnessPersistenceWriteRequest toWriteRequest(Map<String, Object> model, FluxionEngineExecutionResult engineResult) {
        Map<String, String> nodeTypes = new LinkedHashMap<>();
        for (Map<String, Object> node : (List<Map<String, Object>>) model.getOrDefault("nodes", List.of())) {
            nodeTypes.put(String.valueOf(node.get("nodeId")), String.valueOf(node.get("nodeType")));
        }
        long instanceId = 1L;
        long durationMs = engineResult.nodeResults().values().stream().mapToLong(FluxionNodeExecutionResult::durationMs).sum();
        String instanceErrorMessage = engineResult.nodeResults().values().stream()
                .filter(node -> "FAILED".equals(node.status()) && node.errorMessage() != null)
                .map(FluxionNodeExecutionResult::errorMessage)
                .findFirst()
                .orElse(null);
        HarnessPersistenceFlowInstanceRow flowInstance = new HarnessPersistenceFlowInstanceRow(
                instanceId,
                String.valueOf(model.get("flowCode")),
                engineResult.instanceStatus(),
                engineResult.errorCode(),
                instanceErrorMessage,
                toJson(engineResult.flowOutput()),
                durationMs
        );
        List<HarnessPersistenceNodeExecutionRow> nodeExecutions = new ArrayList<>();
        List<HarnessPersistenceNodeAttemptRow> nodeAttempts = new ArrayList<>();
        for (Map.Entry<String, FluxionNodeExecutionResult> entry : engineResult.nodeResults().entrySet()) {
            FluxionNodeExecutionResult nodeResult = entry.getValue();
            nodeExecutions.add(new HarnessPersistenceNodeExecutionRow(
                    instanceId,
                    entry.getKey(),
                    nodeTypes.getOrDefault(entry.getKey(), nodeResult.nodeType()),
                    nodeResult.status(),
                    nodeResult.durationMs(),
                    nodeResult.attemptCount(),
                    nodeResult.skipReason(),
                    nodeResult.errorCode(),
                    nodeResult.errorMessage(),
                    toJson(nodeResult.output())
            ));
            for (FluxionNodeAttemptDetail detail : nodeResult.attemptDetails()) {
                nodeAttempts.add(new HarnessPersistenceNodeAttemptRow(
                        instanceId,
                        entry.getKey(),
                        detail.attempt(),
                        detail.status(),
                        detail.durationMs(),
                        detail.errorCode(),
                        detail.errorMessage()
                ));
            }
        }
        return new HarnessPersistenceWriteRequest(flowInstance, List.copyOf(nodeExecutions), List.copyOf(nodeAttempts));
    }

    private String toJson(Object value) {
        try {
            return HarnessJson.MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize persistence harness value", exception);
        }
    }

    private Map<String, String> toNodeStatuses(List<HarnessPersistenceNodeExecutionRow> rows) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (HarnessPersistenceNodeExecutionRow row : rows) {
            result.put(row.nodeId(), row.status());
        }
        return result;
    }

    private Map<String, String> toSkipReasons(List<HarnessPersistenceNodeExecutionRow> rows) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (HarnessPersistenceNodeExecutionRow row : rows) {
            if (row.skipReason() != null) {
                result.put(row.nodeId(), row.skipReason());
            }
        }
        return result;
    }

    private Map<String, Integer> toAttemptCounts(List<HarnessPersistenceNodeExecutionRow> rows) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (HarnessPersistenceNodeExecutionRow row : rows) {
            result.put(row.nodeId(), row.attemptCount());
        }
        return result;
    }

    private Map<String, List<Map<String, Object>>> toAttempts(
            List<HarnessPersistenceNodeExecutionRow> nodeExecutionRows,
            List<HarnessPersistenceNodeAttemptRow> attemptRows
    ) {
        LinkedHashMap<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (HarnessPersistenceNodeExecutionRow row : nodeExecutionRows) {
            result.put(row.nodeId(), new ArrayList<>());
        }
        for (HarnessPersistenceNodeAttemptRow row : attemptRows) {
            result.computeIfAbsent(row.nodeId(), ignored -> new ArrayList<>())
                    .add(toAttemptMap(row));
        }
        return result;
    }

    private Map<String, Object> toAttemptMap(HarnessPersistenceNodeAttemptRow row) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("attempt_no", row.attemptNo());
        result.put("status", row.status());
        result.put("duration_ms", row.durationMs());
        result.put("error_code", row.errorCode());
        result.put("error_message", row.errorMessage());
        return result;
    }
}
