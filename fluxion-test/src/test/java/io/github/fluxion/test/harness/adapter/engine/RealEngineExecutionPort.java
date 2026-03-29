package io.github.fluxion.test.harness.adapter.engine;

public interface RealEngineExecutionPort {

    RealEngineExecutionResult execute(RealEngineExecutionRequest request) throws Exception;
}
