package io.github.fluxion.persistence.harness.mybatis.mapper;

import io.github.fluxion.persistence.harness.mybatis.entity.HarnessNodeExecutionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

public interface HarnessNodeExecutionMapper {

    @Insert("""
            insert into node_executions(
                instance_id,
                node_id,
                node_type,
                status,
                duration_ms,
                attempt_count,
                skip_reason,
                error_code,
                error_message,
                output_json
            ) values (
                #{instanceId},
                #{nodeId},
                #{nodeType},
                #{status},
                #{durationMs},
                #{attemptCount},
                #{skipReason},
                #{errorCode},
                #{errorMessage},
                #{outputJson}
            )
            """)
    int insert(HarnessNodeExecutionEntity entity);

    @Select("""
            select
                instance_id,
                node_id,
                node_type,
                status,
                duration_ms,
                attempt_count,
                skip_reason,
                error_code,
                error_message,
                output_json
            from node_executions
            where instance_id = #{instanceId}
            order by node_id asc
            """)
    List<HarnessNodeExecutionEntity> selectByInstanceId(long instanceId);
}
