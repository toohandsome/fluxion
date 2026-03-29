from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from pathlib import Path
from xml.etree.ElementTree import Element, ElementTree, SubElement


REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from tools.harness.lib.diagnostics import enrich_diagnostic, write_diagnostics
from tools.harness.lib.reference_engine import create_sqlite_resource, execute_reference_engine


OUTPUT_DIR = REPO_ROOT / ".artifacts" / "harness" / "engine"


def load_cases(case_filter: str | None = None, suite_filter: str | None = None) -> list[dict]:
    cases = []
    for path in sorted((REPO_ROOT / "fixtures" / "engine").rglob("*.scenario.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["_path"] = path
        if case_filter and payload["caseId"] != case_filter:
            continue
        if suite_filter and payload.get("suite", "engine-scenarios") != suite_filter:
            continue
        cases.append(payload)
    return cases


def main() -> None:
    args = parse_args()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    diagnostics = []
    cases = []
    loaded_cases = load_cases(case_filter=args.case, suite_filter=args.suite)
    if (args.case or args.suite) and not loaded_cases:
        raise SystemExit(f"no engine cases matched case={args.case!r} suite={args.suite!r}")

    for case in loaded_cases:
        fixture_path = str(case["_path"].relative_to(REPO_ROOT)).replace("\\", "/")
        resources, closers = materialize_resources(case.get("resources", {}))
        try:
            result = execute_reference_engine(
                case["model"],
                trigger=case.get("trigger", {}),
                resources=resources,
                instance_id=next_instance_id(cases),
            )
        finally:
            for closer in closers:
                closer()
        mismatches = compare_engine_expectation(case, result)
        passed = not mismatches
        cases.append(
            {
                "caseId": case["caseId"],
                "suite": case.get("suite", "engine-scenarios"),
                "description": case.get("description", ""),
                "fixturePath": fixture_path,
                "status": "passed" if passed else "failed",
                "failureMessage": "; ".join(mismatches) if mismatches else None,
                "instanceStatus": result["instance"]["status"],
                "errorCode": result["instance"].get("errorCode"),
                "durationMs": result["durationMs"],
            }
        )
        for mismatch in mismatches:
            diagnostics.append(
                enrich_diagnostic(
                    repo_root=REPO_ROOT,
                    tool="fluxion-engine",
                    suite=case.get("suite", "engine-scenarios"),
                    case_id=case["caseId"],
                    stage="EXECUTION",
                    severity="ERROR",
                    error_code=result["instance"].get("errorCode") or "FLOW_FAILED",
                    message=mismatch,
                    module="fluxion-engine",
                    suggested_command=f"python tools/harness/run_engine_cases.py --case {case['caseId']}",
                    candidate_files=["fluxion-engine", fixture_path, "docs/phase-1/runtime-semantics.md"],
                    related_fixture=fixture_path,
                    source_path="docs/phase-1/runtime-semantics.md",
                    line_hint="runtime-semantics-failure-propagation",
                    attempt_summary=build_attempt_summary(result),
                )
            )

    results = {
        "suite": "engine-scenarios",
        "tool": "fluxion-engine",
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
    print(f"engine scenario suite finished: {results['stats']}")
    if results["stats"]["failed"]:
        raise SystemExit(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Fluxion engine scenario fixtures.")
    parser.add_argument("--case", dest="case", help="Only run a single caseId.", default=None)
    parser.add_argument("--suite", dest="suite", help="Only run a single suite.", default=None)
    return parser.parse_args()


def next_instance_id(existing_cases: list[dict]) -> int:
    return 10000 + len(existing_cases) + 1


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


def compare_engine_expectation(case: dict, result: dict) -> list[str]:
    expect = case.get("expect", {})
    mismatches: list[str] = []
    if expect.get("instanceStatus") and result["instance"]["status"] != expect["instanceStatus"]:
        mismatches.append(f"instanceStatus expected {expect['instanceStatus']} got {result['instance']['status']}")
    if expect.get("errorCode") and result["instance"].get("errorCode") != expect["errorCode"]:
        mismatches.append(f"errorCode expected {expect['errorCode']} got {result['instance'].get('errorCode')}")
    if "flowOutput" in expect and result.get("flowOutput") != expect["flowOutput"]:
        mismatches.append(f"flowOutput expected {expect['flowOutput']} got {result.get('flowOutput')}")
    for node_id, expected_status in expect.get("nodeStatuses", {}).items():
        actual = result["nodeRecords"].get(node_id, {}).get("status")
        if actual != expected_status:
            mismatches.append(f"node {node_id} expected {expected_status} got {actual}")
    for node_id, expected_reason in expect.get("skipReasons", {}).items():
        actual = result["nodeRecords"].get(node_id, {}).get("skipReason")
        if actual != expected_reason:
            mismatches.append(f"node {node_id} skipReason expected {expected_reason} got {actual}")
    for node_id, expected_error_code in expect.get("nodeErrorCodes", {}).items():
        actual = result["nodeRecords"].get(node_id, {}).get("errorCode")
        if actual != expected_error_code:
            mismatches.append(f"node {node_id} errorCode expected {expected_error_code} got {actual}")
    for node_id, expected_attempts in expect.get("attemptCounts", {}).items():
        actual = result["nodeRecords"].get(node_id, {}).get("attempts")
        if actual != expected_attempts:
            mismatches.append(f"node {node_id} attempts expected {expected_attempts} got {actual}")
    for node_id, expected_attempt_details in expect.get("attemptDetails", {}).items():
        actual = result["nodeRecords"].get(node_id, {}).get("attemptDetails")
        if not contains_subset(actual, expected_attempt_details):
            mismatches.append(f"node {node_id} attemptDetails expected subset {expected_attempt_details} got {actual}")
    for node_id in expect.get("missingNodes", []):
        if node_id in result["nodeRecords"]:
            mismatches.append(f"node {node_id} should not be scheduled")
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
    return expected == actual


def build_attempt_summary(result: dict, limit: int = 3) -> list[dict]:
    summary: list[dict] = []
    for node_id, record in result.get("nodeRecords", {}).items():
        attempts = record.get("attemptDetails", [])
        if not attempts:
            continue
        failed_attempts = [item for item in attempts if item.get("status") == "FAILED"]
        interesting = failed_attempts or attempts
        summary.append(
            {
                "nodeId": node_id,
                "status": record.get("status"),
                "attempts": interesting[-limit:],
            }
        )
    return summary


def write_junit(path: Path, results: dict) -> None:
    root = Element(
        "testsuite",
        attrib={
            "name": results["suite"],
            "tests": str(results["stats"]["total"]),
            "failures": str(results["stats"]["failed"]),
            "skipped": "0",
        },
    )
    for case in results["cases"]:
        testcase = SubElement(root, "testcase", attrib={"classname": case["suite"], "name": case["caseId"]})
        if case["status"] == "failed":
            failure = SubElement(testcase, "failure", attrib={"message": case["failureMessage"] or "failed"})
            failure.text = json.dumps(case, ensure_ascii=False, indent=2)
    ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


if __name__ == "__main__":
    main()
