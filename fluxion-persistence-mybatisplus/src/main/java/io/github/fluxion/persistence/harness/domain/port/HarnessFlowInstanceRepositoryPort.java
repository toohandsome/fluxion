package io.github.fluxion.persistence.harness.domain.port;

import io.github.fluxion.persistence.harness.api.HarnessPersistenceFlowInstanceRow;

public interface HarnessFlowInstanceRepositoryPort {

    void save(HarnessPersistenceFlowInstanceRow row);

    HarnessPersistenceFlowInstanceRow findByInstanceId(long instanceId);
}
