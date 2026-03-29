from __future__ import annotations

from collections import defaultdict, deque
import re
from typing import Any, Dict, Iterable, List, Set, Tuple


ALLOWED_NODE_TYPES = {"log", "variable", "condition", "http", "dbQuery", "dbUpdate"}
ALLOWED_VARIABLE_TYPES = {"STRING", "INT", "LONG", "DOUBLE", "BOOLEAN", "JSON"}
RUNTIME_POLICY_FIELDS = {"timeoutMs", "retry", "retryIntervalMs", "logEnabled"}
RUNTIME_POLICY_ALLOWED_FIELDS = RUNTIME_POLICY_FIELDS | {"extensions"}
NODE_REQUIRED_FIELDS = {
    "nodeId",
    "nodeType",
    "nodeName",
    "config",
    "inputMapping",
    "outputMapping",
    "runtimePolicy",
}
NODE_ALLOWED_FIELDS = NODE_REQUIRED_FIELDS | {"sideEffectPolicy", "extensions"}
TOP_LEVEL_REQUIRED_FIELDS = {
    "modelVersion",
    "flowDefId",
    "flowVersionId",
    "flowCode",
    "flowName",
    "variables",
    "flowOutputMapping",
    "nodes",
    "edges",
}
TOP_LEVEL_ALLOWED_FIELDS = TOP_LEVEL_REQUIRED_FIELDS | {"extensions"}
VARIABLE_ALLOWED_FIELDS = {"name", "type", "defaultValue", "description", "extensions"}
EDGE_REQUIRED_FIELDS = {"edgeId", "sourceNodeId", "targetNodeId", "branchKey"}
EDGE_ALLOWED_FIELDS = EDGE_REQUIRED_FIELDS | {"extensions"}
VAR_ROOT_PATTERN = re.compile(r"\bvars\.([A-Za-z_][A-Za-z0-9_]*)")
VAR_DYNAMIC_PATTERN = re.compile(r"\bvars\s*\[")


