package io.github.fluxion.test.harness.adapter;

import io.github.fluxion.test.harness.adapter.engine.RealEngineSuiteAdapter;
import io.github.fluxion.test.harness.adapter.engine.FluxionEngineBackedExecutionPort;
import io.github.fluxion.test.harness.adapter.persistence.RealPersistenceSuiteAdapter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class HarnessAdapterRegistry {

    private final Map<String, HarnessSuiteAdapter> adapters = new LinkedHashMap<>();

    public HarnessAdapterRegistry() {
        register(new PythonBackedSuiteAdapter(
                "contracts",
                "tools/harness/run_contracts.py",
                ".artifacts/harness/contracts/results.json"
        ));
        register(new PythonBackedSuiteAdapter(
                "engine",
                "tools/harness/run_engine_cases.py",
                ".artifacts/harness/engine/results.json"
        ));
        register(new PythonBackedSuiteAdapter(
                "runtime-api",
                "tools/harness/run_runtime_api_cases.py",
                ".artifacts/harness/runtime-api/results.json"
        ));
        register(new PythonBackedSuiteAdapter(
                "scheduler",
                "tools/harness/run_scheduler_cases.py",
                ".artifacts/harness/scheduler/results.json"
        ));
        register(new PythonBackedSuiteAdapter(
                "persistence",
                "tools/harness/run_persistence_cases.py",
                ".artifacts/harness/persistence/results.json"
        ));
        register(new RealEngineSuiteAdapter(
                new FluxionEngineBackedExecutionPort(),
                new io.github.fluxion.test.harness.adapter.engine.EngineFixtureMapper(),
                new io.github.fluxion.test.harness.adapter.engine.RealEngineResultComparator()
        ));
        register(new RealPersistenceSuiteAdapter());
    }

    public Optional<HarnessSuiteAdapter> find(String suite) {
        return Optional.ofNullable(adapters.get(suite));
    }

    public Iterable<String> suites() {
        return adapters.keySet();
    }

    private void register(HarnessSuiteAdapter adapter) {
        adapters.put(adapter.suite(), adapter);
    }
}
