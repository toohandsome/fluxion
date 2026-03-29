package io.github.fluxion.persistence.harness.mybatis.repository;

import io.github.fluxion.persistence.harness.api.HarnessPersistenceNodeExecutionRow;
import io.github.fluxion.persistence.harness.domain.port.HarnessNodeExecutionRepositoryPort;
import io.github.fluxion.persistence.harness.mybatis.entity.HarnessNodeExecutionEntity;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessNodeExecutionMapper;
import java.util.List;

public final class MybatisHarnessNodeExecutionRepository implements HarnessNodeExecutionRepositoryPort {

    private final HarnessNodeExecutionMapper mapper;

    public MybatisHarnessNodeExecutionRepository(HarnessNodeExecutionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void saveAll(List<HarnessPersistenceNodeExecutionRow> rows) {
        for (HarnessPersistenceNodeExecutionRow row : rows) {
            mapper.insert(toEntity(row));
        }
    }

    @Override
    public List<HarnessPersistenceNodeExecutionRow> findByInstanceId(long instanceId) {
        return mapper.selectByInstanceId(instanceId).stream().map(this::toRow).toList();
    }

    private HarnessNodeExecutionEntity toEntity(HarnessPersistenceNodeExecutionRow row) {
        HarnessNodeExecutionEntity entity = new HarnessNodeExecutionEntity();
        entity.setInstanceId(row.instanceId());
        entity.setNodeId(row.nodeId());
        entity.setNodeType(row.nodeType());
        entity.setStatus(row.status());
        entity.setDurationMs(row.durationMs());
        entity.setAttemptCount(row.attemptCount());
        entity.setSkipReason(row.skipReason());
        entity.setErrorCode(row.errorCode());
        entity.setErrorMessage(row.errorMessage());
        entity.setOutputJson(row.outputJson());
        return entity;
    }

    private HarnessPersistenceNodeExecutionRow toRow(HarnessNodeExecutionEntity entity) {
        return new HarnessPersistenceNodeExecutionRow(
                entity.getInstanceId(),
                entity.getNodeId(),
                entity.getNodeType(),
                entity.getStatus(),
                entity.getDurationMs(),
                entity.getAttemptCount(),
                entity.getSkipReason(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getOutputJson()
        );
    }
}
