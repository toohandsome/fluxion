package io.github.fluxion.test.harness;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class HarnessJson {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private HarnessJson() {
    }

    public static JsonNode readTree(HarnessCommandRunner runner, String relativePath) throws IOException {
        return MAPPER.readTree(runner.readText(Path.of(relativePath)));
    }

    public static Map<String, Object> readMap(HarnessCommandRunner runner, String relativePath) throws IOException {
        return MAPPER.readValue(runner.readText(Path.of(relativePath)), new TypeReference<>() {
        });
    }

    public static List<String> readSelectedSuites(HarnessCommandRunner.CommandResult result) throws IOException {
        JsonNode tree = MAPPER.readTree(result.output());
        return MAPPER.convertValue(tree.get("selectedSuites"), new TypeReference<>() {
        });
    }
}
