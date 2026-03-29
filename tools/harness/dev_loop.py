from __future__ import annotations

import argparse
import fnmatch
import json
import shutil
import subprocess
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]

PYTHON_SUITE_COMMANDS = {
    "contracts": [sys.executable, "tools/harness/run_contracts.py"],
    "engine": [sys.executable, "tools/harness/run_engine_cases.py"],
    "runtime-api": [sys.executable, "tools/harness/run_runtime_api_cases.py"],
    "scheduler": [sys.executable, "tools/harness/run_scheduler_cases.py"],
    "persistence": [sys.executable, "tools/harness/run_persistence_cases.py"],
}

JAVA_BRIDGE_SMOKE_CASES = {
    "engine": "engine/db-seed-update-or-in-order-limit",
    "persistence": "persistence/retry-attempt-snapshots",
}

JAVA_RELATED_PATTERNS = {
    "pom.xml",
    "fluxion-*/pom.xml",
    "fluxion-*/src/main/java/*",
    "fluxion-*/src/test/java/*",
    "fluxion-*/src/main/resources/*",
    "fluxion-test/*",
    "tools/harness/run_java_adapter_bridge.py",
}


def main() -> None:
    args = parse_args()
    if args.case and args.suite == "all":
        raise SystemExit("--case requires an explicit --suite")
    if args.suite in {"engine-real", "persistence-real"} and not args.case:
        raise SystemExit(f"--suite {args.suite} requires --case")

    if args.smart:
        args.auto_java_bridge = True
        args.maven_if_changed_java = True

    changed_files = resolve_changed_files(args)
    executed_commands: list[list[str]] = []

    if not args.skip_build_catalogs:
        run([sys.executable, "tools/contracts/build_catalogs.py"], executed_commands)

    if should_run_doctor(args):
        run([sys.executable, "tools/harness/doctor.py"], executed_commands)

    suites = resolve_suites(args, changed_files)
    if not suites:
        raise SystemExit("no suites selected")

    for suite in suites:
        command = build_suite_command(suite, args.case)
        run(command, executed_commands)

    java_bridge_runs = resolve_java_bridge_runs(args, suites)
    for bridge_suite, bridge_case in java_bridge_runs:
        run(build_suite_command(bridge_suite, bridge_case), executed_commands)

    if should_collect(args, suites):
        run([sys.executable, "tools/harness/collect_results.py"], executed_commands)

    should_run_maven, maven_triggered_by = resolve_maven_strategy(args, changed_files)
    if should_run_maven:
        run(build_maven_command(), executed_commands)

    payload = {
        "status": "ok",
        "suites": suites,
        "javaBridgeRuns": [
            {"suite": bridge_suite, "case": bridge_case}
            for bridge_suite, bridge_case in java_bridge_runs
        ],
        "case": args.case,
        "changedFiles": changed_files,
        "withMaven": should_run_maven,
        "mavenTriggeredBy": maven_triggered_by,
        "mavenMode": "always" if args.with_maven else ("if-changed-java" if args.maven_if_changed_java else "off"),
        "autoJavaBridge": args.auto_java_bridge,
        "smart": args.smart,
        "commands": [" ".join(command) for command in executed_commands],
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Unified Fluxion harness feedback loop entrypoint.")
    parser.add_argument(
        "--suite",
        choices=["all", *PYTHON_SUITE_COMMANDS.keys(), "engine-real", "persistence-real"],
        default="all",
        help="Suite to run. Default: all Python suites.",
    )
    parser.add_argument("--case", default=None, help="Run a single caseId for the selected suite.")
    parser.add_argument("--changed-file", action="append", default=[], help="Changed file path, can be passed multiple times.")
    parser.add_argument("--base-ref", default="HEAD~1")
    parser.add_argument("--head-ref", default="HEAD")
    parser.add_argument("--skip-build-catalogs", action="store_true")
    parser.add_argument("--skip-doctor", action="store_true")
    parser.add_argument("--skip-collect", action="store_true")
    parser.add_argument("--with-maven", action="store_true", help="Also run: mvn -q -pl fluxion-test -am test")
    parser.add_argument(
        "--maven-if-changed-java",
        action="store_true",
        help="Run Maven only when changed files include Java modules / adapter / bridge paths.",
    )
    parser.add_argument(
        "--auto-java-bridge",
        action="store_true",
        help="Automatically append engine-real / persistence-real smoke bridge runs when relevant.",
    )
    parser.add_argument(
        "--smart",
        action="store_true",
        help="Equivalent to: --auto-java-bridge --maven-if-changed-java",
    )
    return parser.parse_args()


def resolve_suites(args: argparse.Namespace, changed_files: list[str]) -> list[str]:
    if args.suite != "all":
        return [args.suite]
    if changed_files:
        return select_suites_from_changed_files(changed_files)
    return list(PYTHON_SUITE_COMMANDS.keys())


def resolve_changed_files(args: argparse.Namespace) -> list[str]:
    if args.changed_file:
        return normalize_paths(args.changed_file)
    return normalize_paths(detect_changed_files(args.base_ref, args.head_ref))


def build_suite_command(suite: str, case_id: str | None) -> list[str]:
    if suite in PYTHON_SUITE_COMMANDS:
        command = list(PYTHON_SUITE_COMMANDS[suite])
        if case_id:
            command.extend(["--case", case_id])
        return command

    if suite not in {"engine-real", "persistence-real"}:
        raise SystemExit(f"unsupported suite for dev_loop: {suite}")

    command = [sys.executable, "tools/harness/run_java_adapter_bridge.py", "--suite", suite]
    if case_id:
        command.extend(["--case", case_id])
    return command


def resolve_java_bridge_runs(args: argparse.Namespace, suites: list[str]) -> list[tuple[str, str]]:
    if not args.auto_java_bridge:
        return []

    runs: list[tuple[str, str]] = []
    if args.suite == "engine-real" or args.suite == "persistence-real":
        return runs

    if args.suite == "engine" and args.case:
        runs.append(("engine-real", args.case))
    elif "engine" in suites:
        runs.append(("engine-real", JAVA_BRIDGE_SMOKE_CASES["engine"]))

    if args.suite == "persistence" and args.case:
        runs.append(("persistence-real", args.case))
    elif "persistence" in suites:
        runs.append(("persistence-real", JAVA_BRIDGE_SMOKE_CASES["persistence"]))

    return runs


def resolve_maven_strategy(args: argparse.Namespace, changed_files: list[str]) -> tuple[bool, list[dict[str, str]]]:
    if args.with_maven:
        return True, [{"reason": "explicit --with-maven"}]
    if not args.maven_if_changed_java:
        return False, []
    matches = find_java_related_change_matches(changed_files)
    return bool(matches), matches


def should_run_doctor(args: argparse.Namespace) -> bool:
    if args.skip_doctor:
        return False
    return args.suite in {"all", "contracts", "engine", "runtime-api", "scheduler", "persistence"}


def should_collect(args: argparse.Namespace, suites: list[str]) -> bool:
    if args.skip_collect:
        return False
    return any(suite in PYTHON_SUITE_COMMANDS for suite in suites)


def select_suites_from_changed_files(changed_files: list[str]) -> list[str]:
    command = [
        sys.executable,
        "tools/harness/selective_tests.py",
    ]
    for changed_file in changed_files:
        command.extend(["--changed-file", changed_file])
    completed = subprocess.run(
        command,
        cwd=REPO_ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    payload = json.loads(completed.stdout)
    return payload["selectedSuites"]


def find_java_related_change_matches(changed_files: list[str]) -> list[dict[str, str]]:
    matches: list[dict[str, str]] = []
    for changed_file in normalize_paths(changed_files):
        for pattern in JAVA_RELATED_PATTERNS:
            if fnmatch.fnmatch(changed_file, pattern):
                matches.append(
                    {
                        "reason": "changed file matched Java-related Maven trigger",
                        "path": changed_file,
                        "pattern": pattern,
                    }
                )
                break
    return matches


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


def normalize_paths(paths: list[str]) -> list[str]:
    return [path.replace("\\", "/") for path in paths]


def run(command: list[str], executed_commands: list[list[str]]) -> None:
    executed_commands.append(command)
    completed = subprocess.run(command, cwd=REPO_ROOT)
    if completed.returncode != 0:
        raise SystemExit(completed.returncode)


def build_maven_command() -> list[str]:
    mvn = shutil.which("mvn") or shutil.which("mvn.cmd") or "mvn"
    return [mvn, "-q", "-pl", "fluxion-test", "-am", "test"]


if __name__ == "__main__":
    main()
