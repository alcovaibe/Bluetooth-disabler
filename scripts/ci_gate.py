#!/usr/bin/env python3
"""Require success from selected jobs and an explicit skip from unselected jobs."""
import json
import os
import sys


def evaluate(needs, event_name):
    if needs["changes"]["result"] != "success":
        return ["Change detection failed, was cancelled, or was skipped"]
    outputs = needs["changes"]["outputs"]
    keys = ("android", "windows", "android_tests", "windows_tests")
    if any(outputs.get(key) not in {"true", "false"} for key in keys):
        return ["Change detection did not return valid platform flags"]
    flags = {key: outputs[key] == "true" for key in keys}
    selected = {
        "android_pr": flags["android_tests"] and event_name != "push",
        "android_main": flags["android_tests"] and event_name == "push",
        "windows": flags["windows_tests"],
        "codeql": flags["android"] or flags["windows"],
        "android_license": flags["android"],
        "windows_audit": flags["windows"],
        "android_version": flags["android"] and event_name != "schedule",
    }
    errors = []
    for job, enabled in selected.items():
        actual = needs.get(job, {}).get("result", "missing")
        expected = "success" if enabled else "skipped"
        print(f"{job}: selected={enabled}, result={actual}, expected={expected}")
        if actual != expected:
            errors.append(f"{job}: expected {expected}, got {actual}")
    return errors


if __name__ == "__main__":
    errors = evaluate(json.loads(os.environ["CI_NEEDS"]), os.environ["GITHUB_EVENT_NAME"])
    for error in errors:
        print(f"::error::{error}")
    sys.exit(bool(errors))
