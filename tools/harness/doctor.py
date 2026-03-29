from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]


def check_command(label: str, command: list[str]) -> tuple[bool, str]:
    executable = shutil.which(command[0])
    if not executable:
        return False, f"{label}: missing ({command[0]} not found)"
    try:
        completed = subprocess.run(
            [executable, *command[1:]],
            cwd=REPO_ROOT,
            check=True,
            capture_output=True,
            text=True,
        )
        output = (completed.stdout or completed.stderr).strip().splitlines()
        first_line = output[0] if output else "ok"
        return True, f"{label}: {first_line}"
    except subprocess.CalledProcessError as exc:
        output = (exc.stdout or exc.stderr or "").strip().splitlines()
        first_line = output[0] if output else "failed"
        return False, f"{label}: {first_line}"


def main() -> None:
    print("Fluxion harness doctor")
    checks = [
        check_command("python", [sys.executable, "--version"]),
        check_command("java", ["java", "-version"]),
        check_command("maven", ["mvn", "-v"]),
    ]
    docs = [
        REPO_ROOT / "docs" / "phase-1" / "runtime-semantics.md",
        REPO_ROOT / "docs" / "phase-1" / "model-json-contract.md",
        REPO_ROOT / "docs" / "phase-1" / "error-codes.md",
    ]
    for path in docs:
        checks.append((path.exists(), f"doc: {'ok' if path.exists() else 'missing'} -> {path.relative_to(REPO_ROOT)}"))

    generated = [
        REPO_ROOT / "generated" / "error-codes.json",
        REPO_ROOT / "generated" / "runtime-rules.json",
        REPO_ROOT / "generated" / "diagnostic-schema.json",
    ]
    for path in generated:
        checks.append((path.exists(), f"generated: {'ok' if path.exists() else 'missing'} -> {path.relative_to(REPO_ROOT)}"))

    fixture_count = len(list((REPO_ROOT / "fixtures").rglob("*.json")))
    checks.append((fixture_count > 0, f"fixtures: {fixture_count} json files"))

    failed = False
    for ok, message in checks:
        status = "OK" if ok else "FAIL"
        print(f"[{status}] {message}")
        failed = failed or not ok

    if failed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
