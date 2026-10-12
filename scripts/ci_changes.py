#!/usr/bin/env python3
"""Classify the complete git diff, including both sides of renames/deletions."""
import json
import os
from pathlib import Path
import subprocess


def git(*args):
    return subprocess.check_output(["git", *args])


def classify(paths):
    android = windows = False
    for path in paths:
        # CI infrastructure is deliberately shared and must exercise both platforms.
        if path in {".gitignore", ".editorconfig", ".github/workflows/ci.yml"} or path.startswith("scripts/ci_"):
            android = windows = True
        elif path.endswith(".md") or path.startswith("docs/") or path in {"LICENSE", "LICENSE-NOTICE"}:
            continue
        elif path.startswith("windows/") or path in {
            ".github/workflows/windows.yml", ".github/workflows/windows-audit.yml",
            "scripts/audit_nuget.py", "NuGet.Config", "nuget.config",
            "Directory.Build.props", "Directory.Build.targets", "Directory.Packages.props",
        }:
            windows = True
        elif path.startswith(("app/", "gradle/")) or path in {
            "gradlew", "gradlew.bat", "gradle.properties", "build.gradle.kts", "settings.gradle.kts",
            "keystore.properties.example", "scripts/run_android_tests.sh",
            "scripts/verify_android_test_results.py", "scripts/audit_dependency_licenses.py",
            ".github/workflows/android-ci.yml", ".github/workflows/android-full-compat.yml",
            ".github/workflows/license-check.yml", ".github/workflows/version-check.yml",
        }:
            android = True
        elif path in {"wrangler.jsonc", ".github/workflows/pages.yml",
                      ".github/workflows/github-pages-redirect.yml", "scripts/update-site-releases.mjs"}:
            continue
        else:
            # New/unclassified paths cannot silently escape platform verification.
            android = windows = True
    return android, windows


def changed_paths(event_name, event):
    if event_name in {"schedule", "workflow_dispatch"}:
        return None
    if event_name == "pull_request":
        base = event["pull_request"]["base"]["sha"]
        head = event["pull_request"]["head"]["sha"]
        base = git("merge-base", base, head).decode().strip()
    elif event_name == "merge_group":
        base, head = event["merge_group"]["base_sha"], event["merge_group"]["head_sha"]
    elif event_name == "push":
        base, head = event["before"], event["after"]
        if base == "0" * 40:
            return None
        try:
            git("cat-file", "-e", base + "^{commit}")
        except subprocess.CalledProcessError:
            # A force push may remove the old head from the fetched branch history.
            subprocess.run(["git", "fetch", "--no-tags", "origin", base], check=True)
    else:
        raise ValueError(f"Unsupported event: {event_name}")
    # --no-renames lists old and new paths separately; -z handles whitespace/newlines.
    raw = git("diff", "--name-only", "--no-renames", "-z", base, head, "--")
    return [p.decode("utf-8", errors="strict") for p in raw.split(b"\0") if p]


def windows_available():
    tracked = git("ls-files", "-z", "windows/").split(b"\0")
    has_code = any(p.endswith((b".cs", b".csproj")) for p in tracked)
    required = ["windows/BluetoothDisable.sln", "windows/global.json",
                "windows/BluetoothDisable.App/BluetoothDisable.App.csproj",
                "windows/BluetoothDisable.Core/BluetoothDisable.Core.csproj",
                "windows/BluetoothDisable.Tests/BluetoothDisable.Tests.csproj"]
    complete = all(Path(p).is_file() for p in required)
    if has_code and not complete:
        raise ValueError("Windows source exists but the solution/SDK/projects are incomplete")
    return complete


def main():
    event_name = os.environ["GITHUB_EVENT_NAME"]
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    paths = changed_paths(event_name, event)
    android, windows = (True, True) if paths is None else classify(paths)
    available = windows_available()
    if windows and not available:
        print("Windows scaffold only: Windows jobs wait for the solution from PR #69.")
    windows = windows and available
    outputs = {"android": android, "windows": windows,
               "android_tests": android and event_name != "schedule",
               "windows_tests": windows and event_name != "schedule"}
    print(json.dumps({"paths": paths, "windows_available": available, **outputs}, indent=2))
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        for key, value in outputs.items():
            output.write(f"{key}={str(value).lower()}\n")


if __name__ == "__main__":
    main()
