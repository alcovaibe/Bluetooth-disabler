"""Regression tests for CI routing, gate failure handling, and NuGet audit evidence."""
import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import audit_nuget
import ci_changes
import ci_gate


class RoutingTests(unittest.TestCase):
    def test_windows_only_does_not_select_android(self):
        self.assertEqual(ci_changes.classify(["windows/BluetoothDisable.Core/Models/Radio.cs"]), (False, True))

    def test_android_only_does_not_select_windows(self):
        self.assertEqual(ci_changes.classify(["app/src/main/Example.kt"]), (True, False))

    def test_windows_workflow_and_audit(self):
        self.assertEqual(ci_changes.classify([".github/workflows/windows.yml", "scripts/audit_nuget.py"]), (False, True))

    def test_android_build_scripts(self):
        self.assertEqual(ci_changes.classify(["gradle/libs.versions.toml", "scripts/run_android_tests.sh"]), (True, False))

    def test_docs_and_site_do_not_select_platforms(self):
        self.assertEqual(ci_changes.classify(["windows/README.md", "docs/pages/worker.js", "wrangler.jsonc"]), (False, False))

    def test_shared_or_unknown_paths_select_both(self):
        for path in [".gitignore", "scripts/ci_changes.py", "future-platform/main.rs"]:
            with self.subTest(path=path):
                self.assertEqual(ci_changes.classify([path]), (True, True))

    def test_mixed_diff(self):
        self.assertEqual(ci_changes.classify(["app/a.kt", "windows/a.cs"]), (True, True))

    def test_platform_change_after_many_docs_is_not_truncated(self):
        self.assertEqual(ci_changes.classify([f"docs/{i}.md" for i in range(3500)] + ["windows/a.cs"]), (False, True))

    def test_git_diff_includes_deletion_and_rename_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            def git(*args):
                return subprocess.check_output(["git", "-C", directory, *args], stderr=subprocess.DEVNULL).decode().strip()
            git("init"); git("config", "user.name", "CI test"); git("config", "user.email", "ci@example.invalid")
            root = Path(directory); (root / "windows").mkdir(); (root / "docs").mkdir()
            (root / "windows/radio.cs").write_text("old source")
            git("add", "."); git("commit", "-m", "base"); base = git("rev-parse", "HEAD")
            (root / "windows/radio.cs").rename(root / "docs/radio.md")
            git("add", "-A"); git("commit", "-m", "move"); head = git("rev-parse", "HEAD")
            original = ci_changes.git
            with patch.object(ci_changes, "git", side_effect=lambda *args: subprocess.check_output(["git", "-C", directory, *args])):
                paths = ci_changes.changed_paths("push", {"before": base, "after": head})
            self.assertCountEqual(paths, ["windows/radio.cs", "docs/radio.md"])
            self.assertEqual(ci_changes.classify(paths), (False, True))

    def test_scaffold_inventory_and_incomplete_solution(self):
        with tempfile.TemporaryDirectory() as directory:
            old = os.getcwd()
            try:
                os.chdir(directory)
                with patch.object(ci_changes, "git", return_value=b"windows/installer/.gitkeep\0"):
                    self.assertFalse(ci_changes.windows_available())
                with patch.object(ci_changes, "git", return_value=b"windows/App/Radio.cs\0"):
                    with self.assertRaises(ValueError): ci_changes.windows_available()
            finally:
                os.chdir(old)


