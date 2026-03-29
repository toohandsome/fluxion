package io.github.fluxion.persistence.harness.api;

import java.util.List;

public record HarnessPersistenceSnapshot(
        HarnessPersistenceFlowInstanceRow flowInstance,
        List<HarnessPersistenceNodeExecutionRow> nodeExecutions,
        List<HarnessPersistenceNodeAttemptRow> nodeAttempts
) {
}