def validate_model_contract(model: dict) -> List[dict]:
    diagnostics: List[dict] = []

    def add(stage: str, error_code: str, message: str, *, field=None, node_id=None):
        diagnostics.append(
            {
                "stage": stage,
                "severity": "ERROR",
                "errorCode": error_code,
                "message": message,
                "field": field,
                "nodeId": node_id,
            }
        )

    missing_top = sorted(TOP_LEVEL_REQUIRED_FIELDS - set(model.keys()))
    for field in missing_top:
        add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", f"missing top-level field: {field}", field=field)
    unexpected_top = sorted(set(model.keys()) - TOP_LEVEL_ALLOWED_FIELDS)
    for field in unexpected_top:
        add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", f"unexpected top-level field: {field}", field=field)
    if missing_top:
        return diagnostics

    if model.get("modelVersion") != "1.0":
        add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", "modelVersion must be 1.0", field="modelVersion")

    flow_output = model.get("flowOutputMapping")
    if not isinstance(flow_output, dict) or not flow_output:
        add(
            "MODEL_COMPILE",
            "FLOW_OUTPUT_MAPPING_MISSING",
            "flowOutputMapping is required for published runtime model",
            field="flowOutputMapping",
        )

    variables = model.get("variables")
    nodes = model.get("nodes")
    edges = model.get("edges")

    declared_variables: Dict[str, dict] = {}

    if not isinstance(variables, list):
        add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", "variables must be an array", field="variables")
        return diagnostics
    if not isinstance(nodes, list) or not nodes:
        add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", "nodes must be a non-empty array", field="nodes")
        return diagnostics
    if not isinstance(edges, list):
        add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", "edges must be an array", field="edges")
        return diagnostics

    seen_variable_names: Set[str] = set()
    for item in variables:
        if not isinstance(item, dict):
            add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", "variable definition must be an object", field="variables")
            continue
        unexpected_fields = sorted(set(item.keys()) - VARIABLE_ALLOWED_FIELDS)
        for field in unexpected_fields:
            add(
                "SCHEMA_VALIDATE",
                "NODE_SCHEMA_INVALID",
                f"unexpected variable field: {field}",
                field=f"variables.{field}",
            )
        name = item.get("name")
        var_type = item.get("type")
        if not isinstance(name, str) or not name:
            add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", "variable name must be a non-empty string", field="variables.name")
            continue
        if name in seen_variable_names:
            add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", f"duplicate variable name: {name}", field="variables.name")
            continue
        seen_variable_names.add(name)
        declared_variables[name] = item
        if var_type not in ALLOWED_VARIABLE_TYPES:
            add(
                "SCHEMA_VALIDATE",
                "NODE_SCHEMA_INVALID",
                f"unsupported variable type: {var_type}",
                field="variables.type",
            )
        if "defaultValue" in item and not is_value_compatible(item.get("defaultValue"), var_type):
            add(
                "MODEL_COMPILE",
                "VARIABLE_DEFAULT_TYPE_MISMATCH",
                f"defaultValue is incompatible with variable type: {name}",
                field="variables.defaultValue",
            )

    node_map: Dict[str, dict] = {}
    duplicate_node_ids: Set[str] = set()
    for node in nodes:
        if not isinstance(node, dict):
            add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", "node must be an object", field="nodes")
            continue
        missing_fields = NODE_REQUIRED_FIELDS - set(node.keys())
        for field in sorted(missing_fields):
            add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", f"missing node field: {field}", field=field, node_id=node.get("nodeId"))
        unexpected_fields = sorted(set(node.keys()) - NODE_ALLOWED_FIELDS)
        for field in unexpected_fields:
            add(
                "SCHEMA_VALIDATE",
                "NODE_SCHEMA_INVALID",
                f"unexpected node field: {field}",
                field=field,
                node_id=node.get("nodeId"),
            )
        node_id = node.get("nodeId")
        if not isinstance(node_id, str) or not node_id:
            add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", "nodeId must be a non-empty string", field="nodeId")
            continue
        if node_id in node_map:
            duplicate_node_ids.add(node_id)
        node_map[node_id] = node
        node_type = node.get("nodeType")
        if node_type not in ALLOWED_NODE_TYPES:
            add(
                "SCHEMA_VALIDATE",
                "NODE_SCHEMA_INVALID",
                f"unsupported nodeType: {node_type}",
                field="nodeType",
                node_id=node_id,
            )
        runtime_policy = node.get("runtimePolicy")
        if not isinstance(runtime_policy, dict) or not RUNTIME_POLICY_FIELDS.issubset(runtime_policy.keys()):
            add(
                "SCHEMA_VALIDATE",
                "NODE_SCHEMA_INVALID",
                "runtimePolicy must contain timeoutMs, retry, retryIntervalMs, logEnabled",
                field="runtimePolicy",
                node_id=node_id,
            )
        elif set(runtime_policy.keys()) - RUNTIME_POLICY_ALLOWED_FIELDS:
            add(
                "SCHEMA_VALIDATE",
                "NODE_SCHEMA_INVALID",
                "runtimePolicy contains unsupported fields",
                field="runtimePolicy",
                node_id=node_id,
            )
        if node_type in {"http", "dbUpdate"} and "sideEffectPolicy" not in node:
            add(
                "SCHEMA_VALIDATE",
                "NODE_SCHEMA_INVALID",
                f"{node_type} node must declare sideEffectPolicy",
                field="sideEffectPolicy",
                node_id=node_id,
            )
        if node_type == "variable":
            target_var = (node.get("config") or {}).get("targetVar")
            if not isinstance(target_var, str) or target_var not in declared_variables:
                add(
                    "MODEL_COMPILE",
                    "VARIABLE_NOT_DECLARED",
                    f"variable targetVar is not declared: {target_var}",
                    field="config.targetVar",
                    node_id=node_id,
                )

    for node_id in sorted(duplicate_node_ids):
        add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", f"duplicate nodeId: {node_id}", field="nodeId", node_id=node_id)

    edge_ids: Set[str] = set()
    incoming: Dict[str, List[str]] = defaultdict(list)
    outgoing: Dict[str, List[str]] = defaultdict(list)
    reverse_adj: Dict[str, List[str]] = defaultdict(list)
    for edge in edges:
        if not isinstance(edge, dict):
            add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", "edge must be an object", field="edges")
            continue
        missing_fields = EDGE_REQUIRED_FIELDS - set(edge.keys())
        for field in sorted(missing_fields):
            add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", f"missing edge field: {field}", field=field)
        unexpected_fields = sorted(set(edge.keys()) - EDGE_ALLOWED_FIELDS)
        for field in unexpected_fields:
            add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", f"unexpected edge field: {field}", field=field)
        edge_id = edge.get("edgeId")
        source = edge.get("sourceNodeId")
        target = edge.get("targetNodeId")
        branch_key = edge.get("branchKey")
        if not isinstance(edge_id, str) or not edge_id:
            add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", "edgeId must be a non-empty string", field="edgeId")
            continue
        if edge_id in edge_ids:
            add("SCHEMA_VALIDATE", "NODE_SCHEMA_INVALID", f"duplicate edgeId: {edge_id}", field="edgeId")
        edge_ids.add(edge_id)
        if source not in node_map or target not in node_map:
            add(
                "SCHEMA_VALIDATE",
                "NODE_SCHEMA_INVALID",
                f"edge references missing node: {source}->{target}",
                field="edges",
            )
            continue
        source_type = node_map[source]["nodeType"]
        if source_type == "condition":
            if branch_key not in {"true", "false"}:
                add(
                    "STRUCTURE_VALIDATE",
                    "INVALID_CONDITION_BRANCH",
                    "condition edge branchKey must be true or false",
                    field="branchKey",
                    node_id=source,
                )
        elif branch_key != "default":
            add(
                "STRUCTURE_VALIDATE",
                "INVALID_CONDITION_BRANCH",
                "non-condition edge branchKey must be default",
                field="branchKey",
                node_id=source,
            )
        outgoing[source].append(target)
        incoming[target].append(source)
        reverse_adj[target].append(source)

    if not node_map:
        return diagnostics

    node_order = [node["nodeId"] for node in nodes if isinstance(node, dict) and isinstance(node.get("nodeId"), str)]
    computed_starts = derive_start_nodes(node_order, incoming)
    terminals = derive_terminal_nodes(node_order, outgoing)
    ordered_ids = derive_topological_order(node_order, outgoing, incoming)

    if not computed_starts:
        add("STRUCTURE_VALIDATE", "NO_START_NODE", "no start node exists in graph", field="nodes")
    if not terminals:
        add("STRUCTURE_VALIDATE", "NO_TERMINAL_NODE", "no terminal node exists in graph", field="nodes")

    if computed_starts:
        reachable = walk_graph(computed_starts, outgoing)
        for node_id in sorted(node_map.keys() - reachable):
            add(
                "STRUCTURE_VALIDATE",
                "UNREACHABLE_NODE",
                "node is unreachable from any computed start node",
                field="edges",
                node_id=node_id,
            )

    if terminals:
        reverse_reachable = walk_graph(terminals, reverse_adj)
        for node_id in sorted(node_map.keys() - reverse_reachable):
            add(
                "STRUCTURE_VALIDATE",
                "NODE_NOT_ON_START_TO_END_PATH",
                "node cannot reach any terminal node",
                field="edges",
                node_id=node_id,
            )

    if not is_weakly_connected(node_map.keys(), outgoing, incoming):
        add(
            "STRUCTURE_VALIDATE",
            "DISCONNECTED_SUBGRAPH",
            "graph contains disconnected subgraph or isolated node",
            field="edges",
        )

    if has_cycle(node_map.keys(), outgoing, incoming):
        add("STRUCTURE_VALIDATE", "CYCLIC_GRAPH", "graph contains a cycle", field="edges")

    for expr_field, expr, node_id in collect_expression_strings(model):
        if VAR_DYNAMIC_PATTERN.search(expr):
            add(
                "MODEL_COMPILE",
                "VARIABLE_DYNAMIC_ACCESS_NOT_ALLOWED",
                "dynamic variable access is not supported",
                field=expr_field,
                node_id=node_id,
            )
        for var_name in sorted(set(VAR_ROOT_PATTERN.findall(expr))):
            if var_name not in declared_variables:
                add(
                    "MODEL_COMPILE",
                    "VARIABLE_NOT_DECLARED",
                    f"variable is not declared: {var_name}",
                    field=expr_field,
                    node_id=node_id,
                )

    return deduplicate(diagnostics)


