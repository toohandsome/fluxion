from __future__ import annotations

import json
from pathlib import Path
from xml.etree.ElementTree import Element, ElementTree, parse


REPO_ROOT = Path(__file__).resolve().parents[2]
ARTIFACT_DIR = REPO_ROOT / ".artifacts" / "harness"


def load_results() -> list[dict]:
    result_files = sorted(ARTIFACT_DIR.rglob("results.json"))
    return [json.loads(path.read_text(encoding="utf-8")) for path in result_files]


def load_diagnostics() -> list[dict]:
    diagnostics = []
    for path in sorted(ARTIFACT_DIR.rglob("diagnostics.json")):
        if path == ARTIFACT_DIR / "diagnostics.json":
            continue
        payload = json.loads(path.read_text(encoding="utf-8"))
        diagnostics.extend(payload.get("diagnostics", []))
    return diagnostics


def write_summary(results: list[dict], diagnostics: list[dict]) -> None:
    total = sum(item["stats"]["total"] for item in results)
    passed = sum(item["stats"]["passed"] for item in results)
    failed = sum(item["stats"]["failed"] for item in results)
    skipped = sum(item["stats"]["skipped"] for item in results)
    lines = [
        "# Fluxion Harness Summary",
        "",
        f"- total: {total}",
        f"- passed: {passed}",
        f"- failed: {failed}",
        f"- skipped: {skipped}",
        "",
        "## Suites",
    ]
    for item in results:
        lines.append(
            f"- {item['suite']}: total={item['stats']['total']}, passed={item['stats']['passed']}, "
            f"failed={item['stats']['failed']}, skipped={item['stats']['skipped']}"
        )
    lines.extend(["", "## Diagnostics"])
    if diagnostics:
        for diag in diagnostics:
            lines.append(
                f"- [{diag['stage']}] {diag['errorCode']} @ {diag['caseId']} -> {diag['fixHint']} "
                f"(source: {diag.get('sourcePath')}#{diag.get('lineHint')})"
            )
    else:
        lines.append("- no diagnostics")
    lines.extend(["", "## Diagnostics by ownerModule"])
    module_buckets: dict[str, list[dict]] = {}
    for diag in diagnostics:
        module_buckets.setdefault(diag["ownerModule"], []).append(diag)
    if module_buckets:
        for owner_module in sorted(module_buckets):
            lines.append(f"- {owner_module}: {len(module_buckets[owner_module])}")
            for diag in module_buckets[owner_module]:
                lines.append(
                    f"  - {diag['errorCode']} ({diag['caseId']}) | cmd: {diag.get('suggestedCommand', '')}"
                )
    else:
        lines.append("- no module diagnostics")
    (ARTIFACT_DIR / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_aggregate_diagnostics(diagnostics: list[dict]) -> None:
    payload = {"version": "1.0", "diagnostics": diagnostics}
    (ARTIFACT_DIR / "diagnostics.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_aggregate_junit() -> None:
    root = Element("testsuites")
    for path in sorted(ARTIFACT_DIR.rglob("junit.xml")):
        if path == ARTIFACT_DIR / "junit.xml":
            continue
        suite = parse(path).getroot()
        root.append(suite)
    ElementTree(root).write(ARTIFACT_DIR / "junit.xml", encoding="utf-8", xml_declaration=True)


def main() -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    results = load_results()
    diagnostics = load_diagnostics()
    write_summary(results, diagnostics)
    write_aggregate_diagnostics(diagnostics)
    write_aggregate_junit()
    print(f"aggregated {len(results)} suites and {len(diagnostics)} diagnostics")


if __name__ == "__main__":
    main()
