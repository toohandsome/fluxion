package io.github.fluxion.persistence.harness.mybatis.support;

import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessFlowInstanceMapper;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessNodeAttemptMapper;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessNodeExecutionMapper;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

public final class HarnessPersistenceMybatisSupport {

    private HarnessPersistenceMybatisSupport() {
    }

    public static SqlSessionFactory createSqlSessionFactory(DataSource dataSource) {
        Configuration configuration = new Configuration(
                new Environment("fluxion-harness-persistence", new JdbcTransactionFactory(), dataSource)
        );
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(HarnessFlowInstanceMapper.class);
        configuration.addMapper(HarnessNodeExecutionMapper.class);
        configuration.addMapper(HarnessNodeAttemptMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }
}
