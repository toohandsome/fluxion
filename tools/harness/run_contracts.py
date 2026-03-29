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
from tools.harness.lib.doc_contracts import validate_phase1_doc_contracts
from tools.harness.lib.model_contracts import validate_model_contract


OUTPUT_DIR = REPO_ROOT / ".artifacts" / "harness" / "contracts"
CONTRACTS_SUITE = "contracts"
DOC_SYNC_CASE_ID = "contracts/doc-sync"


def load_cases(case_filter: str | None = None, suite_filter: str | None = None) -> list[dict]:
    cases = []
    for path in sorted((REPO_ROOT / "fixtures" / "modeler").rglob("*.case.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["_path"] = path
        if case_filter and payload["caseId"] != case_filter:
            continue
        if suite_filter and payload.get("suite", CONTRACTS_SUITE) != suite_filter:
            continue
        cases.append(payload)
    return cases


def main() -> None:
    args = parse_args()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    diagnostics = []
    result_cases = []
    loaded_cases = load_cases(case_filter=args.case, suite_filter=args.suite)
    allow_doc_sync_only = args.case == DOC_SYNC_CASE_ID
    if (args.case or args.suite) and not loaded_cases and not allow_doc_sync_only:
        raise SystemExit(f"no modeler cases matched case={args.case!r} suite={args.suite!r}")

    for case in loaded_cases:
        case_id = case["caseId"]
        suite = case.get("suite", CONTRACTS_SUITE)
        model = case["model"]
        fixture_path = str(case["_path"].relative_to(REPO_ROOT)).replace("\\", "/")
        raw_items = validate_model_contract(model)
        case_diags = [
            enrich_diagnostic(
                repo_root=REPO_ROOT,
                tool="fluxion-modeler",
                suite=suite,
                case_id=case_id,
                stage=item["stage"],
                severity=item["severity"],
                error_code=item["errorCode"],
                message=item["message"],
                field=item.get("field"),
                node_id=item.get("nodeId"),
                module="fluxion-modeler",
                suggested_command=f"python tools/harness/run_contracts.py --case {case_id}",
                candidate_files=candidate_files_for(item["errorCode"]),
                related_fixture=fixture_path,
            )
            for item in raw_items
        ]
        diagnostics.extend(case_diags)
        errors = [item for item in case_diags if item.severity == "ERROR"]
        if case["kind"] == "valid":
            passed = not errors
            failure_message = None if passed else f"expected valid model but got {[item.errorCode for item in errors]}"
        else:
            expected = case["expect"]["primaryErrorCode"]
            passed = any(item.errorCode == expected for item in errors)
            failure_message = None if passed else f"expected {expected}, got {[item.errorCode for item in errors]}"
        result_cases.append(
            {
                "caseId": case_id,
                "suite": suite,
                "kind": case["kind"],
                "description": case.get("description", ""),
                "passed": passed,
                "expectedErrorCode": case.get("expect", {}).get("primaryErrorCode"),
                "observedErrorCodes": [item.errorCode for item in errors],
                "fixturePath": fixture_path,
                "failureMessage": failure_message,
            }
        )

    if not args.case or args.case == DOC_SYNC_CASE_ID:
        raw_doc_items = validate_phase1_doc_contracts(REPO_ROOT)
        doc_sync_diags = [
            enrich_diagnostic(
                repo_root=REPO_ROOT,
                tool="fluxion-docs",
                suite=CONTRACTS_SUITE,
                case_id=DOC_SYNC_CASE_ID,
                stage=item["stage"],
                severity=item["severity"],
                error_code=item["errorCode"],
                message=item["message"],
                module="docs",
                suggested_command=f"python tools/harness/run_contracts.py --case {DOC_SYNC_CASE_ID}",
                candidate_files=[
                    "docs/phase-1/admin-api-contract.md",
                    "docs/phase-1/technical-solution.md",
                    "docs/phase-1/schedule-contract.md",
                    "docs/schema-pg.sql",
                    "docs/doc-structure.md",
                    "README.md",
                    "fixtures/modeler/invalid/unexpected-derived-fields.case.json",
                    "tools/harness/selective_tests.py",
                    "tools/harness/run_contracts.py",
                ],
                source_path=item.get("sourcePath"),
                line_hint=item.get("lineHint"),
            )
            for item in raw_doc_items
        ]
        diagnostics.extend(doc_sync_diags)
        doc_errors = [item for item in doc_sync_diags if item.severity == "ERROR"]
        result_cases.append(
            {
                "caseId": DOC_SYNC_CASE_ID,
                "suite": CONTRACTS_SUITE,
                "kind": "doc-sync",
                "description": "检查 contract 文档入口、technical-solution 索引边界与 Admin API SoT 是否同步。",
                "passed": not doc_errors,
                "expectedErrorCode": None,
                "observedErrorCodes": [item.errorCode for item in doc_errors],
                "fixturePath": None,
                "failureMessage": None if not doc_errors else f"doc sync drift: {[item.message for item in doc_errors]}",
            }
        )

    results = {
        "suite": CONTRACTS_SUITE,
        "tool": "fluxion-contracts",
        "cases": result_cases,
        "stats": {
            "total": len(result_cases),
            "passed": sum(1 for item in result_cases if item["passed"]),
            "failed": sum(1 for item in result_cases if not item["passed"]),
            "skipped": 0,
        },
    }

    (OUTPUT_DIR / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_diagnostics(OUTPUT_DIR / "diagnostics.json", diagnostics)
    write_junit(OUTPUT_DIR / "junit.xml", results)
    print(f"contract suite finished: {results['stats']}")
    if results["stats"]["failed"]:
        raise SystemExit(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Fluxion contract fixtures and doc sync checks.")
    parser.add_argument("--case", dest="case", help="Only run a single caseId.", default=None)
    parser.add_argument("--suite", dest="suite", help="Only run a single suite.", default=None)
    return parser.parse_args()


def candidate_files_for(error_code: str) -> list[str]:
    mapping = {
        "FLOW_OUTPUT_MAPPING_MISSING": [
            "docs/phase-1/runtime-semantics.md",
            "docs/phase-1/model-json-contract.md",
            "fluxion-modeler",
        ],
        "INVALID_CONDITION_BRANCH": [
            "docs/phase-1/runtime-semantics.md",
            "docs/phase-1/model-json-contract.md",
            "fixtures/modeler",
            "fluxion-modeler",
        ],
        "UNREACHABLE_NODE": [
            "docs/phase-1/runtime-semantics.md",
            "docs/phase-1/model-json-contract.md",
            "fixtures/modeler",
            "fluxion-modeler",
        ],
        "DISCONNECTED_SUBGRAPH": [
            "docs/phase-1/runtime-semantics.md",
            "docs/phase-1/model-json-contract.md",
            "fixtures/modeler",
            "fluxion-modeler",
        ],
        "VARIABLE_NOT_DECLARED": [
            "docs/phase-1/graph-json-contract.md",
            "docs/phase-1/node-schemas.md",
            "docs/phase-1/model-json-contract.md",
            "fixtures/modeler",
            "fluxion-modeler",
        ],
        "VARIABLE_DEFAULT_TYPE_MISMATCH": [
            "docs/phase-1/graph-json-contract.md",
            "docs/phase-1/model-json-contract.md",
            "fixtures/modeler",
            "fluxion-modeler",
        ],
        "VARIABLE_DYNAMIC_ACCESS_NOT_ALLOWED": [
            "docs/phase-1/graph-json-contract.md",
            "docs/phase-1/node-schemas.md",
            "docs/phase-1/model-json-contract.md",
            "fixtures/modeler",
            "fluxion-modeler",
        ],
    }
    return mapping.get(error_code, ["docs/phase-1/error-codes.md", "fluxion-modeler"])


def write_junit(path: Path, results: dict) -> None:
    root = Element(
        "testsuite",
        attrib={
            "name": results["suite"],
            "tests": str(results["stats"]["total"]),
            "failures": str(results["stats"]["failed"]),
            "skipped": str(results["stats"]["skipped"]),
        },
    )
    for case in results["cases"]:
        testcase = SubElement(
            root,
            "testcase",
            attrib={"classname": case["suite"], "name": case["caseId"]},
        )
        if not case["passed"]:
            failure = SubElement(testcase, "failure", attrib={"message": case["failureMessage"] or "failed"})
            failure.text = json.dumps(case, ensure_ascii=False, indent=2)
    ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


if __name__ == "__main__":
    main()
