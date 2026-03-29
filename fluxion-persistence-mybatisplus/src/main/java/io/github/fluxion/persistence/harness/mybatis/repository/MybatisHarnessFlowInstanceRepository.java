package io.github.fluxion.persistence.harness.mybatis.repository;

import io.github.fluxion.persistence.harness.api.HarnessPersistenceFlowInstanceRow;
import io.github.fluxion.persistence.harness.domain.port.HarnessFlowInstanceRepositoryPort;
import io.github.fluxion.persistence.harness.mybatis.entity.HarnessFlowInstanceEntity;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessFlowInstanceMapper;

public final class MybatisHarnessFlowInstanceRepository implements HarnessFlowInstanceRepositoryPort {

    private final HarnessFlowInstanceMapper mapper;

    public MybatisHarnessFlowInstanceRepository(HarnessFlowInstanceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(HarnessPersistenceFlowInstanceRow row) {
        mapper.insert(toEntity(row));
    }

    @Override
    public HarnessPersistenceFlowInstanceRow findByInstanceId(long instanceId) {
        HarnessFlowInstanceEntity entity = mapper.selectByInstanceId(instanceId);
        if (entity == null) {
            throw new IllegalStateException("Missing persisted flow_instance row");
        }
        return new HarnessPersistenceFlowInstanceRow(
                entity.getInstanceId(),
                entity.getFlowCode(),
                entity.getStatus(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getFlowOutputJson(),
                entity.getDurationMs()
        );
    }

    private HarnessFlowInstanceEntity toEntity(HarnessPersistenceFlowInstanceRow row) {
        HarnessFlowInstanceEntity entity = new HarnessFlowInstanceEntity();
        entity.setInstanceId(row.instanceId());
        entity.setFlowCode(row.flowCode());
        entity.setStatus(row.status());
        entity.setErrorCode(row.errorCode());
        entity.setErrorMessage(row.errorMessage());
        entity.setFlowOutputJson(row.flowOutputJson());
        entity.setDurationMs(row.durationMs());
        return entity;
    }
}
