package io.github.fluxion.test.harness.adapter;

import io.github.fluxion.test.harness.HarnessCommandRunner;

public interface HarnessSuiteAdapter {

    String suite();

    HarnessAdapterResponse execute(HarnessAdapterRequest request, HarnessCommandRunner runner) throws Exception;
}
