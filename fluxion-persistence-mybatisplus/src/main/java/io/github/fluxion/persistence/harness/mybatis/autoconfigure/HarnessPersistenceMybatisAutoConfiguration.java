package io.github.fluxion.persistence.harness.mybatis.autoconfigure;

import io.github.fluxion.persistence.harness.api.HarnessPersistencePort;
import io.github.fluxion.persistence.harness.domain.port.HarnessFlowInstanceRepositoryPort;
import io.github.fluxion.persistence.harness.domain.port.HarnessNodeAttemptRepositoryPort;
import io.github.fluxion.persistence.harness.domain.port.HarnessNodeExecutionRepositoryPort;
import io.github.fluxion.persistence.harness.domain.service.RepositoryBackedHarnessPersistencePort;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessFlowInstanceMapper;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessNodeAttemptMapper;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessNodeExecutionMapper;
import io.github.fluxion.persistence.harness.mybatis.repository.MybatisHarnessFlowInstanceRepository;
import io.github.fluxion.persistence.harness.mybatis.repository.MybatisHarnessNodeAttemptRepository;
import io.github.fluxion.persistence.harness.mybatis.repository.MybatisHarnessNodeExecutionRepository;
import io.github.fluxion.persistence.harness.mybatis.support.HarnessPersistenceSchemaSupport;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@AutoConfiguration
@ConditionalOnClass({SqlSessionFactory.class, SqlSessionFactoryBean.class})
@ConditionalOnProperty(prefix = "fluxion.harness.persistence", name = "enabled", havingValue = "true")
@ConditionalOnBean(DataSource.class)
@EnableTransactionManagement
@MapperScan("io.github.fluxion.persistence.harness.mybatis.mapper")
public class HarnessPersistenceMybatisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SqlSessionFactory.class)
    public SqlSessionFactory harnessPersistenceSqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factoryBean.setConfiguration(configuration);
        return factoryBean.getObject();
    }

    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager harnessPersistenceTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fluxion.harness.persistence", name = "initialize-schema", havingValue = "true")
    public InitializingBean harnessPersistenceSchemaInitializer(DataSource dataSource) {
        return () -> HarnessPersistenceSchemaSupport.initialize(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public HarnessFlowInstanceRepositoryPort harnessFlowInstanceRepositoryPort(HarnessFlowInstanceMapper mapper) {
        return new MybatisHarnessFlowInstanceRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public HarnessNodeExecutionRepositoryPort harnessNodeExecutionRepositoryPort(HarnessNodeExecutionMapper mapper) {
        return new MybatisHarnessNodeExecutionRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public HarnessNodeAttemptRepositoryPort harnessNodeAttemptRepositoryPort(HarnessNodeAttemptMapper mapper) {
        return new MybatisHarnessNodeAttemptRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(HarnessPersistencePort.class)
    public HarnessPersistencePort harnessPersistencePort(
            HarnessFlowInstanceRepositoryPort flowInstanceRepositoryPort,
            HarnessNodeExecutionRepositoryPort nodeExecutionRepositoryPort,
            HarnessNodeAttemptRepositoryPort nodeAttemptRepositoryPort
    ) {
        return new RepositoryBackedHarnessPersistencePort(
                flowInstanceRepositoryPort,
                nodeExecutionRepositoryPort,
                nodeAttemptRepositoryPort
        );
    }
}
