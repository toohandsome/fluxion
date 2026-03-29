package io.github.fluxion.persistence.harness.api;

public interface HarnessPersistencePort {

    HarnessPersistenceSnapshot persist(HarnessPersistenceWriteRequest request);
}
