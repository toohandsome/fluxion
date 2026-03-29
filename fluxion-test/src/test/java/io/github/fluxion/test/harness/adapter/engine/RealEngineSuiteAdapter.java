package io.github.fluxion.test.harness.adapter.engine;

import io.github.fluxion.test.harness.HarnessCommandRunner;
import io.github.fluxion.test.harness.adapter.HarnessAdapterRequest;
import io.github.fluxion.test.harness.adapter.HarnessAdapterResponse;
import io.github.fluxion.test.harness.adapter.HarnessSuiteAdapter;
import java.util.List;

public final class RealEngineSuiteAdapter implements HarnessSuiteAdapter {

    private final RealEngineExecutionPort executionPort;
    private final EngineFixtureMapper fixtureMapper;
    private final RealEngineResultComparator comparator;

    public RealEngineSuiteAdapter() {
        this(new UnsupportedRealEngineExecutionPort(), new EngineFixtureMapper(), new RealEngineResultComparator());
    }

    public RealEngineSuiteAdapter(
            RealEngineExecutionPort executionPort,
            EngineFixtureMapper fixtureMapper,
            RealEngineResultComparator comparator
    ) {
        this.executionPort = executionPort;
        this.fixtureMapper = fixtureMapper;
        this.comparator = comparator;
    }

    @Override
    public String suite() {
        return "engine-real";
    }

    @Override
    public HarnessAdapterResponse execute(HarnessAdapterRequest request, HarnessCommandRunner runner) throws Exception {
        if (request.caseId() == null || request.caseId().isBlank()) {
            throw new IllegalArgumentException("engine-real adapter requires a specific --case / caseId");
        }
        EngineFixtureScenario scenario = fixtureMapper.load(runner.repoRoot(), request.caseId());
        RealEngineExecutionRequest executionRequest = fixtureMapper.toExecutionRequest(scenario);
        RealEngineExecutionResult actual = executionPort.execute(executionRequest);
        List<String> mismatches = comparator.compare(scenario.expect(), actual);
        return new HarnessAdapterResponse(
                suite(),
                request.caseId(),
                mismatches.isEmpty(),
                "java-adapter:" + suite(),
                scenario.fixturePath().toString().replace('\\', '/'),
                List.of(request.caseId()),
                mismatches.isEmpty() ? "engine-real adapter passed" : String.join("; ", mismatches)
        );
    }
}