def walk_graph(start_nodes: List[str], graph: Dict[str, List[str]]) -> Set[str]:
    seen: Set[str] = set()
    queue = deque(start_nodes)
    while queue:
        current = queue.popleft()
        if current in seen:
            continue
        seen.add(current)
        for child in graph.get(current, []):
            if child not in seen:
                queue.append(child)
    return seen


def is_weakly_connected(node_ids, outgoing, incoming) -> bool:
    node_ids = list(node_ids)
    if not node_ids:
        return True
    visited = set()
    queue = deque([node_ids[0]])
    while queue:
        current = queue.popleft()
        if current in visited:
            continue
        visited.add(current)
        for nxt in outgoing.get(current, []) + incoming.get(current, []):
            if nxt not in visited:
                queue.append(nxt)
    return len(visited) == len(node_ids)


def has_cycle(node_ids, outgoing, incoming) -> bool:
    node_ids = list(node_ids)
    indegree = {node_id: len(incoming.get(node_id, [])) for node_id in node_ids}
    queue = deque([node_id for node_id, degree in indegree.items() if degree == 0])
    visited = 0
    while queue:
        current = queue.popleft()
        visited += 1
        for child in outgoing.get(current, []):
            indegree[child] -= 1
            if indegree[child] == 0:
                queue.append(child)
    return visited != len(node_ids)


