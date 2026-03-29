from __future__ import annotations

import json
import sqlite3
import time
from dataclasses import dataclass
from pathlib import Path
from types import SimpleNamespace
from typing import Any, Dict, Iterable, Optional

from tools.harness.lib.model_contracts import derive_start_nodes, derive_topological_order


class AttrNamespace(SimpleNamespace):
    def __getattr__(self, name: str) -> Any:
        return None


def to_namespace(value: Any) -> Any:
    if isinstance(value, dict):
        return AttrNamespace(**{k: to_namespace(v) for k, v in value.items()})
    if isinstance(value, list):
        return [to_namespace(item) for item in value]
    return value


def from_namespace(value: Any) -> Any:
    if isinstance(value, SimpleNamespace):
        return {key: from_namespace(val) for key, val in vars(value).items()}
    if isinstance(value, list):
        return [from_namespace(item) for item in value]
    return value


def deep_get(data: dict, path: str) -> Any:
    current: Any = data
    for part in path.split("."):
        if isinstance(current, dict):
            current = current.get(part)
        else:
            current = getattr(current, part, None)
        if current is None:
            return None
    return current


def eval_expression(expr: Any, context: dict) -> Any:
    if not isinstance(expr, str):
        return expr
    stripped = expr.strip()
    if stripped.startswith("${") and stripped.endswith("}"):
        inner = stripped[2:-1].strip()
        namespace = {key: to_namespace(value) for key, value in context.items()}
        namespace.update({"True": True, "False": False, "None": None, "null": None})
        return from_namespace(eval(inner, {"__builtins__": {}}, namespace))
    if "${" not in expr:
        return expr
    result = expr
    while "${" in result:
        start = result.index("${")
        end = result.index("}", start)
        inner = result[start + 2 : end]
        value = eval_expression("${" + inner + "}", context)
        if isinstance(value, (dict, list)):
            replacement = json.dumps(value, ensure_ascii=False)
        else:
            replacement = "" if value is None else str(value)
        result = result[:start] + replacement + result[end + 1 :]
    return result


def eval_mapping(mapping: Any, context: dict) -> Any:
    if isinstance(mapping, dict):
        return {key: eval_mapping(value, context) for key, value in mapping.items()}
    if isinstance(mapping, list):
        return [eval_mapping(item, context) for item in mapping]
    return eval_expression(mapping, context)


@dataclass
class NodeRecord:
    node_id: str
    node_type: str
    status: str
    output: Any = None
    error_code: Optional[str] = None
    error_message: Optional[str] = None
    skip_reason: Optional[str] = None
    branch_result: Optional[bool] = None
    attempts: int = 0
    duration_ms: int = 0
    attempt_details: list[dict[str, Any]] | None = None


class ResourcePermitExhaustedError(RuntimeError):
    pass


