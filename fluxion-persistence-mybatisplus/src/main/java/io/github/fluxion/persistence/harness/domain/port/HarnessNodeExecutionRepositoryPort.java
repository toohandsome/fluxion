package io.github.fluxion.persistence.harness.domain.port;

import io.github.fluxion.persistence.harness.api.HarnessPersistenceNodeExecutionRow;
import java.util.List;

public interface HarnessNodeExecutionRepositoryPort {

    void saveAll(List<HarnessPersistenceNodeExecutionRow> rows);

    List<HarnessPersistenceNodeExecutionRow> findByInstanceId(long instanceId);
}
