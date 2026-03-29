from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, List, Optional


@dataclass
class Diagnostic:
    tool: str
    suite: str
    caseId: str
    stage: str
    severity: str
    errorCode: str
    message: str
    ownerModule: str
    fixHint: str
    docRefs: List[str]
    suggestedCommand: str
    candidateFiles: List[str]
    relatedFixture: Optional[str] = None
    sourcePath: Optional[str] = None
    lineHint: Optional[str] = None
    module: Optional[str] = None
    field: Optional[str] = None
    nodeId: Optional[str] = None
    attemptSummary: Optional[List[dict]] = None

    def to_dict(self) -> dict:
        return asdict(self)


def load_error_catalog(repo_root: Path) -> dict:
    path = repo_root / "generated" / "error-codes.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    return {entry["errorCode"]: entry for entry in payload["entries"]}


def enrich_diagnostic(
    *,
    repo_root: Path,
    tool: str,
    suite: str,
    case_id: str,
    stage: str,
    severity: str,
    error_code: str,
    message: str,
    module: Optional[str] = None,
    field: Optional[str] = None,
    node_id: Optional[str] = None,
    suggested_command: Optional[str] = None,
    candidate_files: Optional[List[str]] = None,
    related_fixture: Optional[str] = None,
    source_path: Optional[str] = None,
    line_hint: Optional[str] = None,
    attempt_summary: Optional[List[dict]] = None,
) -> Diagnostic:
    catalog = load_error_catalog(repo_root)
    entry = catalog.get(
        error_code,
        {
            "ownerModule": "fluxion-common",
            "fixHint": "回看对应契约文档，定位失败阶段与字段后修复。",
            "docRefs": ["docs/phase-1/error-codes.md"],
        },
    )
    return Diagnostic(
        tool=tool,
        suite=suite,
        caseId=case_id,
        stage=stage,
        severity=severity,
        errorCode=error_code,
        message=message,
        ownerModule=entry["ownerModule"],
        fixHint=entry["fixHint"],
        docRefs=entry["docRefs"],
        suggestedCommand=suggested_command or "",
        candidateFiles=candidate_files or [],
        relatedFixture=related_fixture,
        sourcePath=source_path or entry.get("sourcePath"),
        lineHint=line_hint or entry.get("lineHint"),
        module=module,
        field=field,
        nodeId=node_id,
        attemptSummary=attempt_summary,
    )


def write_diagnostics(path: Path, diagnostics: Iterable[Diagnostic]) -> None:
    payload = {
        "version": "1.0",
        "diagnostics": [item.to_dict() for item in diagnostics],
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
