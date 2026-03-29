from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from xml.etree.ElementTree import Element, ElementTree, SubElement


REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from tools.harness.lib.diagnostics import enrich_diagnostic, write_diagnostics
from tools.harness.lib.reference_engine import SqliteHarnessPersistence, create_sqlite_resource, execute_reference_engine


OUTPUT_DIR = REPO_ROOT / ".artifacts" / "harness" / "persistence"


def load_cases(case_filter: str | None = None, suite_filter: str | None = None) -> list[dict]:
    cases = []
    for path in sorted((REPO_ROOT / "fixtures" / "persistence").rglob("*.case.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["_path"] = path
        if case_filter and payload["caseId"] != case_filter:
            continue
        if suite_filter and payload.get("suite", "persistence-integration") != suite_filter:
            continue
        cases.append(payload)
    return cases


def main() -> None:
    args = parse_args()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    loaded_cases = load_cases(case_filter=args.case, suite_filter=args.suite)
    if (args.case or args.suite) and not loaded_cases:
        raise SystemExit(f"no persistence cases matched case={args.case!r} suite={args.suite!r}")
    diagnostics = []
    cases = []
    for index, case in enumerate(loaded_cases, start=1):
        fixture_path = str(case["_path"].relative_to(REPO_ROOT)).replace("\\", "/")
        db_path = OUTPUT_DIR / f"{case['caseId'].replace('/', '_')}.sqlite"
        if db_path.exists():
            db_path.unlink()
        persistence = SqliteHarnessPersistence(db_path)
        resources, closers = materialize_resources(case.get("resources", {}))
        try:
            result = execute_reference_engine(
                case["model"],
                trigger=case.get("trigger", {}),
                resources=resources,
                persistence=persistence,
                instance_id=30000 + index,
            )
            instance_rows = persistence.fetch_all("flow_instances")
            node_rows = persistence.fetch_all("node_executions")
            attempt_rows = persistence.fetch_all("node_execution_attempts")
        finally:
            for closer in closers:
                closer()
            persistence.close()
        mismatches = compare_expectation(case["expect"], result, instance_rows, node_rows, attempt_rows)
        cases.append(
            {
                "caseId": case["caseId"],
                "suite": case.get("suite", "persistence-integration"),
                "description": case.get("description", ""),
                "fixturePath": fixture_path,
                "status": "passed" if not mismatches else "failed",
                "failureMessage": "; ".join(mismatches) if mismatches else None,
                "dbPath": str(db_path.relative_to(REPO_ROOT)).replace("\\", "/"),
            }
        )
        for mismatch in mismatches:
            diagnostics.append(
                enrich_diagnostic(
                    repo_root=REPO_ROOT,
                    tool="fluxion-persistence-mybatisplus",
                    suite=case.get("suite", "persistence-integration"),
                    case_id=case["caseId"],
                    stage="PERSISTENCE",
                    severity="ERROR",
                    error_code=result["instance"].get("errorCode") or "INTERNAL_ERROR",
                    message=mismatch,
                    module="fluxion-persistence-mybatisplus",
                    suggested_command=f"python tools/harness/run_persistence_cases.py --case {case['caseId']}",
                    candidate_files=["fluxion-persistence-mybatisplus", fixture_path, "docs/schema-pg.sql"],
                    related_fixture=fixture_path,
                    source_path="docs/schema-pg.sql",
                    line_hint="flx_node_execution_attempt",
                    attempt_summary=build_attempt_summary(result),
                )
            )
    results = {
        "suite": "persistence-integration",
        "tool": "fluxion-persistence-mybatisplus",
        "cases": cases,
        "stats": {
            "total": len(cases),
            "passed": sum(1 for item in cases if item["status"] == "passed"),
            "failed": sum(1 for item in cases if item["status"] == "failed"),
            "skipped": 0,
        },
    }
    (OUTPUT_DIR / "results.json").write_text(
        json.dumps(results, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    write_diagnostics(OUTPUT_DIR / "diagnostics.json", diagnostics)
    write_junit(OUTPUT_DIR / "junit.xml", results)
    print(f"persistence integration suite finished: {results['stats']}")
    if results["stats"]["failed"]:
        raise SystemExit(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Fluxion persistence integration fixtures.")
    parser.add_argument("--case", default=None)
    parser.add_argument("--suite", default=None)
    return parser.parse_args()


def materialize_resources(resource_config: dict) -> tuple[dict, list]:
    resources = {
        "http": resource_config.get("http", {}),
        "db": {},
        "resourcePermits": resource_config.get("resourcePermits", {}),
    }
    closers = []
    for resource_ref, schema_sql in resource_config.get("dbSchemas", {}).items():
        conn = create_sqlite_resource(schema_sql=schema_sql, seed_sql=resource_config.get("dbSeeds", {}).get(resource_ref))
        resources["db"][resource_ref] = conn
        closers.append(conn.close)
    return resources, closers


def compare_expectation(
    expect: dict,
    result: dict,
    instance_rows: list[dict],
    node_rows: list[dict],
    attempt_rows: list[dict],
) -> list[str]:
    mismatches = []
    if len(instance_rows) != 1:
        mismatches.append(f"expected 1 flow_instance row got {len(instance_rows)}")
        return mismatches
    instance = instance_rows[0]
    if instance["status"] != expect["instanceStatus"]:
        mismatches.append(f"instance status expected {expect['instanceStatus']} got {instance['status']}")
    node_statuses = {row["node_id"]: row["status"] for row in node_rows}
    for node_id, expected_status in expect.get("nodeStatuses", {}).items():
        if node_statuses.get(node_id) != expected_status:
            mismatches.append(f"node {node_id} expected {expected_status} got {node_statuses.get(node_id)}")
    node_attempt_counts = {row["node_id"]: row["attempt_count"] for row in node_rows}
    for node_id, expected_attempt_count in expect.get("nodeAttemptCounts", {}).items():
        if node_attempt_counts.get(node_id) != expected_attempt_count:
            mismatches.append(
                f"node {node_id} attemptCount expected {expected_attempt_count} got {node_attempt_counts.get(node_id)}"
            )
    for node_id, expected_skip_reason in expect.get("skipReason", {}).items():
        actual = next((row["skip_reason"] for row in node_rows if row["node_id"] == node_id), None)
        if actual != expected_skip_reason:
            mismatches.append(f"node {node_id} skipReason expected {expected_skip_reason} got {actual}")
    attempts_by_node: dict[str, list[dict]] = {}
    for row in attempt_rows:
        attempts_by_node.setdefault(row["node_id"], []).append(row)
    for node_id, expected_attempts in expect.get("attempts", {}).items():
        actual_attempts = attempts_by_node.get(node_id, [])
        if not contains_subset(actual_attempts, expected_attempts):
            mismatches.append(f"node {node_id} attempts expected subset {expected_attempts} got {actual_attempts}")
    return mismatches


def contains_subset(actual: object, expected: object) -> bool:
    if isinstance(expected, dict):
        if not isinstance(actual, dict):
            return False
        for key, value in expected.items():
            if key not in actual or not contains_subset(actual[key], value):
                return False
        return True
    if isinstance(expected, list):
        if not isinstance(actual, list) or len(actual) < len(expected):
            return False
        for index, value in enumerate(expected):
            if not contains_subset(actual[index], value):
                return False
        return True
    return actual == expected


def build_attempt_summary(result: dict, limit: int = 3) -> list[dict]:
    summary: list[dict] = []
    for node_id, record in result.get("nodeRecords", {}).items():
        attempts = record.get("attemptDetails", [])
        if not attempts:
            continue
        failed_attempts = [item for item in attempts if item.get("status") == "FAILED"]
        interesting = failed_attempts or attempts
        summary.append({"nodeId": node_id, "status": record.get("status"), "attempts": interesting[-limit:]})
    return summary


def write_junit(path: Path, results: dict) -> None:
    root = Element("testsuite", attrib={"name": results["suite"], "tests": str(results["stats"]["total"]), "failures": str(results["stats"]["failed"]), "skipped": "0"})
    for case in results["cases"]:
        testcase = SubElement(root, "testcase", attrib={"classname": case["suite"], "name": case["caseId"]})
        if case["status"] == "failed":
            failure = SubElement(testcase, "failure", attrib={"message": case["failureMessage"] or "failed"})
            failure.text = json.dumps(case, ensure_ascii=False, indent=2)
    ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


if __name__ == "__main__":
    main()
