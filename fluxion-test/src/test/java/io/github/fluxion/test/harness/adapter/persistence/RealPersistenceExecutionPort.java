package io.github.fluxion.test.harness.adapter.persistence;

public interface RealPersistenceExecutionPort {

    RealPersistenceExecutionResult execute(RealPersistenceExecutionRequest request);
}
