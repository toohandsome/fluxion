package io.github.fluxion.test.harness.adapter;

public record HarnessAdapterRequest(String suite, String caseId) {
    public static HarnessAdapterRequest of(String suite, String caseId) {
        return new HarnessAdapterRequest(suite, caseId);
    }
}
