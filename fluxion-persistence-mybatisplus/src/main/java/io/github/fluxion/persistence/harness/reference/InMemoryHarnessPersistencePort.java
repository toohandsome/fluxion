package io.github.fluxion.persistence.harness.reference;

import io.github.fluxion.persistence.harness.api.HarnessPersistenceNodeAttemptRow;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceNodeExecutionRow;
import io.github.fluxion.persistence.harness.api.HarnessPersistencePort;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceSnapshot;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceWriteRequest;
import java.util.ArrayList;
import java.util.List;

public final class InMemoryHarnessPersistencePort implements HarnessPersistencePort {

    @Override
    public HarnessPersistenceSnapshot persist(HarnessPersistenceWriteRequest request) {
        List<HarnessPersistenceNodeExecutionRow> nodeExecutions = new ArrayList<>(request.nodeExecutions());
        List<HarnessPersistenceNodeAttemptRow> nodeAttempts = new ArrayList<>(request.nodeAttempts());
        return new HarnessPersistenceSnapshot(request.flowInstance(), List.copyOf(nodeExecutions), List.copyOf(nodeAttempts));
    }
}
