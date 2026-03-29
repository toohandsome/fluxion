package io.github.fluxion.persistence.harness.mybatis.mapper;

import io.github.fluxion.persistence.harness.mybatis.entity.HarnessFlowInstanceEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

public interface HarnessFlowInstanceMapper {

    @Insert("""
            insert into flow_instances(
                instance_id,
                flow_code,
                status,
                error_code,
                error_message,
                flow_output_json,
                duration_ms
            ) values (
                #{instanceId},
                #{flowCode},
                #{status},
                #{errorCode},
                #{errorMessage},
                #{flowOutputJson},
                #{durationMs}
            )
            """)
    int insert(HarnessFlowInstanceEntity entity);

    @Select("""
            select
                instance_id,
                flow_code,
                status,
                error_code,
                error_message,
                flow_output_json,
                duration_ms
            from flow_instances
            where instance_id = #{instanceId}
            """)
    HarnessFlowInstanceEntity selectByInstanceId(long instanceId);
}
