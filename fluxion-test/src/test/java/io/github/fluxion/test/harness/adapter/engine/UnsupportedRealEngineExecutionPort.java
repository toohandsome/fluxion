package io.github.fluxion.test.harness.adapter.engine;

public final class UnsupportedRealEngineExecutionPort implements RealEngineExecutionPort {

    @Override
    public RealEngineExecutionResult execute(RealEngineExecutionRequest request) {
        throw new UnsupportedOperationException(
                "No Java engine implementation is wired yet. Provide a RealEngineExecutionPort backed by fluxion-engine."
        );
    }
}
