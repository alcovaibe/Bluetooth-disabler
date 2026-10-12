#!/usr/bin/env python3
"""Fail on known vulnerabilities/missing metadata; inventory resolved NuGet licenses.

License inventory is evidence, not an automatic legal compatibility decision.
Custom license files and legacy URLs remain explicit release-review items.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import xml.etree.ElementTree as ET


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def vulnerabilities(report):
    if report.get("version") != 1 or not isinstance(report.get("projects"), list) or not report["projects"]:
        raise ValueError("Missing/unsupported vulnerability report schema or empty project list")
    problems = []
    for log in report.get("logs", []):
        if str(log.get("level", "")).lower() in {"error", "warning"}:
            problems.append("NuGet report: " + str(log.get("message", log)))
    for project in report["projects"]:
        if not project.get("path") or not isinstance(project.get("frameworks"), list) or not project["frameworks"]:
            raise ValueError("Incomplete project in vulnerability report")
        for framework in project["frameworks"]:
            for kind in ("topLevelPackages", "transitivePackages"):
                for package in framework.get(kind, []):
                    for issue in package.get("vulnerabilities", []):
                        problems.append(f"{package['id']} {package['resolvedVersion']}: "
                                        f"{issue['severity']} {issue['advisoryurl']}")
    return problems


def package_license(package_dir):
    specs = list(package_dir.glob("*.nuspec"))
    if len(specs) != 1:
        raise ValueError(f"Expected one nuspec in {package_dir}")
    tree = ET.parse(specs[0])
    metadata = next((e for e in tree.getroot() if e.tag.split('}')[-1] == "metadata"), None)
    if metadata is None:
        raise ValueError(f"Missing nuspec metadata in {specs[0]}")
    values = {e.tag.split('}')[-1]: e for e in metadata}
    license_node = values.get("license")
    if license_node is not None and (license_node.text or "").strip():
        text = license_node.text.strip()
        kind = license_node.get("type")
        if kind == "expression":
            return f"SPDX expression: {text}", None, False
        if kind == "file":
            path = (package_dir / text.replace("\\", "/")).resolve()
            if not path.is_relative_to(package_dir.resolve()) or not path.is_file():
                raise ValueError(f"Missing or unsafe license file: {text}")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            return f"Custom/file license: {text}; SHA256 {digest}", path, True
        raise ValueError(f"Unsupported NuGet license type: {kind}")
    legacy = values.get("licenseUrl")
    if legacy is not None and (legacy.text or "").strip():
        return "Legacy license URL: " + legacy.text.strip(), None, True
    raise ValueError(f"No license expression, file, or legacy license URL in {specs[0]}")


def inventory(root, evidence_dir):
    assets = sorted(root.glob("*/obj/project.assets.json"))
    projects = sorted(root.glob("*/*.csproj"))
    if not projects or len(assets) != len(projects):
        raise ValueError("Missing restore assets for one or more Windows projects")
    packages = {}
    for asset in assets:
        data = read_json(asset)
        folders = [Path(p) for p in data["packageFolders"]]
        for identity, info in data["libraries"].items():
            if info.get("type") != "package":
                continue
            dirs = [(folder / info["path"]).resolve() for folder in folders]
            package_dir = next((p for p, base in zip(dirs, folders)
                                if p.is_relative_to(base.resolve()) and p.is_dir()), None)
            if package_dir is None:
                raise ValueError(f"Package missing from restore cache: {identity}")
            packages.setdefault(identity, (package_dir, set()))[1].add(asset.parent.parent.name)
    if not packages:
        raise ValueError("Restore assets contain no NuGet packages")
    rows, reviews, problems = [], [], []
    for identity, (directory, used_by) in sorted(packages.items()):
        try:
            license_text, license_file, review = package_license(directory)
            if license_file:
                target = evidence_dir / re.sub(r"[^A-Za-z0-9._-]", "_", identity)
                target.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(license_file, target / "LICENSE.txt")
            if review:
                reviews.append(identity)
        except (ValueError, OSError, ET.ParseError) as error:
            license_text = "MISSING/INVALID: " + str(error)
            problems.append(f"{identity}: {error}")
        def cell(value):
            return str(value).replace("|", "\\|").replace("\n", " ").replace("\r", " ")
        rows.append(f"| {cell(identity)} | {cell(', '.join(sorted(used_by)))} | {cell(license_text)} |")
    return rows, reviews, problems


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--vulnerabilities", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    problems, rows, reviews = [], [], []
    try:
        problems.extend(vulnerabilities(read_json(args.vulnerabilities)))
    except (ValueError, KeyError, TypeError, OSError) as error:
        problems.append(f"Cannot validate vulnerability report: {error}")
    try:
        rows, reviews, license_problems = inventory(args.root, args.report.parent / "license-files")
        problems.extend(license_problems)
    except (ValueError, KeyError, TypeError, OSError) as error:
        problems.append(f"Cannot inventory restored packages: {error}")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    lines = ["# Windows NuGet dependency audit", "",
             "Scope: direct and transitive PackageReference packages from fresh solution restore assets.",
             "Known vulnerabilities at every reported severity fail CI. Missing audit data or license metadata fails CI.",
             "This license inventory does not certify redistribution or GPL compatibility.", "",
             "| Package/version | Projects | License metadata |", "| --- | --- | --- |", *rows, "",
             "## Custom/legacy terms requiring release review", "",
             *([f"- {p}" for p in reviews] or ["None reported."]), "",
             "## CI result", "", *([f"- {p}" for p in problems] or ["PASS: vulnerability report and license metadata validated."])]
    args.report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    for problem in problems:
        print(f"::error::{problem}")
    print(f"Audited {len(rows)} packages; {len(reviews)} custom/legacy licenses; {len(problems)} problems.")
    return bool(problems)


if __name__ == "__main__":
    sys.exit(main())
