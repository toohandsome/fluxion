from __future__ import annotations

import argparse
import fnmatch
import json
import subprocess
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]

SUITE_COMMANDS = {
    "contracts": [sys.executable, "tools/harness/run_contracts.py"],
    "engine": [sys.executable, "tools/harness/run_engine_cases.py"],
    "runtime-api": [sys.executable, "tools/harness/run_runtime_api_cases.py"],
    "scheduler": [sys.executable, "tools/harness/run_scheduler_cases.py"],
    "persistence": [sys.executable, "tools/harness/run_persistence_cases.py"],
}

PATTERN_TO_SUITES = {
    "docs/phase-1/model-json*": {"contracts"},
    "docs/phase-1/admin-api-contract.md": {"contracts", "runtime-api", "scheduler", "persistence"},
    "docs/phase-1/node-schemas.md": {"contracts"},
    "fixtures/modeler/*": {"contracts"},
    "fluxion-modeler/*": {"contracts"},
    "docs/phase-1/runtime-semantics.md": {"engine", "runtime-api", "scheduler", "persistence"},
    "fixtures/engine/*": {"engine"},
    "fluxion-engine/*": {"engine"},
    "docs/phase-1/http-endpoint-contract.md": {"runtime-api"},
    "fixtures/runtime-api/*": {"runtime-api"},
    "fluxion-runtime-api/*": {"runtime-api"},
    "docs/phase-1/schedule-contract.md": {"scheduler"},
    "fixtures/scheduler/*": {"scheduler"},
    "fluxion-scheduler/*": {"scheduler"},
    "docs/schema-pg.sql": {"contracts", "persistence"},
    "fixtures/persistence/*": {"persistence"},
    "fluxion-persistence-mybatisplus/*": {"persistence"},
}


def main() -> None:
    args = parse_args()
    changed_files = args.changed_file or detect_changed_files(args.base_ref, args.head_ref)
    suites = select_suites(changed_files)
    payload = {
        "changedFiles": changed_files,
        "selectedSuites": sorted(suites),
        "commands": [" ".join(SUITE_COMMANDS[suite]) for suite in sorted(suites)],
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    if args.run:
        for suite in sorted(suites):
            subprocess.run(SUITE_COMMANDS[suite], cwd=REPO_ROOT, check=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Select Fluxion harness suites from changed files.")
    parser.add_argument("--base-ref", default="HEAD~1")
    parser.add_argument("--head-ref", default="HEAD")
    parser.add_argument("--changed-file", action="append", default=[])
    parser.add_argument("--run", action="store_true")
    return parser.parse_args()


def detect_changed_files(base_ref: str, head_ref: str) -> list[str]:
    try:
        completed = subprocess.run(
            ["git", "diff", "--name-only", f"{base_ref}..{head_ref}"],
            cwd=REPO_ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
    except subprocess.CalledProcessError:
        return []
    return [line.strip() for line in completed.stdout.splitlines() if line.strip()]


def select_suites(changed_files: list[str]) -> set[str]:
    if not changed_files:
        return set(SUITE_COMMANDS.keys())
    suites: set[str] = set()
    for changed_file in changed_files:
        normalized = changed_file.replace("\\", "/")
        matched = False
        for pattern, pattern_suites in PATTERN_TO_SUITES.items():
            if fnmatch.fnmatch(normalized, pattern):
                suites.update(pattern_suites)
                matched = True
        if not matched:
            suites.update(SUITE_COMMANDS.keys())
    return suites


if __name__ == "__main__":
    main()
