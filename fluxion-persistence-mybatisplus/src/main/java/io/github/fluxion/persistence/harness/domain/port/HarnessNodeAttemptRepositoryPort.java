package io.github.fluxion.persistence.harness.domain.port;

import io.github.fluxion.persistence.harness.api.HarnessPersistenceNodeAttemptRow;
import java.util.List;

public interface HarnessNodeAttemptRepositoryPort {

    void saveAll(List<HarnessPersistenceNodeAttemptRow> rows);

    List<HarnessPersistenceNodeAttemptRow> findByInstanceId(long instanceId);
}
