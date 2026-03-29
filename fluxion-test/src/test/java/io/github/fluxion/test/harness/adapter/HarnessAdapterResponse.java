package io.github.fluxion.test.harness.adapter;

import java.util.List;

public record HarnessAdapterResponse(
        String suite,
        String caseId,
        boolean success,
        String command,
        String resultsPath,
        List<String> observedCases,
        String output
) {
}
