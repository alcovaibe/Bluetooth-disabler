#!/usr/bin/env python3
"""Reject instrumentation runs that did not execute tests, even if Gradle succeeded."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    reports = Path("app/build/outputs/androidTest-results/connected")
    files = sorted(reports.rglob("TEST-*.xml"))
    if not files:
        print("No Android instrumentation test reports were produced.", file=sys.stderr)
        return 1

    executed = 0
    failures = 0
    for path in files:
        root = ET.parse(path).getroot()
        for case in root.iter("testcase"):
            if case.find("skipped") is not None:
                continue
            executed += 1
            if case.find("failure") is not None or case.find("error") is not None:
                failures += 1

    print(f"Android instrumentation: {executed} tests executed, {failures} failed.")
    if executed == 0 or failures:
        print("Android instrumentation results are incomplete or failing.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
