package io.github.fluxion.engine.reference;

import io.github.fluxion.engine.api.FluxionEngineExecutionEntry;
import io.github.fluxion.engine.api.FluxionEngineExecutionRequest;
import io.github.fluxion.engine.api.FluxionEngineExecutionResult;
import io.github.fluxion.engine.api.FluxionNodeAttemptDetail;
import io.github.fluxion.engine.api.FluxionNodeExecutionResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InMemoryFluxionEngineExecutionEntry implements FluxionEngineExecutionEntry {

    private static final Pattern INSERT_PATTERN =
            Pattern.compile("insert\\s+into\\s+(\\w+)\\s*\\(([^)]+)\\)\\s*values\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_PATTERN =
            Pattern.compile("update\\s+(\\w+)\\s+set\\s+(.+?)\\s+where\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_PATTERN =
            Pattern.compile(
                    "select\\s+(.+?)\\s+from\\s+(\\w+)(?:\\s+where\\s+(.+?))?(?:\\s+order\\s+by\\s+(.+?))?(?:\\s+limit\\s+([^\\s]+))?\\s*$",
                    Pattern.CASE_INSENSITIVE
            );

    private final ReferenceExpressionEvaluator evaluator = new ReferenceExpressionEvaluator();

    @Override
    public FluxionEngineExecutionResult execute(FluxionEngineExecutionRequest request) {
        Map<String, Object> model = requireMap(request.model(), "model");
        List<Map<String, Object>> nodes = castListOfMaps(model.get("nodes"));
        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            nodeMap.put(String.valueOf(node.get("nodeId")), node);
        }
        List<Map<String, Object>> edges = castListOfMaps(model.get("edges"));
        Map<String, List<Map<String, Object>>> incoming = new LinkedHashMap<>();
        for (String nodeId : nodeMap.keySet()) {
            incoming.put(nodeId, new ArrayList<>());
        }
        for (Map<String, Object> edge : edges) {
            String target = String.valueOf(edge.get("targetNodeId"));
            incoming.get(target).add(edge);
        }

        ExecutionContext context = new ExecutionContext(request.trigger(), request.resources(), model);
        LinkedHashMap<String, FluxionNodeExecutionResult> nodeResults = new LinkedHashMap<>();
        List<String> orderedNodeIds = castStringList(requireMap(model.get("topology"), "topology").get("orderedNodeIds"));
        List<String> startNodeIds = castStringList(model.get("startNodeIds"));
        boolean failed = false;

        for (String nodeId : orderedNodeIds) {
            if (failed) {
                break;
            }
            Map<String, Object> node = nodeMap.get(nodeId);
            boolean active = isStartNode(nodeId, startNodeIds)
                    || isNodeActivated(incoming.get(nodeId), nodeMap, nodeResults);
            if (!active) {
                String skipReason = inferSkipReason(incoming.get(nodeId), nodeMap, nodeResults);
                FluxionNodeExecutionResult skipped = new FluxionNodeExecutionResult(
                        nodeId,
                        String.valueOf(node.get("nodeType")),
                        "SKIPPED",
                        null,
                        null,
                        null,
                        skipReason,
                        null,
                        0,
                        0,
                        List.of()
                );
                nodeResults.put(nodeId, skipped);
                context.putNode(nodeId, skipped);
                continue;
            }

            FluxionNodeExecutionResult result = runNode(node, context);
            nodeResults.put(nodeId, result);
            context.putNode(nodeId, result);
            if ("FAILED".equals(result.status())) {
                context.instance().put("status", "FAILED");
                context.instance().put("errorCode", result.errorCode());
                context.instance().put("errorMessage", result.errorMessage());
                failed = true;
            }
        }

        Map<String, Object> flowOutput = null;
        if (!failed) {
            try {
                flowOutput = castMap(evaluator.evaluate(model.get("flowOutputMapping"), context.toExpressionContext(null, null)));
                context.instance().put("status", "SUCCESS");
            } catch (RuntimeException exception) {
                context.instance().put("status", "FAILED");
                context.instance().put("errorCode", "FLOW_OUTPUT_EVAL_FAILED");
                context.instance().put("errorMessage", exception.getMessage());
            }
        }

        List<String> missingNodes = new ArrayList<>();
        for (String nodeId : orderedNodeIds) {
            if (!nodeResults.containsKey(nodeId)) {
                missingNodes.add(nodeId);
            }
        }
        return new FluxionEngineExecutionResult(
                String.valueOf(context.instance().get("status")),
                nullableString(context.instance().get("errorCode")),
                flowOutput,
                nodeResults,
                missingNodes
        );
    }

    private FluxionNodeExecutionResult runNode(Map<String, Object> node, ExecutionContext context) {
        String nodeId = String.valueOf(node.get("nodeId"));
        String nodeType = String.valueOf(node.get("nodeType"));
        Map<String, Object> runtimePolicy = castMap(node.get("runtimePolicy"));
        int maxAttempts = 1 + intValue(runtimePolicy == null ? null : runtimePolicy.get("retry"), 0);
        int timeoutMs = intValue(runtimePolicy == null ? null : runtimePolicy.get("timeoutMs"), 3000);
        int totalDurationMs = 0;
        String lastErrorCode = null;
        String lastErrorMessage = null;
        List<FluxionNodeAttemptDetail> attemptDetails = new ArrayList<>();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                Object resolvedInput = evaluator.evaluate(node.get("inputMapping"), context.toExpressionContext(null, null));
                NodeRawExecution rawExecution = executeRaw(node, context, resolvedInput, attempt);
                int durationMs = Math.max(elapsedMillis(startedAt), rawExecution.durationMs());
                if (durationMs > timeoutMs) {
                    throw new NodeTimeoutException("node exceeded timeoutMs=" + timeoutMs, durationMs);
                }
                totalDurationMs += durationMs;
                Map<String, Object> output = castMap(evaluator.evaluate(
                        node.get("outputMapping"),
                        context.toExpressionContext(rawExecution.raw(), null)
                ));
                Boolean branchResult = "condition".equals(nodeType) ? castBoolean(rawExecution.raw()) : null;
                attemptDetails.add(new FluxionNodeAttemptDetail(attempt, "SUCCESS", null, null, durationMs));
                return new FluxionNodeExecutionResult(
                        nodeId,
                        nodeType,
                        "SUCCESS",
                        output,
                        null,
                        null,
                        null,
                        branchResult,
                        attempt,
                        totalDurationMs,
                        List.copyOf(attemptDetails)
                );
            } catch (RuntimeException exception) {
                int durationMs = exception instanceof NodeTimeoutException timeoutException
                        ? timeoutException.durationMs()
                        : Math.max(elapsedMillis(startedAt), 0);
                totalDurationMs += durationMs;
                lastErrorCode = classifyError(nodeType, exception);
                lastErrorMessage = exception.getMessage();
                attemptDetails.add(new FluxionNodeAttemptDetail(
                        attempt,
                        "FAILED",
                        lastErrorCode,
                        lastErrorMessage,
                        durationMs
                ));
                if (attempt >= maxAttempts) {
                    return new FluxionNodeExecutionResult(
                            nodeId,
                            nodeType,
                            "FAILED",
                            null,
                            lastErrorCode,
                            lastErrorMessage,
                            null,
                            null,
                            attempt,
                            totalDurationMs,
                            List.copyOf(attemptDetails)
                    );
                }
            }
        }

        return new FluxionNodeExecutionResult(
                nodeId,
                nodeType,
                "FAILED",
                null,
                lastErrorCode,
                lastErrorMessage,
                null,
                null,
                maxAttempts,
                totalDurationMs,
                List.copyOf(attemptDetails)
        );
    }

    private NodeRawExecution executeRaw(
            Map<String, Object> node,
            ExecutionContext context,
            Object resolvedInput,
            int attempt
    ) {
        String nodeType = String.valueOf(node.get("nodeType"));
        Map<String, Object> config = castMap(node.get("config"));
        return switch (nodeType) {
            case "log" -> new NodeRawExecution(mapOf("logged", resolvedInput != null ? resolvedInput : config.get("message")), 0);
            case "condition" -> {
                Object value = evaluator.evaluate(config.get("conditionExpr"), context.toExpressionContext(null, null));
                if (value == null && Boolean.TRUE.equals(config.get("nullAsFalse"))) {
                    value = false;
                }
                yield new NodeRawExecution(Boolean.TRUE.equals(value), 0);
            }
            case "dbUpdate" -> executeDbUpdate(config, context);
            case "dbQuery" -> executeDbQuery(config, context);
            case "http" -> executeHttp(config, node, context, attempt);
            case "variable" -> executeVariable(config, context);
            default -> throw new IllegalStateException("Unsupported nodeType=" + nodeType);
        };
    }

    private NodeRawExecution executeVariable(Map<String, Object> config, ExecutionContext context) {
        String mode = String.valueOf(config.get("mode"));
        String targetVar = String.valueOf(config.get("targetVar"));
        Object value = "SET".equals(mode)
                ? evaluator.evaluate(config.get("valueExpr"), context.toExpressionContext(null, null))
                : evaluator.evaluate(config.get("transformExpr"), context.toExpressionContext(null, null));
        context.vars().put(targetVar, value);
        return new NodeRawExecution(mapOf("value", value), 0);
    }

    private NodeRawExecution executeHttp(
            Map<String, Object> config,
            Map<String, Object> node,
            ExecutionContext context,
            int attempt
    ) {
        String resourceRef = nullableString(config.get("resourceRef"));
        if (resourceRef != null) {
            context.acquirePermit(resourceRef);
        }
        Map<String, Object> httpResources = requireMap(context.resources().get("http"), "resources.http");
        Map<String, Object> mock = resolveAttemptResource(castMap(httpResources.get(String.valueOf(node.get("nodeId")))), attempt);
        int expectedStatus = intValue(config.get("expectedStatus"), 200);
        int actualStatus = intValue(mock == null ? null : mock.get("status"), expectedStatus);
        if (mock != null && mock.get("raise") != null) {
            throw new IllegalStateException(String.valueOf(mock.get("raise")));
        }
        if (actualStatus != expectedStatus) {
            throw new IllegalStateException(nullableString(mock == null ? null : mock.get("errorMessage")) != null
                    ? nullableString(mock.get("errorMessage"))
                    : "unexpected http status");
        }
        return new NodeRawExecution(
                mapOf("status", actualStatus, "body", mock == null ? null : mock.get("body")),
                intValue(mock == null ? null : mock.get("delayMs"), 0)
        );
    }

    private NodeRawExecution executeDbUpdate(Map<String, Object> config, ExecutionContext context) {
        String resourceRef = String.valueOf(config.get("resourceRef"));
        context.acquirePermit(resourceRef);
        InMemoryDatabase database = context.database(resourceRef);
        String sql = String.valueOf(config.get("sql"));
        Map<String, Object> params = resolveSqlParams(config, context);
        Matcher insertMatcher = INSERT_PATTERN.matcher(sql);
        if (insertMatcher.matches()) {
            String table = insertMatcher.group(1);
            List<String> columns = InMemoryDatabase.splitCsv(insertMatcher.group(2)).stream().map(String::trim).toList();
            List<String> values = InMemoryDatabase.splitCsv(insertMatcher.group(3)).stream().map(String::trim).toList();
            if (columns.size() != values.size()) {
                throw new IllegalStateException("dbUpdate insert columns/values mismatch: " + sql);
            }
            LinkedHashMap<String, Object> insertValues = new LinkedHashMap<>();
            for (int index = 0; index < columns.size(); index++) {
                insertValues.put(columns.get(index), resolveSqlToken(values.get(index), params));
            }
            long id = database.insert(table, insertValues);
            return new NodeRawExecution(mapOf("rowsAffected", 1, "lastRowId", id), 0);
        }
        Matcher updateMatcher = UPDATE_PATTERN.matcher(sql);
        if (updateMatcher.matches()) {
            String table = updateMatcher.group(1);
            Map<String, Object> assignments = parseAssignments(updateMatcher.group(2), params);
            List<List<PredicateClause>> conditions = parseWhereGroups(updateMatcher.group(3), params);
            int rowsAffected = database.update(table, assignments, conditions);
            return new NodeRawExecution(mapOf("rowsAffected", rowsAffected, "lastRowId", null), 0);
        }
        throw new IllegalStateException("unsupported dbUpdate sql: " + sql);
    }

    private NodeRawExecution executeDbQuery(Map<String, Object> config, ExecutionContext context) {
        String resourceRef = String.valueOf(config.get("resourceRef"));
        context.acquirePermit(resourceRef);
        InMemoryDatabase database = context.database(resourceRef);
        String sql = String.valueOf(config.get("sql"));
        Matcher matcher = SELECT_PATTERN.matcher(sql);
        if (!matcher.matches()) {
            throw new IllegalStateException("unsupported dbQuery sql: " + sql);
        }
        List<String> selectedColumns = InMemoryDatabase.splitCsv(matcher.group(1)).stream()
                .map(String::trim)
                .toList();
        String table = matcher.group(2);
        Map<String, Object> params = resolveSqlParams(config, context);
        List<List<PredicateClause>> whereGroups = parseWhereGroups(matcher.group(3), params);
        List<OrderSpec> orderBy = parseOrderBy(matcher.group(4));
        Integer limit = parseLimit(matcher.group(5), params);
        Map<String, Object> row = database.selectOne(table, selectedColumns, whereGroups, orderBy, limit);
        return new NodeRawExecution(row == null ? null : row, 0);
    }

    private Map<String, Object> resolveSqlParams(Map<String, Object> config, ExecutionContext context) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map<String, Object> entry : castListOfMaps(config.get("params"))) {
            resolved.put(String.valueOf(entry.get("key")), evaluator.evaluate(entry.get("value"), context.toExpressionContext(null, null)));
        }
        return resolved;
    }

    private Map<String, Object> parseAssignments(String assignmentsClause, Map<String, Object> params) {
        LinkedHashMap<String, Object> assignments = new LinkedHashMap<>();
        for (String assignment : InMemoryDatabase.splitCsv(assignmentsClause)) {
            String[] parts = assignment.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalStateException("unsupported assignment clause: " + assignment);
            }
            assignments.put(parts[0].trim(), resolveSqlToken(parts[1].trim(), params));
        }
        return assignments;
    }

    private List<List<PredicateClause>> parseWhereGroups(String whereClause, Map<String, Object> params) {
        if (whereClause == null || whereClause.isBlank()) {
            return List.of();
        }
        List<List<PredicateClause>> groups = new ArrayList<>();
        for (String orClause : splitByKeyword(whereClause, "or")) {
            List<PredicateClause> group = new ArrayList<>();
            for (String andClause : splitByKeyword(orClause, "and")) {
                group.add(parsePredicate(andClause.trim(), params));
            }
            groups.add(List.copyOf(group));
        }
        return List.copyOf(groups);
    }

    private PredicateClause parsePredicate(String predicate, Map<String, Object> params) {
        String normalized = predicate.trim();
        int inIndex = indexOfKeyword(normalized, "in");
        if (inIndex > 0) {
            String column = normalized.substring(0, inIndex).trim();
            String valuesClause = normalized.substring(inIndex + 2).trim();
            if (!valuesClause.startsWith("(") || !valuesClause.endsWith(")")) {
                throw new IllegalStateException("unsupported IN predicate: " + predicate);
            }
            List<Object> values = InMemoryDatabase.splitCsv(valuesClause.substring(1, valuesClause.length() - 1)).stream()
                    .map(String::trim)
                    .map(token -> resolveSqlToken(token, params))
                    .toList();
            return new PredicateClause(column, "IN", values);
        }
        String[] parts = normalized.split("=", 2);
        if (parts.length != 2) {
            throw new IllegalStateException("unsupported where clause: " + predicate);
        }
        return new PredicateClause(parts[0].trim(), "EQ", List.of(resolveSqlToken(parts[1].trim(), params)));
    }

    private List<OrderSpec> parseOrderBy(String orderByClause) {
        if (orderByClause == null || orderByClause.isBlank()) {
            return List.of();
        }
        List<OrderSpec> orderSpecs = new ArrayList<>();
        for (String entry : InMemoryDatabase.splitCsv(orderByClause)) {
            String[] parts = entry.trim().split("\\s+");
            boolean ascending = parts.length < 2 || !"desc".equalsIgnoreCase(parts[1]);
            orderSpecs.add(new OrderSpec(parts[0].trim(), ascending));
        }
        return List.copyOf(orderSpecs);
    }

    private Integer parseLimit(String limitToken, Map<String, Object> params) {
        if (limitToken == null || limitToken.isBlank()) {
            return null;
        }
        Object resolved = resolveSqlToken(limitToken.trim(), params);
        return resolved == null ? null : Integer.parseInt(String.valueOf(resolved));
    }

    private List<String> splitByKeyword(String input, String keyword) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inQuote = false;
        char quote = '\0';
        for (int index = 0; index < input.length(); index++) {
            char ch = input.charAt(index);
            if ((ch == '\'' || ch == '"') && (index == 0 || input.charAt(index - 1) != '\\')) {
                if (inQuote && ch == quote) {
                    inQuote = false;
                    quote = '\0';
                } else if (!inQuote) {
                    inQuote = true;
                    quote = ch;
                }
                current.append(ch);
                continue;
            }
            if (!inQuote) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                }
                if (depth == 0 && matchesKeywordAt(input, index, keyword)) {
                    parts.add(current.toString().trim());
                    current.setLength(0);
                    index += keyword.length() - 1;
                    continue;
                }
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    private int indexOfKeyword(String input, String keyword) {
        for (int index = 0; index <= input.length() - keyword.length(); index++) {
            if (matchesKeywordAt(input, index, keyword)) {
                return index;
            }
        }
        return -1;
    }

    private boolean matchesKeywordAt(String input, int index, String keyword) {
        if (index < 0 || index + keyword.length() > input.length()) {
            return false;
        }
        if (!input.regionMatches(true, index, keyword, 0, keyword.length())) {
            return false;
        }
        boolean leftOk = index == 0 || isKeywordBoundary(input.charAt(index - 1));
        boolean rightOk = index + keyword.length() >= input.length()
                || isKeywordBoundary(input.charAt(index + keyword.length()));
        return leftOk && rightOk;
    }

    private boolean isKeywordBoundary(char ch) {
        return !Character.isLetterOrDigit(ch) && ch != '_';
    }

    private Object resolveSqlToken(String token, Map<String, Object> params) {
        if (token.startsWith(":")) {
            return params.get(token.substring(1));
        }
        return InMemoryDatabase.parseLiteral(token);
    }

    private boolean isStartNode(String nodeId, List<String> startNodeIds) {
        return startNodeIds.contains(nodeId);
    }

    private boolean isNodeActivated(
            List<Map<String, Object>> incomingEdges,
            Map<String, Map<String, Object>> nodeMap,
            Map<String, FluxionNodeExecutionResult> results
    ) {
        if (incomingEdges == null || incomingEdges.isEmpty()) {
            return false;
        }
        boolean activated = false;
        for (Map<String, Object> edge : incomingEdges) {
            String sourceNodeId = String.valueOf(edge.get("sourceNodeId"));
            FluxionNodeExecutionResult upstream = results.get(sourceNodeId);
            if (upstream == null || !"SUCCESS".equals(upstream.status())) {
                continue;
            }
            String sourceType = String.valueOf(nodeMap.get(sourceNodeId).get("nodeType"));
            String branchKey = String.valueOf(edge.get("branchKey"));
            if ("condition".equals(sourceType)) {
                String expected = Boolean.TRUE.equals(upstream.branchResult()) ? "true" : "false";
                if (Objects.equals(branchKey, expected)) {
                    activated = true;
                }
            } else if ("default".equals(branchKey)) {
                activated = true;
            }
        }
        return activated;
    }

    private String inferSkipReason(
            List<Map<String, Object>> incomingEdges,
            Map<String, Map<String, Object>> nodeMap,
            Map<String, FluxionNodeExecutionResult> results
    ) {
        if (incomingEdges == null || incomingEdges.isEmpty()) {
            return "ALL_UPSTREAM_PATHS_INVALIDATED";
        }
        for (Map<String, Object> edge : incomingEdges) {
            String sourceNodeId = String.valueOf(edge.get("sourceNodeId"));
            FluxionNodeExecutionResult upstream = results.get(sourceNodeId);
            if (upstream != null
                    && "condition".equals(String.valueOf(nodeMap.get(sourceNodeId).get("nodeType")))
                    && "SUCCESS".equals(upstream.status())) {
                return "BRANCH_NOT_MATCHED";
            }
        }
        return "ALL_UPSTREAM_PATHS_INVALIDATED";
    }

    private String classifyError(String nodeType, RuntimeException exception) {
        if (exception instanceof ResourcePermitExhaustedException) {
            return "RESOURCE_PERMIT_EXHAUSTED";
        }
        if (exception instanceof NodeTimeoutException) {
            return "NODE_TIMEOUT";
        }
        return switch (nodeType) {
            case "http" -> "HTTP_CALL_FAILED";
            case "dbUpdate" -> "DB_UPDATE_FAILED";
            case "dbQuery" -> "DB_QUERY_FAILED";
            default -> "INTERNAL_ERROR";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value == null ? null : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castListOfMaps(Object value) {
        if (value == null) {
            return List.of();
        }
        return ((Collection<?>) value).stream()
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private List<String> castStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        return ((Collection<?>) value).stream().map(String::valueOf).toList();
    }

    private int intValue(Object value, int defaultValue) {
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Boolean castBoolean(Object value) {
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private int elapsedMillis(long startedAt) {
        return (int) ((System.nanoTime() - startedAt) / 1_000_000L);
    }

    private LinkedHashMap<String, Object> mapOf(Object... values) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    private Map<String, Object> resolveAttemptResource(Map<String, Object> resource, int attempt) {
        if (resource == null) {
            return null;
        }
        Object attemptEntries = resource.get("attempts");
        if (!(attemptEntries instanceof List<?> entries) || entries.isEmpty()) {
            return resource;
        }
        Object selected = entries.get(Math.min(attempt - 1, entries.size() - 1));
        if (!(selected instanceof Map<?, ?> selectedMap)) {
            return resource;
        }
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(resource);
        for (Map.Entry<?, ?> entry : selectedMap.entrySet()) {
            merged.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return merged;
    }

    private record NodeRawExecution(Object raw, int durationMs) {
    }

    private static final class NodeTimeoutException extends RuntimeException {
        private final int durationMs;

        private NodeTimeoutException(String message, int durationMs) {
            super(message);
            this.durationMs = durationMs;
        }

        private int durationMs() {
            return durationMs;
        }
    }

    private static final class ResourcePermitExhaustedException extends RuntimeException {
        private ResourcePermitExhaustedException(String message) {
            super(message);
        }
    }

    private static final class ExecutionContext {
        private final Map<String, Object> request;
        private final Map<String, Object> schedule;
        private final Map<String, Object> vars;
        private final Map<String, Object> instance;
        private final Map<String, Object> nodes;
        private final Map<String, Object> resources;
        private final Map<String, InMemoryDatabase> databases;
        private final Map<String, Integer> permitCursor;

        private ExecutionContext(Map<String, Object> trigger, Map<String, Object> resources, Map<String, Object> model) {
            Map<String, Object> triggerMap = trigger == null ? Map.of() : trigger;
            this.request = mutableMap(cast(triggerMap.get("request")));
            this.schedule = mutableMap(cast(triggerMap.get("schedule")));
            this.vars = mutableMap(cast(triggerMap.get("vars")));
            this.instance = new LinkedHashMap<>();
            this.instance.put("instanceId", 1L);
            this.instance.put("flowCode", String.valueOf(model.get("flowCode")));
            this.instance.put("status", "CREATED");
            this.instance.put("businessKey", triggerMap.get("businessKey"));
            this.instance.put("errorCode", null);
            this.instance.put("errorMessage", null);
            this.nodes = new LinkedHashMap<>();
            this.resources = resources == null ? Map.of() : resources;
            this.databases = new LinkedHashMap<>();
            this.permitCursor = new LinkedHashMap<>();
            Map<String, Object> dbSchemas = cast(this.resources.get("dbSchemas"));
            if (dbSchemas != null) {
                for (Map.Entry<String, Object> entry : dbSchemas.entrySet()) {
                    databases.put(entry.getKey(), InMemoryDatabase.fromSchema(String.valueOf(entry.getValue())));
                }
            }
            Map<String, Object> dbSeeds = cast(this.resources.get("dbSeeds"));
            if (dbSeeds != null) {
                for (Map.Entry<String, Object> entry : dbSeeds.entrySet()) {
                    InMemoryDatabase database = databases.get(entry.getKey());
                    if (database != null) {
                        database.seed(String.valueOf(entry.getValue()));
                    }
                }
            }
        }

        private Map<String, Object> toExpressionContext(Object raw, Object flowOutput) {
            LinkedHashMap<String, Object> context = new LinkedHashMap<>();
            context.put("request", request);
            context.put("schedule", schedule);
            context.put("vars", vars);
            context.put("instance", instance);
            context.put("nodes", nodes);
            context.put("raw", raw);
            context.put("flow", mapOf("output", flowOutput));
            return context;
        }

        private void putNode(String nodeId, FluxionNodeExecutionResult result) {
            LinkedHashMap<String, Object> node = new LinkedHashMap<>();
            node.put("status", result.status());
            node.put("output", result.output());
            nodes.put(nodeId, node);
        }

        private void acquirePermit(String resourceRef) {
            Map<String, Object> permitResources = cast(resources.get("resourcePermits"));
            if (permitResources == null) {
                return;
            }
            Map<String, Object> permit = cast(permitResources.get(resourceRef));
            if (permit == null) {
                return;
            }
            Object sequenceValue = permit.get("sequence");
            boolean allowed;
            if (sequenceValue instanceof List<?> sequence && !sequence.isEmpty()) {
                int index = permitCursor.getOrDefault(resourceRef, 0);
                Object decision = sequence.get(Math.min(index, sequence.size() - 1));
                permitCursor.put(resourceRef, index + 1);
                allowed = Boolean.parseBoolean(String.valueOf(decision));
            } else {
                allowed = !permit.containsKey("available") || Boolean.parseBoolean(String.valueOf(permit.get("available")));
            }
            if (!allowed) {
                throw new ResourcePermitExhaustedException("resource permit exhausted for " + resourceRef);
            }
        }

        private InMemoryDatabase database(String resourceRef) {
            return Optional.ofNullable(databases.get(resourceRef))
                    .orElseThrow(() -> new IllegalStateException("missing db resource: " + resourceRef));
        }

        private Map<String, Object> vars() {
            return vars;
        }

        private Map<String, Object> instance() {
            return instance;
        }

        private Map<String, Object> resources() {
            return resources;
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> cast(Object value) {
            return value == null ? null : (Map<String, Object>) value;
        }

        private static Map<String, Object> mutableMap(Map<String, Object> value) {
            return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
        }

        private static LinkedHashMap<String, Object> mapOf(Object... values) {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            for (int index = 0; index < values.length; index += 2) {
                map.put(String.valueOf(values[index]), values[index + 1]);
            }
            return map;
        }
    }

    private static final class InMemoryDatabase {
        private static final Pattern INSERT_VALUES_PATTERN = Pattern.compile(
                "insert\\s+into\\s+(\\w+)\\s*\\(([^)]+)\\)\\s*values\\s*(.+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        private final String tableName;
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private long sequence = 1L;

        private InMemoryDatabase(String tableName) {
            this.tableName = tableName;
        }

        private static InMemoryDatabase fromSchema(String schemaSql) {
            String normalized = schemaSql.toLowerCase(Locale.ROOT);
            int tableIndex = normalized.indexOf("table");
            String remainder = normalized.substring(tableIndex + "table".length()).trim();
            String tableName = remainder.split("[\\s(]")[0];
            return new InMemoryDatabase(tableName);
        }

        private void seed(String seedSql) {
            for (String statement : seedSql.split(";")) {
                String sql = statement.trim();
                if (sql.isEmpty()) {
                    continue;
                }
                Matcher matcher = INSERT_VALUES_PATTERN.matcher(sql);
                if (matcher.matches()) {
                    String table = matcher.group(1);
                    List<String> columns = splitCsv(matcher.group(2));
                    for (String tuple : extractTuples(matcher.group(3))) {
                        List<String> values = splitCsv(tuple);
                        if (values.size() != columns.size()) {
                            throw new IllegalStateException("seed column/value count mismatch: " + sql);
                        }
                        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                        for (int index = 0; index < columns.size(); index++) {
                            row.put(columns.get(index).trim(), parseLiteral(values.get(index).trim()));
                        }
                        insert(table, row);
                    }
                    continue;
                }
                Matcher updateMatcher = UPDATE_PATTERN.matcher(sql);
                if (updateMatcher.matches()) {
                    String table = updateMatcher.group(1);
                    Map<String, Object> assignments = parseAssignments(updateMatcher.group(2));
                    List<List<PredicateClause>> conditions = parseWhereGroups(updateMatcher.group(3));
                    update(table, assignments, conditions);
                    continue;
                }
                throw new IllegalStateException("unsupported db seed sql: " + sql);
            }
        }

        private long insert(String table, Map<String, Object> values) {
            validateTable(table);
            LinkedHashMap<String, Object> row = new LinkedHashMap<>(values);
            if (!row.containsKey("id")) {
                row.put("id", sequence++);
            } else {
                long explicitId = Long.parseLong(String.valueOf(row.get("id")));
                sequence = Math.max(sequence, explicitId + 1);
            }
            rows.add(row);
            return Long.parseLong(String.valueOf(row.get("id")));
        }

        private int update(String table, Map<String, Object> assignments, List<List<PredicateClause>> conditions) {
            validateTable(table);
            int updated = 0;
            for (Map<String, Object> row : rows) {
                if (matches(row, conditions)) {
                    row.putAll(assignments);
                    updated++;
                }
            }
            return updated;
        }

        private Map<String, Object> selectOne(
                String table,
                List<String> selectedColumns,
                List<List<PredicateClause>> conditions,
                List<OrderSpec> orderBy,
                Integer limit
        ) {
            validateTable(table);
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (matches(row, conditions)) {
                    filtered.add(row);
                }
            }
            if (!orderBy.isEmpty()) {
                filtered.sort((left, right) -> compareRows(left, right, orderBy));
            }
            int effectiveLimit = limit == null ? filtered.size() : Math.min(limit, filtered.size());
            if (effectiveLimit == 0 || filtered.isEmpty()) {
                return null;
            }
            return projectRow(filtered.get(0), selectedColumns);
        }

        private int compareRows(Map<String, Object> left, Map<String, Object> right, List<OrderSpec> orderBy) {
            for (OrderSpec spec : orderBy) {
                int comparison = compareValues(left.get(spec.column()), right.get(spec.column()));
                if (comparison != 0) {
                    return spec.ascending() ? comparison : -comparison;
                }
            }
            return 0;
        }

        private LinkedHashMap<String, Object> projectRow(Map<String, Object> row, List<String> selectedColumns) {
            LinkedHashMap<String, Object> selected = new LinkedHashMap<>();
            if (selectedColumns.size() == 1 && "*".equals(selectedColumns.get(0))) {
                selected.putAll(row);
                return selected;
            }
            for (String selectedColumn : selectedColumns) {
                selected.put(selectedColumn, row.get(selectedColumn));
            }
            return selected;
        }

        private boolean matches(Map<String, Object> row, List<List<PredicateClause>> conditions) {
            if (conditions.isEmpty()) {
                return true;
            }
            for (List<PredicateClause> group : conditions) {
                boolean groupMatched = true;
                for (PredicateClause predicate : group) {
                    Object actual = row.get(predicate.column());
                    if ("IN".equals(predicate.operator())) {
                        if (!predicate.values().contains(actual)) {
                            groupMatched = false;
                            break;
                        }
                    } else if (!Objects.equals(actual, predicate.values().get(0))) {
                        groupMatched = false;
                        break;
                    }
                }
                if (groupMatched) {
                    return true;
                }
            }
            return false;
        }

        private void validateTable(String table) {
            if (!Objects.equals(tableName, table.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("unknown table: " + table);
            }
        }

        private static List<String> extractTuples(String valuesClause) {
            List<String> tuples = new ArrayList<>();
            int depth = 0;
            boolean inQuote = false;
            char quote = '\0';
            int tupleStart = -1;
            for (int index = 0; index < valuesClause.length(); index++) {
                char current = valuesClause.charAt(index);
                if ((current == '\'' || current == '"') && (index == 0 || valuesClause.charAt(index - 1) != '\\')) {
                    if (inQuote && current == quote) {
                        inQuote = false;
                        quote = '\0';
                    } else if (!inQuote) {
                        inQuote = true;
                        quote = current;
                    }
                }
                if (inQuote) {
                    continue;
                }
                if (current == '(') {
                    if (depth == 0) {
                        tupleStart = index + 1;
                    }
                    depth++;
                } else if (current == ')') {
                    depth--;
                    if (depth == 0 && tupleStart >= 0) {
                        tuples.add(valuesClause.substring(tupleStart, index));
                        tupleStart = -1;
                    }
                }
            }
            return tuples;
        }

        static List<String> splitCsv(String input) {
            List<String> parts = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuote = false;
            char quote = '\0';
            for (int index = 0; index < input.length(); index++) {
                char ch = input.charAt(index);
                if ((ch == '\'' || ch == '"') && (index == 0 || input.charAt(index - 1) != '\\')) {
                    if (inQuote && ch == quote) {
                        inQuote = false;
                        quote = '\0';
                    } else if (!inQuote) {
                        inQuote = true;
                        quote = ch;
                    }
                    current.append(ch);
                    continue;
                }
                if (ch == ',' && !inQuote) {
                    parts.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
                current.append(ch);
            }
            if (!current.isEmpty()) {
                parts.add(current.toString().trim());
            }
            return parts;
        }

        static Object parseLiteral(String literal) {
            String normalized = literal.trim();
            if ((normalized.startsWith("'") && normalized.endsWith("'"))
                    || (normalized.startsWith("\"") && normalized.endsWith("\""))) {
                return normalized.substring(1, normalized.length() - 1)
                        .replace("\\'", "'")
                        .replace("\\\"", "\"");
            }
            if ("null".equalsIgnoreCase(normalized)) {
                return null;
            }
            if ("true".equalsIgnoreCase(normalized)) {
                return true;
            }
            if ("false".equalsIgnoreCase(normalized)) {
                return false;
            }
            try {
                return Integer.parseInt(normalized);
            } catch (NumberFormatException ignored) {
                return normalized;
            }
        }

        private static Map<String, Object> parseAssignments(String clause) {
            LinkedHashMap<String, Object> assignments = new LinkedHashMap<>();
            for (String assignment : splitCsv(clause)) {
                String[] parts = assignment.split("=", 2);
                if (parts.length != 2) {
                    throw new IllegalStateException("unsupported assignment clause: " + assignment);
                }
                assignments.put(parts[0].trim(), parseLiteral(parts[1].trim()));
            }
            return assignments;
        }

        private static List<List<PredicateClause>> parseWhereGroups(String whereClause) {
            if (whereClause == null || whereClause.isBlank()) {
                return List.of();
            }
            List<List<PredicateClause>> groups = new ArrayList<>();
            for (String orClause : splitByKeyword(whereClause, "or")) {
                List<PredicateClause> group = new ArrayList<>();
                for (String andClause : splitByKeyword(orClause, "and")) {
                    group.add(parsePredicate(andClause.trim()));
                }
                groups.add(List.copyOf(group));
            }
            return List.copyOf(groups);
        }

        private static PredicateClause parsePredicate(String predicate) {
            String normalized = predicate.trim();
            int inIndex = indexOfKeyword(normalized, "in");
            if (inIndex > 0) {
                String column = normalized.substring(0, inIndex).trim();
                String valuesClause = normalized.substring(inIndex + 2).trim();
                if (!valuesClause.startsWith("(") || !valuesClause.endsWith(")")) {
                    throw new IllegalStateException("unsupported IN predicate: " + predicate);
                }
                List<Object> values = splitCsv(valuesClause.substring(1, valuesClause.length() - 1)).stream()
                        .map(String::trim)
                        .map(InMemoryDatabase::parseLiteral)
                        .toList();
                return new PredicateClause(column, "IN", values);
            }
            String[] parts = normalized.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalStateException("unsupported where clause: " + predicate);
            }
            return new PredicateClause(parts[0].trim(), "EQ", List.of(parseLiteral(parts[1].trim())));
        }

        private static List<String> splitByKeyword(String input, String keyword) {
            List<String> parts = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            int depth = 0;
            boolean inQuote = false;
            char quote = '\0';
            for (int index = 0; index < input.length(); index++) {
                char ch = input.charAt(index);
                if ((ch == '\'' || ch == '"') && (index == 0 || input.charAt(index - 1) != '\\')) {
                    if (inQuote && ch == quote) {
                        inQuote = false;
                        quote = '\0';
                    } else if (!inQuote) {
                        inQuote = true;
                        quote = ch;
                    }
                    current.append(ch);
                    continue;
                }
                if (!inQuote) {
                    if (ch == '(') {
                        depth++;
                    } else if (ch == ')') {
                        depth--;
                    }
                    if (depth == 0 && matchesKeywordAt(input, index, keyword)) {
                        parts.add(current.toString().trim());
                        current.setLength(0);
                        index += keyword.length() - 1;
                        continue;
                    }
                }
                current.append(ch);
            }
            if (!current.isEmpty()) {
                parts.add(current.toString().trim());
            }
            return parts;
        }

        private static int indexOfKeyword(String input, String keyword) {
            for (int index = 0; index <= input.length() - keyword.length(); index++) {
                if (matchesKeywordAt(input, index, keyword)) {
                    return index;
                }
            }
            return -1;
        }

        private static boolean matchesKeywordAt(String input, int index, String keyword) {
            if (index < 0 || index + keyword.length() > input.length()) {
                return false;
            }
            if (!input.regionMatches(true, index, keyword, 0, keyword.length())) {
                return false;
            }
            boolean leftOk = index == 0 || isKeywordBoundary(input.charAt(index - 1));
            boolean rightOk = index + keyword.length() >= input.length()
                    || isKeywordBoundary(input.charAt(index + keyword.length()));
            return leftOk && rightOk;
        }

        private static boolean isKeywordBoundary(char ch) {
            return !Character.isLetterOrDigit(ch) && ch != '_';
        }

        private static int compareValues(Object left, Object right) {
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return 1;
            }
            if (right == null) {
                return -1;
            }
            if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
                return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
            }
            return String.valueOf(left).compareTo(String.valueOf(right));
        }
    }

    private record PredicateClause(String column, String operator, List<Object> values) {
    }

    private record OrderSpec(String column, boolean ascending) {
    }
}
