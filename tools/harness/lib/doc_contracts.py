from __future__ import annotations

import re
from pathlib import Path


ADMIN_ENDPOINT_PATTERN = re.compile(r"`(?:GET|POST|PUT|DELETE|PATCH)\s+/admin/[^`]+`")
SCHEDULE_TIMEZONE_COMMENT_PATTERN = re.compile(
    r"COMMENT ON COLUMN flx_schedule_job\.timezone IS '[^']*Asia/Shanghai[^']*';"
)
LEGACY_MODELER_SUITE_PATTERN = re.compile(r'"suite"\s*:\s*"modeler-contracts"')


def validate_phase1_doc_contracts(repo_root: Path) -> list[dict]:
    diagnostics: list[dict] = []

    def add(message: str, *, source_path: str, line_hint: str) -> None:
        diagnostics.append(
            {
                "stage": "DOC_SYNC",
                "severity": "ERROR",
                "errorCode": "DOC_CONTRACT_DRIFT",
                "message": message,
                "sourcePath": source_path,
                "lineHint": line_hint,
            }
        )

    def relative(path: Path) -> str:
        return str(path.relative_to(repo_root)).replace("\\", "/")

    def require_contains(path: Path, needle: str, message: str, line_hint: str) -> None:
        text = path.read_text(encoding="utf-8")
        if needle not in text:
            add(message, source_path=relative(path), line_hint=line_hint)

    def require_absent(path: Path, pattern: re.Pattern[str], message: str, line_hint: str) -> None:
        text = path.read_text(encoding="utf-8")
        if pattern.search(text):
            add(message, source_path=relative(path), line_hint=line_hint)

    admin_contract = repo_root / "docs" / "phase-1" / "admin-api-contract.md"
    if not admin_contract.exists():
        add(
            "missing formal admin API contract document: docs/phase-1/admin-api-contract.md",
            source_path="docs/phase-1/admin-api-contract.md",
            line_hint="missing-file",
        )
        return diagnostics

    require_contains(
        repo_root / "README.md",
        "docs/phase-1/admin-api-contract.md",
        "README should link to the formal admin API contract",
        "section-doc-navigation",
    )
    require_contains(
        repo_root / "docs" / "doc-structure.md",
        "phase-1/admin-api-contract.md",
        "doc-structure should register admin-api-contract.md as the admin API SoT",
        "section-sot-table",
    )
    require_contains(
        repo_root / "docs" / "phase-1" / "technical-solution.md",
        "admin-api-contract.md",
        "technical-solution should point to admin-api-contract.md instead of repeating Admin API protocol details",
        "section-7.3-admin-api-index",
    )
    require_contains(
        repo_root / "tools" / "harness" / "run_contracts.py",
        'DOC_SYNC_CASE_ID = "contracts/doc-sync"',
        "run_contracts.py should expose a synthetic contracts/doc-sync case",
        "contracts-doc-sync-case",
    )
    require_contains(
        repo_root / "tools" / "harness" / "selective_tests.py",
        '"docs/phase-1/admin-api-contract.md"',
        "selective_tests.py should route admin-api-contract.md through harness suites",
        "changed-file-routing",
    )
    require_contains(
        repo_root / "tools" / "harness" / "selective_tests.py",
        '"docs/schema-pg.sql"',
        "selective_tests.py should route docs/schema-pg.sql through contracts and persistence suites",
        "changed-file-routing",
    )
    require_contains(
        repo_root / "docs" / "phase-1" / "schedule-contract.md",
        "Asia/Shanghai",
        "schedule-contract.md should pin phase-1 business timezone to Asia/Shanghai",
        "section-timezone",
    )
    require_contains(
        repo_root / "docs" / "phase-1" / "schedule-contract.md",
        "VALIDATION_ERROR",
        "schedule-contract.md should describe rejecting non-Asia/Shanghai timezone input",
        "section-timezone-validation",
    )

    schema_path = repo_root / "docs" / "schema-pg.sql"
    require_contains(
        schema_path,
        "timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai'",
        "schema-pg.sql should keep flx_schedule_job.timezone fixed to Asia/Shanghai in phase 1",
        "schedule-timezone-column",
    )
    require_absent(
        schema_path,
        re.compile(r"APP_KEY|BEARER_TOKEN"),
        "schema-pg.sql should not advertise unsupported phase-1 auth enums like APP_KEY or BEARER_TOKEN",
        "auth-enum-phase-boundary",
    )
    schema_text = schema_path.read_text(encoding="utf-8")
    if not SCHEDULE_TIMEZONE_COMMENT_PATTERN.search(schema_text):
        add(
            "schema-pg.sql should document flx_schedule_job.timezone as a fixed Asia/Shanghai snapshot in phase 1",
            source_path="docs/schema-pg.sql",
            line_hint="schedule-timezone-comment",
        )

    technical_solution_path = repo_root / "docs" / "phase-1" / "technical-solution.md"
    technical_solution = technical_solution_path.read_text(encoding="utf-8")
    if ADMIN_ENDPOINT_PATTERN.search(technical_solution):
        add(
            "technical-solution.md still contains concrete /admin/* endpoint definitions; keep only index links there",
            source_path="docs/phase-1/technical-solution.md",
            line_hint="section-7.3-admin-api-index",
        )

    derived_fixture = repo_root / "fixtures" / "modeler" / "invalid" / "unexpected-derived-fields.case.json"
    if not derived_fixture.exists():
        add(
            "missing fixture that guards against derived model_json fields drifting back into published contracts",
            source_path="fixtures/modeler/invalid/unexpected-derived-fields.case.json",
            line_hint="missing-file",
        )
    else:
        derived_fixture_text = derived_fixture.read_text(encoding="utf-8")
        if '"suite": "contracts"' not in derived_fixture_text:
            add(
                "unexpected-derived-fields fixture should stay in the contracts suite",
                source_path="fixtures/modeler/invalid/unexpected-derived-fields.case.json",
                line_hint="suite",
            )
        if '"primaryErrorCode": "NODE_SCHEMA_INVALID"' not in derived_fixture_text:
            add(
                "unexpected-derived-fields fixture should assert NODE_SCHEMA_INVALID as its primary error",
                source_path="fixtures/modeler/invalid/unexpected-derived-fields.case.json",
                line_hint="expect.primaryErrorCode",
            )

    for path in sorted((repo_root / "fixtures" / "modeler").rglob("*.case.json")):
        fixture_text = path.read_text(encoding="utf-8")
        if LEGACY_MODELER_SUITE_PATTERN.search(fixture_text):
            add(
                "modeler fixtures should no longer use the legacy modeler-contracts suite name",
                source_path=relative(path),
                line_hint="suite",
            )

    return diagnostics
