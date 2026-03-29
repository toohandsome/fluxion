package io.github.fluxion.persistence.harness.mybatis.repository;

import io.github.fluxion.persistence.harness.api.HarnessPersistenceNodeAttemptRow;
import io.github.fluxion.persistence.harness.domain.port.HarnessNodeAttemptRepositoryPort;
import io.github.fluxion.persistence.harness.mybatis.entity.HarnessNodeAttemptEntity;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessNodeAttemptMapper;
import java.util.List;

public final class MybatisHarnessNodeAttemptRepository implements HarnessNodeAttemptRepositoryPort {

    private final HarnessNodeAttemptMapper mapper;

    public MybatisHarnessNodeAttemptRepository(HarnessNodeAttemptMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void saveAll(List<HarnessPersistenceNodeAttemptRow> rows) {
        for (HarnessPersistenceNodeAttemptRow row : rows) {
            mapper.insert(toEntity(row));
        }
    }

    @Override
    public List<HarnessPersistenceNodeAttemptRow> findByInstanceId(long instanceId) {
        return mapper.selectByInstanceId(instanceId).stream().map(this::toRow).toList();
    }

    private HarnessNodeAttemptEntity toEntity(HarnessPersistenceNodeAttemptRow row) {
        HarnessNodeAttemptEntity entity = new HarnessNodeAttemptEntity();
        entity.setInstanceId(row.instanceId());
        entity.setNodeId(row.nodeId());
        entity.setAttemptNo(row.attemptNo());
        entity.setStatus(row.status());
        entity.setDurationMs(row.durationMs());
        entity.setErrorCode(row.errorCode());
        entity.setErrorMessage(row.errorMessage());
        return entity;
    }

    private HarnessPersistenceNodeAttemptRow toRow(HarnessNodeAttemptEntity entity) {
        return new HarnessPersistenceNodeAttemptRow(
                entity.getInstanceId(),
                entity.getNodeId(),
                entity.getAttemptNo(),
                entity.getStatus(),
                entity.getDurationMs(),
                entity.getErrorCode(),
                entity.getErrorMessage()
        );
    }
}
