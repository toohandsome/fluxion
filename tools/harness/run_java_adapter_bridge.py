from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]


def main() -> None:
    args = parse_args()
    mvn = shutil.which("mvn") or shutil.which("mvn.cmd") or "mvn"
    command = [
        mvn,
        "-q",
        "-pl",
        "fluxion-test",
        "-am",
        f"-Dtest=JavaAdapterHarnessExecutionTest",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        f"-Dfluxion.adapter.suite={args.suite}",
    ]
    if args.case:
        command.append(f"-Dfluxion.adapter.case={args.case}")
    command.append("test")
    completed = subprocess.run(command, cwd=REPO_ROOT)
    raise SystemExit(completed.returncode)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Fluxion harness through Java adapter registry.")
    parser.add_argument("--suite", required=True, choices=["contracts", "engine", "engine-real", "runtime-api", "scheduler", "persistence", "persistence-real"])
    parser.add_argument("--case", default=None)
    return parser.parse_args()


if __name__ == "__main__":
    main()