class SqliteHarnessPersistence:
    def __init__(self, db_path: Path):
        self.db_path = db_path
        self.conn = sqlite3.connect(db_path)
        self.conn.row_factory = sqlite3.Row
        self.ensure_schema()

    def ensure_schema(self) -> None:
        self.conn.executescript(
            """
            create table if not exists flow_instances (
                instance_id integer primary key,
                flow_code text not null,
                status text not null,
                error_code text,
                error_message text,
                flow_output text,
                duration_ms integer
            );
            create table if not exists node_executions (
                instance_id integer not null,
                node_id text not null,
                node_type text not null,
                status text not null,
                duration_ms integer not null default 0,
                attempt_count integer not null default 0,
                skip_reason text,
                error_code text,
                error_message text,
                output_json text
            );
            create table if not exists node_execution_attempts (
                instance_id integer not null,
                node_id text not null,
                attempt_no integer not null,
                status text not null,
                duration_ms integer not null default 0,
                error_code text,
                error_message text
            );
            """
        )
        self.conn.commit()

    def save_instance(self, result: dict) -> None:
        self.conn.execute(
            """
            insert or replace into flow_instances(instance_id, flow_code, status, error_code, error_message, flow_output, duration_ms)
            values (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                result["instance"]["instanceId"],
                result["instance"]["flowCode"],
                result["instance"]["status"],
                result["instance"].get("errorCode"),
                result["instance"].get("errorMessage"),
                json.dumps(result.get("flowOutput"), ensure_ascii=False),
                result.get("durationMs", 0),
            ),
        )
        self.conn.execute("delete from node_executions where instance_id = ?", (result["instance"]["instanceId"],))
        self.conn.execute("delete from node_execution_attempts where instance_id = ?", (result["instance"]["instanceId"],))
        for record in result["nodeRecords"].values():
            self.conn.execute(
                """
                insert into node_executions(instance_id, node_id, node_type, status, duration_ms, attempt_count, skip_reason, error_code, error_message, output_json)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    result["instance"]["instanceId"],
                    record["nodeId"],
                    record["nodeType"],
                    record["status"],
                    record.get("durationMs", 0),
                    record.get("attempts", 0),
                    record.get("skipReason"),
                    record.get("errorCode"),
                    record.get("errorMessage"),
                    json.dumps(record.get("output"), ensure_ascii=False),
                ),
            )
            for attempt in record.get("attemptDetails", []):
                self.conn.execute(
                    """
                    insert into node_execution_attempts(instance_id, node_id, attempt_no, status, duration_ms, error_code, error_message)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        result["instance"]["instanceId"],
                        record["nodeId"],
                        attempt.get("attempt"),
                        attempt.get("status"),
                        attempt.get("durationMs", 0),
                        attempt.get("errorCode"),
                        attempt.get("errorMessage"),
                    ),
                )
        self.conn.commit()

    def fetch_all(self, table_name: str) -> list[dict]:
        rows = self.conn.execute(f"select * from {table_name} order by rowid").fetchall()
        return [dict(row) for row in rows]

    def close(self) -> None:
        self.conn.close()


def execute_reference_engine(
    model: dict,
    *,
    trigger: Optional[dict] = None,
    resources: Optional[dict] = None,
    persistence: Optional[SqliteHarnessPersistence] = None,
    instance_id: int = 1,
) -> dict:
    trigger = trigger or {}
    resources = resources or {}
    node_map = {node["nodeId"]: node for node in model["nodes"]}
    edges = list(model["edges"])
    incoming = {node_id: [] for node_id in node_map}
    outgoing = {node_id: [] for node_id in node_map}
    for edge in edges:
        incoming[edge["targetNodeId"]].append(edge)
        outgoing[edge["sourceNodeId"]].append(edge)
    node_order = [node["nodeId"] for node in model["nodes"]]
    derived_incoming = {node_id: [edge["sourceNodeId"] for edge in incoming[node_id]] for node_id in node_map}
    derived_outgoing = {node_id: [edge["targetNodeId"] for edge in outgoing[node_id]] for node_id in node_map}
    start_nodes = set(derive_start_nodes(node_order, derived_incoming))
    ordered_node_ids = derive_topological_order(node_order, derived_outgoing, derived_incoming)
    if len(ordered_node_ids) != len(node_order):
        raise ValueError("model graph must be acyclic for reference execution")

    state = {
        "request": trigger.get("request", {}),
        "schedule": trigger.get("schedule", {}),
        "vars": build_initial_vars(model.get("variables", []), trigger.get("vars", {})),
        "instance": {
            "instanceId": instance_id,
            "flowCode": model["flowCode"],
            "flowName": model["flowName"],
            "triggerType": trigger.get("triggerType", "MANUAL"),
            "traceId": f"trace-{instance_id}",
            "businessKey": trigger.get("businessKey"),
            "status": "CREATED",
            "errorCode": None,
            "errorMessage": None,
        },
        "nodes": {},
    }
    records: Dict[str, NodeRecord] = {}
    flow_failed = False
    flow_output = None
    total_duration = 0

    for node_id in ordered_node_ids:
        if flow_failed:
            break
        node = node_map[node_id]
        if node_id in start_nodes:
            active = True
            skip_reason = None
        else:
            active_edges = []
            for edge in incoming[node_id]:
                source_record = records.get(edge["sourceNodeId"])
                if not source_record or source_record.status != "SUCCESS":
                    continue
                if node_map[edge["sourceNodeId"]]["nodeType"] == "condition":
                    expected_branch = "true" if source_record.branch_result else "false"
                    if edge["branchKey"] == expected_branch:
                        active_edges.append(edge)
                elif edge["branchKey"] == "default":
                    active_edges.append(edge)
            active = bool(active_edges)
            skip_reason = infer_skip_reason(incoming[node_id], records, node_map)
        if not active:
            record = NodeRecord(
                node_id=node_id,
                node_type=node["nodeType"],
                status="SKIPPED",
                skip_reason=skip_reason,
                attempt_details=[],
            )
            records[node_id] = record
            state["nodes"][node_id] = {"status": "SKIPPED"}
            continue

        execution = run_node(node=node, state=state, resources=resources)
        total_duration += execution["durationMs"]
        record = NodeRecord(
            node_id=node_id,
            node_type=node["nodeType"],
            status=execution["status"],
            output=execution.get("output"),
            error_code=execution.get("errorCode"),
            error_message=execution.get("errorMessage"),
            branch_result=execution.get("branchResult"),
            attempts=execution.get("attempts", 1),
            duration_ms=execution.get("durationMs", 0),
            attempt_details=execution.get("attemptDetails", []),
        )
        records[node_id] = record
        state["nodes"][node_id] = {
            "status": record.status,
            "output": record.output,
        }
        if record.status == "FAILED":
            flow_failed = True
            state["instance"]["status"] = "FAILED"
            state["instance"]["errorCode"] = record.error_code
            state["instance"]["errorMessage"] = record.error_message
            break

    if not flow_failed:
        try:
            flow_output = eval_mapping(
                model["flowOutputMapping"],
                {
                    **state,
                    "flow": {"output": None},
                },
            )
            state["instance"]["status"] = "SUCCESS"
        except Exception as exc:  # noqa: BLE001
            state["instance"]["status"] = "FAILED"
            state["instance"]["errorCode"] = "FLOW_OUTPUT_EVAL_FAILED"
            state["instance"]["errorMessage"] = str(exc)

    result = {
        "instance": state["instance"],
        "vars": state["vars"],
        "nodeRecords": {node_id: to_record_dict(record) for node_id, record in records.items()},
        "flowOutput": flow_output,
        "durationMs": total_duration,
    }
    if persistence:
        persistence.save_instance(result)
    return result


def build_initial_vars(variable_defs: list[dict], trigger_vars: Optional[dict]) -> dict:
    result: Dict[str, Any] = {}
    for item in variable_defs:
        if not isinstance(item, dict):
            continue
        name = item.get("name")
        if not isinstance(name, str) or not name:
            continue
        result[name] = item.get("defaultValue") if "defaultValue" in item else None
    for key, value in (trigger_vars or {}).items():
        result[key] = value
    return result


def infer_skip_reason(incoming_edges: Iterable[dict], records: Dict[str, NodeRecord], node_map: dict) -> str:
    if any(
        node_map[edge["sourceNodeId"]]["nodeType"] == "condition"
        and records.get(edge["sourceNodeId"])
        and records[edge["sourceNodeId"]].status == "SUCCESS"
        for edge in incoming_edges
    ):
        return "BRANCH_NOT_MATCHED"
    return "ALL_UPSTREAM_PATHS_INVALIDATED"


def run_node(*, node: dict, state: dict, resources: dict) -> dict:
    max_attempts = 1 + int(node["runtimePolicy"].get("retry", 0))
    timeout_ms = int(node["runtimePolicy"].get("timeoutMs", 3000))
    last_error_code = None
    last_error_message = None
    total_duration_ms = 0
    attempt_details: list[dict[str, Any]] = []
    for attempt in range(1, max_attempts + 1):
        base_context = {
            **state,
            "raw": None,
        }
        resolved_input = eval_mapping(node.get("inputMapping", {}), base_context)
        started = time.perf_counter()
        attempt_duration_ms = 0
        try:
            raw, simulated_duration = execute_node_raw(node, resolved_input, state, resources, attempt)
            actual_duration = max(int((time.perf_counter() - started) * 1000), simulated_duration)
            attempt_duration_ms = actual_duration
            if actual_duration > timeout_ms:
                raise TimeoutError(f"node exceeded timeoutMs={timeout_ms}")
            context = {
                **state,
                "raw": raw,
            }
            output = eval_mapping(node.get("outputMapping", {}), context)
            total_duration_ms += actual_duration
            attempt_details.append(
                {
                    "attempt": attempt,
                    "status": "SUCCESS",
                    "errorCode": None,
                    "errorMessage": None,
                    "durationMs": actual_duration,
                }
            )
            return {
                "status": "SUCCESS",
                "output": output,
                "attempts": attempt,
                "durationMs": total_duration_ms,
                "branchResult": raw if node["nodeType"] == "condition" else None,
                "attemptDetails": attempt_details,
            }
        except Exception as exc:  # noqa: BLE001
            if isinstance(exc, TimeoutError):
                duration_ms = max(attempt_duration_ms, timeout_ms)
            else:
                duration_ms = max(attempt_duration_ms, int((time.perf_counter() - started) * 1000), 0)
            total_duration_ms += duration_ms
            last_error_code = classify_error(node["nodeType"], exc)
            last_error_message = str(exc)
            attempt_details.append(
                {
                    "attempt": attempt,
                    "status": "FAILED",
                    "errorCode": last_error_code,
                    "errorMessage": last_error_message,
                    "durationMs": duration_ms,
                }
            )
            if attempt >= max_attempts:
                return {
                    "status": "FAILED",
                    "errorCode": last_error_code,
                    "errorMessage": last_error_message,
                    "attempts": attempt,
                    "durationMs": total_duration_ms,
                    "attemptDetails": attempt_details,
                }
    return {
        "status": "FAILED",
        "errorCode": last_error_code or "INTERNAL_ERROR",
        "errorMessage": last_error_message or "unknown failure",
        "attempts": max_attempts,
        "durationMs": total_duration_ms,
        "attemptDetails": attempt_details,
    }


def execute_node_raw(
    node: dict,
    resolved_input: dict,
    state: dict,
    resources: dict,
    attempt: int,
) -> tuple[Any, int]:
    node_type = node["nodeType"]
    if node_type == "log":
        return {"logged": resolved_input or node["config"]["message"]}, 0
    if node_type == "variable":
        config = node["config"]
        if config["mode"] == "SET":
            value = eval_expression(config["valueExpr"], state)
        else:
            value = eval_expression(config["transformExpr"], state)
        state["vars"][config["targetVar"]] = value
        return {"value": value}, 0
    if node_type == "condition":
        value = eval_expression(node["config"]["conditionExpr"], state)
        if value is None:
            value = False if node["config"].get("nullAsFalse", False) else None
        return bool(value), 0
    if node_type == "http":
        acquire_resource_permit(node, resources, attempt)
        mock = resolve_attempt_resource(resources.get("http", {}).get(node["nodeId"], {}), attempt)
        delay_ms = int(mock.get("delayMs", 0))
        if mock.get("raise"):
            raise RuntimeError(mock["raise"])
        if mock.get("status", node["config"].get("expectedStatus", 200)) != node["config"].get("expectedStatus", 200):
            raise RuntimeError(mock.get("errorMessage", "unexpected http status"))
        return {
            "status": mock.get("status", 200),
            "body": mock.get("body"),
        }, delay_ms
    if node_type in {"dbQuery", "dbUpdate"}:
        acquire_resource_permit(node, resources, attempt)
        db_resource = resources.get("db", {}).get(node["config"]["resourceRef"])
        if not db_resource:
            raise RuntimeError("missing db resource")
        conn = db_resource
        params = {
            item["key"]: eval_expression(item["value"], state)
            for item in node["config"].get("params", [])
        }
        cursor = conn.cursor()
        cursor.execute(node["config"]["sql"], params)
        if node_type == "dbQuery":
            rows = [dict(row) for row in cursor.fetchall()]
            if node["config"]["resultMode"] == "ONE":
                return (rows[0] if rows else None), 0
            return rows, 0
        conn.commit()
        return {
            "rowsAffected": cursor.rowcount,
            "lastRowId": cursor.lastrowid,
        }, 0
    raise RuntimeError(f"unsupported node type: {node_type}")


def resolve_attempt_resource(resource: dict, attempt: int) -> dict:
    if not isinstance(resource, dict):
        return {}
    attempts = resource.get("attempts")
    if isinstance(attempts, list) and attempts:
        index = min(attempt - 1, len(attempts) - 1)
        selected = attempts[index]
        if isinstance(selected, dict):
            return {**resource, **selected}
    return resource


def acquire_resource_permit(node: dict, resources: dict, attempt: int) -> None:
    resource_ref = node.get("config", {}).get("resourceRef")
    if not resource_ref:
        return
    permits = resources.get("resourcePermits", {})
    permit = permits.get(resource_ref)
    if not isinstance(permit, dict):
        return
    state = resources.setdefault("_resourcePermitState", {})
    sequence = permit.get("sequence")
    if isinstance(sequence, list) and sequence:
        cursor = int(state.get(resource_ref, 0))
        state[resource_ref] = cursor + 1
        index = min(cursor, len(sequence) - 1)
        allowed = bool(sequence[index])
    else:
        allowed = bool(permit.get("available", True))
    if not allowed:
        raise ResourcePermitExhaustedError(f"resource permit exhausted for {resource_ref}")


def classify_error(node_type: str, exc: Exception) -> str:
    if isinstance(exc, ResourcePermitExhaustedError):
        return "RESOURCE_PERMIT_EXHAUSTED"
    if isinstance(exc, TimeoutError):
        return "NODE_TIMEOUT"
    if node_type == "http":
        return "HTTP_CALL_FAILED"
    if node_type == "dbQuery":
        return "DB_QUERY_FAILED"
    if node_type == "dbUpdate":
        return "DB_UPDATE_FAILED"
    return "INTERNAL_ERROR"


def to_record_dict(record: NodeRecord) -> dict:
    return {
        "nodeId": record.node_id,
        "nodeType": record.node_type,
        "status": record.status,
        "output": record.output,
        "errorCode": record.error_code,
        "errorMessage": record.error_message,
        "skipReason": record.skip_reason,
        "attempts": record.attempts,
        "durationMs": record.duration_ms,
        "attemptDetails": record.attempt_details or [],
    }


def create_sqlite_resource(schema_sql: Optional[str] = None, seed_sql: Optional[str] = None) -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    if schema_sql:
        conn.executescript(schema_sql)
    if seed_sql:
        conn.executescript(seed_sql)
    conn.commit()
    return conn
