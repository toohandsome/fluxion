package io.github.fluxion.persistence.harness.mybatis.repository;

import io.github.fluxion.persistence.harness.api.HarnessPersistenceFlowInstanceRow;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceNodeAttemptRow;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceNodeExecutionRow;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceSnapshot;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceWriteRequest;
import io.github.fluxion.persistence.harness.mybatis.entity.HarnessFlowInstanceEntity;
import io.github.fluxion.persistence.harness.mybatis.entity.HarnessNodeAttemptEntity;
import io.github.fluxion.persistence.harness.mybatis.entity.HarnessNodeExecutionEntity;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessFlowInstanceMapper;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessNodeAttemptMapper;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessNodeExecutionMapper;
import java.util.List;

public final class MybatisHarnessPersistenceRepository {

    private final HarnessFlowInstanceMapper flowInstanceMapper;
    private final HarnessNodeExecutionMapper nodeExecutionMapper;
    private final HarnessNodeAttemptMapper nodeAttemptMapper;

    public MybatisHarnessPersistenceRepository(
            HarnessFlowInstanceMapper flowInstanceMapper,
            HarnessNodeExecutionMapper nodeExecutionMapper,
            HarnessNodeAttemptMapper nodeAttemptMapper
    ) {
        this.flowInstanceMapper = flowInstanceMapper;
        this.nodeExecutionMapper = nodeExecutionMapper;
        this.nodeAttemptMapper = nodeAttemptMapper;
    }

    public void save(HarnessPersistenceWriteRequest request) {
        flowInstanceMapper.insert(toEntity(request.flowInstance()));
        for (HarnessPersistenceNodeExecutionRow row : request.nodeExecutions()) {
            nodeExecutionMapper.insert(toEntity(row));
        }
        for (HarnessPersistenceNodeAttemptRow row : request.nodeAttempts()) {
            nodeAttemptMapper.insert(toEntity(row));
        }
    }

    public HarnessPersistenceSnapshot fetch(long instanceId) {
        return new HarnessPersistenceSnapshot(
                toRow(flowInstanceMapper.selectByInstanceId(instanceId)),
                nodeExecutionMapper.selectByInstanceId(instanceId).stream().map(this::toRow).toList(),
                nodeAttemptMapper.selectByInstanceId(instanceId).stream().map(this::toRow).toList()
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

    private HarnessPersistenceFlowInstanceRow toRow(HarnessFlowInstanceEntity entity) {
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
