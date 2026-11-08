#!/usr/bin/env python3
"""Reuse one SHA-256-verified Gradle zip across Android CI emulator jobs."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import sys
import time

PROPERTIES = Path("gradle/wrapper/gradle-wrapper.properties")


def read_properties():
    content = PROPERTIES.read_text(encoding="utf-8")
    entries = dict(line.split("=", 1) for line in content.splitlines()
                   if "=" in line and not line.lstrip().startswith("#"))
    url = entries["distributionUrl"].replace("\\:", ":")
    archive = url.rsplit("/", 1)[-1]
    version = re.fullmatch(r"gradle-(\d[\w.-]*)-bin\.zip", archive)
    digest = entries["distributionSha256Sum"].lower()
    if not version or not re.fullmatch(r"[0-9a-f]{64}", digest):
        raise ValueError("Unexpected Gradle archive or invalid SHA-256 pin")
    return content, url, archive, version.group(1), digest


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(directory):
    _, original, archive, version, expected = read_properties()
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / archive
    if target.is_file() and digest(target) == expected:
        print("Existing distribution passed SHA-256 verification")
        return
    target.unlink(missing_ok=True)
    staging = directory / (archive + ".part")
    urls = list(dict.fromkeys([
        original,
        f"https://downloads.gradle.org/distributions/{archive}",
        f"https://services.gradle.org/distributions/{archive}",
        f"https://github.com/gradle/gradle-distributions/releases/download/v{version}/{archive}",
    ]))
    for attempt in range(1, 16):
        staging.unlink(missing_ok=True)
        url = urls[(attempt - 1) % len(urls)]
        print(f"Gradle fetch {attempt}/15: {url}", flush=True)
        outcome = subprocess.run([
            "curl", "--location", "--fail", "--silent", "--show-error",
            "--connect-timeout", "20", "--max-time", "180",
            "--output", str(staging), url,
        ], check=False)
        if outcome.returncode == 0 and staging.is_file():
            actual = digest(staging)
            if actual == expected:
                staging.replace(target)
                print(f"Gradle verified: {archive} SHA-256={expected}", flush=True)
                return
            print(f"SHA-256 mismatch ({actual}); refusing archive", file=sys.stderr)
        else:
            print(f"Download failed (curl exit {outcome.returncode})", file=sys.stderr)
        staging.unlink(missing_ok=True)
        if attempt < 15:
            time.sleep(min(5 * attempt, 40))
    raise RuntimeError("All Gradle distribution download attempts failed")


def use(directory):
    original, _, archive, _, expected = read_properties()
    local = (directory / archive).resolve(strict=True)
    if digest(local) != expected:
        raise ValueError("Gradle artifact checksum mismatch")
    url = local.as_uri().replace(":", r"\:", 1)
    updated, count = re.subn(
        r"(?m)^distributionUrl=.*$",
        lambda _: "distributionUrl=" + url,
        original,
    )
    if count != 1:
        raise ValueError("Gradle wrapper must have one distributionUrl")
    PROPERTIES.write_text(updated, encoding="utf-8")
    print(f"Using verified local Gradle distribution: {local}")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("action", choices=["fetch", "use"])
    p.add_argument("directory", type=Path)
    args = p.parse_args()
    (fetch if args.action == "fetch" else use)(args.directory)
