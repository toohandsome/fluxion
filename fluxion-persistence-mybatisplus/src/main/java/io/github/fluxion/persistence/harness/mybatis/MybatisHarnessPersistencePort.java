package io.github.fluxion.persistence.harness.mybatis;

import io.github.fluxion.persistence.harness.api.HarnessPersistencePort;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceSnapshot;
import io.github.fluxion.persistence.harness.api.HarnessPersistenceWriteRequest;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessFlowInstanceMapper;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessNodeAttemptMapper;
import io.github.fluxion.persistence.harness.mybatis.mapper.HarnessNodeExecutionMapper;
import io.github.fluxion.persistence.harness.mybatis.repository.MybatisHarnessPersistenceRepository;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public final class MybatisHarnessPersistencePort implements HarnessPersistencePort {

    private final SqlSessionFactory sqlSessionFactory;

    public MybatisHarnessPersistencePort(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    public HarnessPersistenceSnapshot persist(HarnessPersistenceWriteRequest request) {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            MybatisHarnessPersistenceRepository repository = new MybatisHarnessPersistenceRepository(
                    session.getMapper(HarnessFlowInstanceMapper.class),
                    session.getMapper(HarnessNodeExecutionMapper.class),
                    session.getMapper(HarnessNodeAttemptMapper.class)
            );
            try {
                repository.save(request);
                session.commit();
                return repository.fetch(request.flowInstance().instanceId());
            } catch (RuntimeException exception) {
                session.rollback();
                throw exception;
            }
        }
    }
}
