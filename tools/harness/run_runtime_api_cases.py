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
from tools.harness.lib.reference_engine import eval_expression, eval_mapping, execute_reference_engine


OUTPUT_DIR = REPO_ROOT / ".artifacts" / "harness" / "runtime-api"


def load_cases(case_filter: str | None = None, suite_filter: str | None = None) -> list[dict]:
    cases = []
    for path in sorted((REPO_ROOT / "fixtures" / "runtime-api").rglob("*.case.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["_path"] = path
        if case_filter and payload["caseId"] != case_filter:
            continue
        if suite_filter and payload.get("suite", "runtime-api-integration") != suite_filter:
            continue
        cases.append(payload)
    return cases


def main() -> None:
    args = parse_args()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    loaded_cases = load_cases(case_filter=args.case, suite_filter=args.suite)
    if (args.case or args.suite) and not loaded_cases:
        raise SystemExit(f"no runtime-api cases matched case={args.case!r} suite={args.suite!r}")

    diagnostics = []
    cases = []
    for index, case in enumerate(loaded_cases, start=1):
        fixture_path = str(case["_path"].relative_to(REPO_ROOT)).replace("\\", "/")
        actual = execute_runtime_api_case(case, instance_id=20000 + index)
        mismatches = compare_expectation(case["expect"], actual)
        cases.append(
            {
                "caseId": case["caseId"],
                "suite": case.get("suite", "runtime-api-integration"),
                "description": case.get("description", ""),
                "fixturePath": fixture_path,
                "status": "passed" if not mismatches else "failed",
                "failureMessage": "; ".join(mismatches) if mismatches else None,
                "initialCode": actual["initial"]["code"],
                "resultCode": actual.get("resultQuery", {}).get("code"),
            }
        )
        for mismatch in mismatches:
            diagnostics.append(
                enrich_diagnostic(
                    repo_root=REPO_ROOT,
                    tool="fluxion-runtime-api",
                    suite=case.get("suite", "runtime-api-integration"),
                    case_id=case["caseId"],
                    stage="RUNTIME_API",
                    severity="ERROR",
                    error_code=actual["initial"]["code"] if actual["initial"]["code"] != "OK" else "FLOW_FAILED",
                    message=mismatch,
                    module="fluxion-runtime-api",
                    suggested_command=f"python tools/harness/run_runtime_api_cases.py --case {case['caseId']}",
                    candidate_files=["fluxion-runtime-api", fixture_path, "docs/phase-1/http-endpoint-contract.md"],
                    related_fixture=fixture_path,
                    source_path="docs/phase-1/http-endpoint-contract.md",
                    line_hint="request_config / response_config",
                )
            )

    results = {
        "suite": "runtime-api-integration",
        "tool": "fluxion-runtime-api",
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
    print(f"runtime-api integration suite finished: {results['stats']}")
    if results["stats"]["failed"]:
        raise SystemExit(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Fluxion runtime-api integration fixtures.")
    parser.add_argument("--case", default=None)
    parser.add_argument("--suite", default=None)
    return parser.parse_args()


def execute_runtime_api_case(case: dict, *, instance_id: int) -> dict:
    endpoint = case["endpoint"]
    request = case.get("request", {})
    extraction = extract_request(endpoint.get("requestConfig", {}), request)
    if extraction["code"] != "OK":
        return {"initial": extraction}
    trigger = {
        "triggerType": "HTTP",
        "request": extraction["request"],
        "businessKey": extraction.get("businessKey"),
    }
    result = execute_reference_engine(case["model"], trigger=trigger, instance_id=instance_id)
    flow_context = {
        **build_context(result, extraction["request"]),
        "flow": {"output": result.get("flowOutput")},
    }
    if endpoint.get("syncMode", True):
        if result["instance"]["status"] == "SUCCESS":
            data = endpoint.get("responseConfig", {}).get("successDataMapping") or {
                "instanceId": "${instance.instanceId}",
                "status": "${instance.status}",
                "result": "${flow.output}",
            }
            return {
                "initial": {
                    "code": "OK",
                    "data": eval_mapping(data, flow_context),
                }
            }
        data = endpoint.get("responseConfig", {}).get("failureDataMapping") or {
            "instanceId": "${instance.instanceId}",
            "status": "${instance.status}",
            "errorCode": "${instance.errorCode}",
            "errorMessage": "${instance.errorMessage}",
        }
        return {
            "initial": {
                "code": result["instance"].get("errorCode") or "FLOW_FAILED",
                "data": eval_mapping(data, flow_context),
            }
        }

    running = endpoint.get("responseConfig", {}).get("runningDataMapping") or {
        "instanceId": "${instance.instanceId}",
        "status": "RUNNING",
        "queryUrl": "/runtime/instances/${instance.instanceId}/result",
    }
    initial = {
        "code": "ACCEPTED",
        "data": eval_mapping(running, flow_context),
    }
    result_query = {
        "code": "OK" if result["instance"]["status"] == "SUCCESS" else result["instance"].get("errorCode") or "FLOW_FAILED",
        "data": {
            "instanceId": result["instance"]["instanceId"],
            "status": result["instance"]["status"],
            "result": result.get("flowOutput"),
        },
    }
    return {"initial": initial, "resultQuery": result_query}


def build_context(result: dict, extracted_request: dict) -> dict:
    instance = dict(result["instance"])
    return {
        "request": extracted_request,
        "schedule": {},
        "vars": result.get("vars", {}),
        "instance": instance,
        "nodes": {
            node_id: {
                "status": record["status"],
                "output": record.get("output"),
            }
            for node_id, record in result["nodeRecords"].items()
        },
    }


def extract_request(request_config: dict, request: dict) -> dict:
    extracted = {
        "path": {},
        "query": {},
        "headers": {},
        "body": request.get("body"),
    }
    for section_name, target_key, source_key in [
        ("pathParams", "path", "path"),
        ("queryParams", "query", "query"),
        ("headerParams", "headers", "headers"),
    ]:
        for param in request_config.get(section_name, []):
            raw_value = request.get(source_key, {}).get(param["name"], param.get("defaultValue"))
            if raw_value is None and param.get("required"):
                return {"code": "VALIDATION_ERROR", "data": {"field": param["name"]}}
            extracted[target_key][param["name"]] = convert_type(raw_value, param.get("type"))
    body_config = request_config.get("body", {})
    body = request.get("body")
    if body_config.get("required") and not body:
        return {"code": "VALIDATION_ERROR", "data": {"field": "body"}}
    required_fields = body_config.get("schema", {}).get("required", [])
    if body is not None:
        for field in required_fields:
            if field not in body:
                return {"code": "VALIDATION_ERROR", "data": {"field": field}}
    business_key = None
    if request_config.get("businessKeyExpr"):
        business_key = eval_expression(request_config["businessKeyExpr"], {"request": extracted})
    return {"code": "OK", "request": extracted, "businessKey": business_key}


def convert_type(value, type_name):
    if value is None or type_name is None:
        return value
    if type_name == "BOOLEAN":
        if isinstance(value, bool):
            return value
        return str(value).lower() == "true"
    if type_name == "INT":
        return int(value)
    if type_name == "LONG":
        return int(value)
    if type_name == "DOUBLE":
        return float(value)
    return value


def compare_expectation(expect: dict, actual: dict) -> list[str]:
    mismatches = []
    if actual["initial"]["code"] != expect["initialCode"]:
        mismatches.append(f"initialCode expected {expect['initialCode']} got {actual['initial']['code']}")
    if "initialData" in expect and not contains_subset(actual["initial"].get("data"), expect["initialData"]):
        mismatches.append(f"initialData expected subset {expect['initialData']} got {actual['initial'].get('data')}")
    if "resultCode" in expect:
        if actual.get("resultQuery", {}).get("code") != expect["resultCode"]:
            mismatches.append(f"resultCode expected {expect['resultCode']} got {actual.get('resultQuery', {}).get('code')}")
    if "resultData" in expect and not contains_subset(actual.get("resultQuery", {}).get("data"), expect["resultData"]):
        mismatches.append(f"resultData expected subset {expect['resultData']} got {actual.get('resultQuery', {}).get('data')}")
    return mismatches


def contains_subset(actual, expected) -> bool:
    if isinstance(expected, dict):
        if not isinstance(actual, dict):
            return False
        return all(key in actual and contains_subset(actual[key], value) for key, value in expected.items())
    if isinstance(expected, list):
        if not isinstance(actual, list) or len(actual) < len(expected):
            return False
        return all(contains_subset(actual[index], value) for index, value in enumerate(expected))
    return actual == expected


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
