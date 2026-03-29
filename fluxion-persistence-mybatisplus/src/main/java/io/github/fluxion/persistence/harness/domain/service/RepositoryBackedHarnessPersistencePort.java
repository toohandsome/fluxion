package io.github.fluxion.persistence.harness.domain.service;

import io.github.fluxion.persistence.harness.api.HarnessPersistencePort;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceSnapshot;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceWriteRequest;
import io.github.fluxion.persistence.harness.domain.port.HarnessFlowInstanceRepositoryPort;
import io.github.fluxion.persistence.harness.domain.port.HarnessNodeAttemptRepositoryPort;
import io.github.fluxion.persistence.harness.domain.port.HarnessNodeExecutionRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

public class RepositoryBackedHarnessPersistencePort implements HarnessPersistencePort {

    private final HarnessFlowInstanceRepositoryPort flowInstanceRepositoryPort;
    private final HarnessNodeExecutionRepositoryPort nodeExecutionRepositoryPort;
    private final HarnessNodeAttemptRepositoryPort nodeAttemptRepositoryPort;

    public RepositoryBackedHarnessPersistencePort(
            HarnessFlowInstanceRepositoryPort flowInstanceRepositoryPort,
            HarnessNodeExecutionRepositoryPort nodeExecutionRepositoryPort,
            HarnessNodeAttemptRepositoryPort nodeAttemptRepositoryPort
    ) {
        this.flowInstanceRepositoryPort = flowInstanceRepositoryPort;
        this.nodeExecutionRepositoryPort = nodeExecutionRepositoryPort;
        this.nodeAttemptRepositoryPort = nodeAttemptRepositoryPort;
    }

    @Override
    @Transactional
    public HarnessPersistenceSnapshot persist(HarnessPersistenceWriteRequest request) {
        long instanceId = request.flowInstance().instanceId();
        flowInstanceRepositoryPort.save(request.flowInstance());
        nodeExecutionRepositoryPort.saveAll(request.nodeExecutions());
        nodeAttemptRepositoryPort.saveAll(request.nodeAttempts());
        return new HarnessPersistenceSnapshot(
                flowInstanceRepositoryPort.findByInstanceId(instanceId),
                nodeExecutionRepositoryPort.findByInstanceId(instanceId),
                nodeAttemptRepositoryPort.findByInstanceId(instanceId)
        );
    }
}