def derive_start_nodes(node_ids: List[str], incoming: Dict[str, List[str]]) -> List[str]:
    return [node_id for node_id in node_ids if not incoming.get(node_id)]


def derive_terminal_nodes(node_ids: List[str], outgoing: Dict[str, List[str]]) -> List[str]:
    return [node_id for node_id in node_ids if not outgoing.get(node_id)]


def derive_topological_order(node_ids: List[str], outgoing: Dict[str, List[str]], incoming: Dict[str, List[str]]) -> List[str]:
    rank = {node_id: index for index, node_id in enumerate(node_ids)}
    indegree = {node_id: len(incoming.get(node_id, [])) for node_id in node_ids}
    ready = sorted([node_id for node_id, degree in indegree.items() if degree == 0], key=rank.get)
    ordered: List[str] = []
    while ready:
        current = ready.pop(0)
        ordered.append(current)
        for child in sorted(outgoing.get(current, []), key=rank.get):
            indegree[child] -= 1
            if indegree[child] == 0:
                ready.append(child)
                ready.sort(key=rank.get)
    return ordered


def collect_expression_strings(model: dict) -> List[Tuple[str, str, str | None]]:
    items: List[Tuple[str, str, str | None]] = []
    flow_output = model.get("flowOutputMapping")
    items.extend(collect_string_values(flow_output, "flowOutputMapping", None))
    for node in model.get("nodes", []):
        if not isinstance(node, dict):
            continue
        node_id = node.get("nodeId")
        for field in ("config", "inputMapping", "outputMapping"):
            items.extend(collect_string_values(node.get(field), field, node_id))
    return items


def collect_string_values(value: Any, field: str, node_id: str | None) -> List[Tuple[str, str, str | None]]:
    items: List[Tuple[str, str, str | None]] = []
    if isinstance(value, str):
        items.append((field, value, node_id))
    elif isinstance(value, dict):
        for key, item in value.items():
            items.extend(collect_string_values(item, f"{field}.{key}", node_id))
    elif isinstance(value, list):
        for index, item in enumerate(value):
            items.extend(collect_string_values(item, f"{field}[{index}]", node_id))
    return items


def is_value_compatible(value: Any, var_type: str | None) -> bool:
    if value is None:
        return True
    if var_type == "STRING":
        return isinstance(value, str)
    if var_type in {"INT", "LONG"}:
        return isinstance(value, int) and not isinstance(value, bool)
    if var_type == "DOUBLE":
        return (isinstance(value, int) and not isinstance(value, bool)) or isinstance(value, float)
    if var_type == "BOOLEAN":
        return isinstance(value, bool)
    if var_type == "JSON":
        return isinstance(value, (dict, list, str, int, float, bool)) or value is None
    return False


def deduplicate(diagnostics: List[dict]) -> List[dict]:
    seen: Set[Tuple[str, str, str, str]] = set()
    result: List[dict] = []
    for item in diagnostics:
        key = (
            item["stage"],
            item["errorCode"],
            item.get("field") or "",
            item.get("nodeId") or "",
        )
        if key in seen:
            continue
        seen.add(key)
        result.append(item)
    return result
