package io.github.fluxion.persistence.harness.mybatis.mapper;

import io.github.fluxion.persistence.harness.mybatis.entity.HarnessNodeAttemptEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

public interface HarnessNodeAttemptMapper {

    @Insert("""
            insert into node_execution_attempts(
                instance_id,
                node_id,
                attempt_no,
                status,
                duration_ms,
                error_code,
                error_message
            ) values (
                #{instanceId},
                #{nodeId},
                #{attemptNo},
                #{status},
                #{durationMs},
                #{errorCode},
                #{errorMessage}
            )
            """)
    int insert(HarnessNodeAttemptEntity entity);

    @Select("""
            select
                instance_id,
                node_id,
                attempt_no,
                status,
                duration_ms,
                error_code,
                error_message
            from node_execution_attempts
            where instance_id = #{instanceId}
            order by node_id asc, attempt_no asc
            """)
    List<HarnessNodeAttemptEntity> selectByInstanceId(long instanceId);
}
