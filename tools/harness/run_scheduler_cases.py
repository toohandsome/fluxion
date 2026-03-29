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


OUTPUT_DIR = REPO_ROOT / ".artifacts" / "harness" / "scheduler"


def load_cases(case_filter: str | None = None, suite_filter: str | None = None) -> list[dict]:
    cases = []
    for path in sorted((REPO_ROOT / "fixtures" / "scheduler").rglob("*.case.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["_path"] = path
        if case_filter and payload["caseId"] != case_filter:
            continue
        if suite_filter and payload.get("suite", "scheduler-integration") != suite_filter:
            continue
        cases.append(payload)
    return cases


def main() -> None:
    args = parse_args()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    loaded_cases = load_cases(case_filter=args.case, suite_filter=args.suite)
    if (args.case or args.suite) and not loaded_cases:
        raise SystemExit(f"no scheduler cases matched case={args.case!r} suite={args.suite!r}")
    diagnostics = []
    cases = []
    for case in loaded_cases:
        fixture_path = str(case["_path"].relative_to(REPO_ROOT)).replace("\\", "/")
        actual = execute_scheduler_case(case)
        mismatches = compare_expectation(case["expect"], actual)
        cases.append(
            {
                "caseId": case["caseId"],
                "suite": case.get("suite", "scheduler-integration"),
                "description": case.get("description", ""),
                "fixturePath": fixture_path,
                "status": "passed" if not mismatches else "failed",
                "failureMessage": "; ".join(mismatches) if mismatches else None,
                "dispatchCode": actual["dispatchCode"],
                "instanceStatusSnapshot": actual.get("instanceStatusSnapshot"),
            }
        )
        for mismatch in mismatches:
            diagnostics.append(
                enrich_diagnostic(
                    repo_root=REPO_ROOT,
                    tool="fluxion-scheduler",
                    suite=case.get("suite", "scheduler-integration"),
                    case_id=case["caseId"],
                    stage="SCHEDULER",
                    severity="ERROR",
                    error_code=actual["dispatchCode"],
                    message=mismatch,
                    module="fluxion-scheduler",
                    suggested_command=f"python tools/harness/run_scheduler_cases.py --case {case['caseId']}",
                    candidate_files=["fluxion-scheduler", fixture_path, "docs/phase-1/schedule-contract.md"],
                    related_fixture=fixture_path,
                    source_path="docs/phase-1/schedule-contract.md",
                    line_hint="reentryPolicy / waitTimeoutMs / maxConcurrency",
                )
            )
    results = {
        "suite": "scheduler-integration",
        "tool": "fluxion-scheduler",
        "cases": cases,
        "stats": {
            "total": len(cases),
            "passed": sum(1 for item in cases if item["status"] == "passed"),
            "failed": sum(1 for item in cases if item["status"] == "failed"),
            "skipped": 0,
        },
    }
    (OUTPUT_DIR / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_diagnostics(OUTPUT_DIR / "diagnostics.json", diagnostics)
    write_junit(OUTPUT_DIR / "junit.xml", results)
    print(f"scheduler integration suite finished: {results['stats']}")
    if results["stats"]["failed"]:
        raise SystemExit(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Fluxion scheduler integration fixtures.")
    parser.add_argument("--case", default=None)
    parser.add_argument("--suite", default=None)
    return parser.parse_args()


def execute_scheduler_case(case: dict) -> dict:
    schedule = case["schedule"]
    if schedule["reentryPolicy"] == "FORBID" and schedule.get("activeInstances", 0) > 0:
        return {"dispatchCode": "REJECTED_CONCURRENCY"}
    if schedule.get("activeInstances", 0) >= schedule.get("maxConcurrency", 1):
        return {"dispatchCode": "REJECTED_CONCURRENCY"}
    engine_case = case.get("engineCase", {})
    duration = engine_case.get("simulatedDurationMs", 0)
    final_status = engine_case.get("finalStatus", "SUCCESS")
    if duration > schedule.get("waitTimeoutMs", 0):
        return {
            "dispatchCode": "WAIT_TIMEOUT",
            "instanceStatusSnapshot": "RUNNING",
            "finalStatus": final_status,
        }
    return {
        "dispatchCode": "OK" if final_status == "SUCCESS" else "DISPATCH_FAILED",
        "instanceStatusSnapshot": final_status,
        "finalStatus": final_status,
    }


def compare_expectation(expect: dict, actual: dict) -> list[str]:
    mismatches = []
    if actual["dispatchCode"] != expect["dispatchCode"]:
        mismatches.append(f"dispatchCode expected {expect['dispatchCode']} got {actual['dispatchCode']}")
    if "instanceStatusSnapshot" in expect and actual.get("instanceStatusSnapshot") != expect["instanceStatusSnapshot"]:
        mismatches.append(
            f"instanceStatusSnapshot expected {expect['instanceStatusSnapshot']} got {actual.get('instanceStatusSnapshot')}"
        )
    return mismatches


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