class GateTests(unittest.TestCase):
    def result(self, android=False, windows=False, event="pull_request"):
        tests = event != "schedule"
        result = {"changes": {"result": "success", "outputs": {
            "android": str(android).lower(), "windows": str(windows).lower(),
            "android_tests": str(android and tests).lower(), "windows_tests": str(windows and tests).lower()}}}
        selected = {"android_pr": android and tests and event != "push",
                    "android_main": android and tests and event == "push", "windows": windows and tests,
                    "codeql": android or windows, "android_license": android, "windows_audit": windows,
                    "android_version": android and tests}
        result.update({k: {"result": "success" if v else "skipped"} for k, v in selected.items()})
        return result

    def evaluate(self, value, event="pull_request"):
        with contextlib.redirect_stdout(io.StringIO()): return ci_gate.evaluate(value, event)

    def test_every_valid_platform_combination_and_event(self):
        for android, windows in [(False, False), (True, False), (False, True), (True, True)]:
            for event in ["pull_request", "push", "schedule", "workflow_dispatch", "merge_group"]:
                with self.subTest(android=android, windows=windows, event=event):
                    self.assertFalse(self.evaluate(self.result(android, windows, event), event))

    def test_selected_failure_cancellation_or_skip_fails(self):
        for status in ["failure", "cancelled", "skipped", "missing"]:
            for job in ["windows", "windows_audit", "codeql"]:
                value = self.result(windows=True); value[job]["result"] = status
                with self.subTest(status=status, job=job): self.assertTrue(self.evaluate(value))

    def test_detector_failure_fails_even_if_all_other_jobs_skip(self):
        value = self.result(); value["changes"]["result"] = "failure"
        self.assertTrue(self.evaluate(value))

    def test_missing_or_invalid_detector_flags_fail(self):
        value = self.result(); del value["changes"]["outputs"]["windows"]
        self.assertTrue(self.evaluate(value))

    def test_unrelated_job_running_is_not_accepted(self):
        value = self.result(windows=True); value["android_pr"]["result"] = "success"
        self.assertTrue(self.evaluate(value))


class NuGetTests(unittest.TestCase):
    def report(self, packages=None):
        return {"version": 1, "projects": [{"path": "App.csproj", "frameworks": [{
            "framework": "net10.0", "transitivePackages": packages or []}]}]}

    def test_valid_clean_report(self):
        self.assertEqual(audit_nuget.vulnerabilities(self.report()), [])

    def test_transitive_low_severity_also_fails(self):
        package = {"id": "Dependency", "resolvedVersion": "1.0", "vulnerabilities": [
            {"severity": "Low", "advisoryurl": "https://example.invalid/advisory"}]}
        self.assertEqual(len(audit_nuget.vulnerabilities(self.report([package]))), 1)

    def test_missing_or_wrong_schema_is_rejected(self):
        for value in [{}, {"version": 2, "projects": []}, {"version": 1, "projects": []}]:
            with self.assertRaises(ValueError): audit_nuget.vulnerabilities(value)

    def test_audit_source_error_cannot_look_clean(self):
        value = self.report(); value["logs"] = [{"level": "Error", "message": "Audit source unavailable"}]
        self.assertTrue(audit_nuget.vulnerabilities(value))

    def test_license_file_is_preserved_and_path_traversal_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); (root / "LICENSE.txt").write_text("custom terms")
            spec = root / "package.nuspec"
            spec.write_text('<package><metadata><license type="file">LICENSE.txt</license></metadata></package>')
            text, file, review = audit_nuget.package_license(root)
            self.assertTrue(review); self.assertIn("SHA256", text); self.assertEqual(file, root / "LICENSE.txt")
            spec.write_text('<package><metadata><license type="file">../outside</license></metadata></package>')
            with self.assertRaises(ValueError): audit_nuget.package_license(root)

    def test_empty_license_metadata_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); (root / "p.nuspec").write_text('<package><metadata/></package>')
            with self.assertRaises(ValueError): audit_nuget.package_license(root)

    def test_complete_assets_inventory_contains_transitive_packages(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); obj = root / "App/obj"; obj.mkdir(parents=True)
            (root / "App/App.csproj").write_text('<Project/>')
            cache = root / "cache"; package = cache / "dependency/1.0"; package.mkdir(parents=True)
            (package / "dependency.nuspec").write_text('<package><metadata><license type="expression">MIT</license></metadata></package>')
            (obj / "project.assets.json").write_text(json.dumps({"packageFolders": {str(cache): {}},
                "libraries": {"Dependency/1.0": {"type": "package", "path": "dependency/1.0"}}}))
            rows, reviews, problems = audit_nuget.inventory(root, root / "evidence")
            self.assertEqual(len(rows), 1); self.assertIn("Dependency/1.0", rows[0]); self.assertFalse(problems)


if __name__ == "__main__":
    unittest.main()
