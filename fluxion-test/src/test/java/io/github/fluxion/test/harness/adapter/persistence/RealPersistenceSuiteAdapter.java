package io.github.fluxion.test.harness.adapter.persistence;

import io.github.fluxion.test.harness.HarnessCommandRunner;
import io.github.fluxion.test.harness.adapter.HarnessAdapterRequest;
import io.github.fluxion.test.harness.adapter.HarnessAdapterResponse;
import io.github.fluxion.test.harness.adapter.HarnessSuiteAdapter;
import java.util.List;

public final class RealPersistenceSuiteAdapter implements HarnessSuiteAdapter {

    private final RealPersistenceExecutionPort executionPort;
    private final PersistenceFixtureMapper fixtureMapper;
    private final RealPersistenceResultComparator comparator;

    public RealPersistenceSuiteAdapter() {
        this(new FluxionPersistenceBackedExecutionPort(), new PersistenceFixtureMapper(), new RealPersistenceResultComparator());
    }

    public RealPersistenceSuiteAdapter(
            RealPersistenceExecutionPort executionPort,
            PersistenceFixtureMapper fixtureMapper,
            RealPersistenceResultComparator comparator
    ) {
        this.executionPort = executionPort;
        this.fixtureMapper = fixtureMapper;
        this.comparator = comparator;
    }

    @Override
    public String suite() {
        return "persistence-real";
    }

    @Override
    public HarnessAdapterResponse execute(HarnessAdapterRequest request, HarnessCommandRunner runner) throws Exception {
        if (request.caseId() == null || request.caseId().isBlank()) {
            throw new IllegalArgumentException("persistence-real adapter requires a specific --case / caseId");
        }
        PersistenceFixtureScenario scenario = fixtureMapper.load(runner.repoRoot(), request.caseId());
        RealPersistenceExecutionResult actual = executionPort.execute(fixtureMapper.toExecutionRequest(scenario));
        List<String> mismatches = comparator.compare(scenario.expect(), actual);
        return new HarnessAdapterResponse(
                suite(),
                request.caseId(),
                mismatches.isEmpty(),
                "java-adapter:" + suite(),
                scenario.fixturePath().toString().replace('\\', '/'),
                List.of(request.caseId()),
                mismatches.isEmpty() ? "persistence-real adapter passed" : String.join("; ", mismatches)
        );
    }
}
