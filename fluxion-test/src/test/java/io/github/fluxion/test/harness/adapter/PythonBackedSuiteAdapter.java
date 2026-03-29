package io.github.fluxion.test.harness.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.fluxion.test.harness.HarnessCommandRunner;
import io.github.fluxion.test.harness.HarnessJson;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PythonBackedSuiteAdapter implements HarnessSuiteAdapter {

    private final String suite;
    private final String scriptPath;
    private final String resultsPath;

    public PythonBackedSuiteAdapter(String suite, String scriptPath, String resultsPath) {
        this.suite = suite;
        this.scriptPath = scriptPath;
        this.resultsPath = resultsPath;
    }

    @Override
    public String suite() {
        return suite;
    }

    @Override
    public HarnessAdapterResponse execute(HarnessAdapterRequest request, HarnessCommandRunner runner) throws Exception {
        List<String> args = new ArrayList<>();
        args.add(scriptPath);
        if (request.caseId() != null && !request.caseId().isBlank()) {
            args.add("--case");
            args.add(request.caseId());
        }
        HarnessCommandRunner.CommandResult result = runner.runPython(args);
        JsonNode tree = HarnessJson.readTree(runner, resultsPath);
        List<String> observedCases = new ArrayList<>();
        tree.path("cases").forEach(node -> observedCases.add(node.path("caseId").asText()));
        boolean success = result.isSuccess() && tree.path("stats").path("failed").asInt() == 0;
        return new HarnessAdapterResponse(
                suite,
                request.caseId(),
                success,
                String.join(" ", result.command()),
                Path.of(resultsPath).toString().replace('\\', '/'),
                observedCases,
                result.output()
        );
    }
}
