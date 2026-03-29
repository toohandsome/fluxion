package io.github.fluxion.test.harness;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class HarnessCommandRunner {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    private final Path repoRoot;
    private final String pythonCommand;

    public HarnessCommandRunner(Path repoRoot) {
        this.repoRoot = repoRoot;
        this.pythonCommand = resolvePythonCommand();
    }

    public Path repoRoot() {
        return repoRoot;
    }

    public CommandResult runPython(List<String> args) throws IOException, InterruptedException {
        return run(List.of(pythonCommand), args, DEFAULT_TIMEOUT);
    }

    public CommandResult run(List<String> commandPrefix, List<String> args, Duration timeout)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(commandPrefix);
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(repoRoot.toFile());
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        boolean finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Command timed out: " + String.join(" ", command));
        }
        return new CommandResult(command, process.exitValue(), output);
    }

    public String readText(Path relativePath) throws IOException {
        return Files.readString(repoRoot.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private String resolvePythonCommand() {
        String fromProperty = System.getProperty("fluxion.python.command");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        return Optional.ofNullable(System.getenv("PYTHON"))
                .filter(value -> !value.isBlank())
                .orElse("python");
    }

    public record CommandResult(List<String> command, int exitCode, String output) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
