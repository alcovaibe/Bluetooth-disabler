"""Offline publisher tests: APK evidence, live baseline and publication race guards."""
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
from email.message import Message

import android_updates as updates


class PublisherTests(unittest.TestCase):
    def release(self):
        tag = "v1.2.0"
        return {"id": 7, "tag_name": tag, "draft": False, "prerelease": False,
                "published_at": "2026-10-15T08:00:00Z", "html_url": f"https://github.com/{updates.REPOSITORY}/releases/tag/{tag}",
                "body": '<!-- android-update-notes\n{"ru":"Исправлено отображение фотографий.","en":"Fixed photo rendering."}\n-->',
                "assets": [{"id": 8, "name": "BluetoothDisable-v1.2.0.apk", "state": "uploaded", "size": 100,
                            "browser_download_url": f"https://github.com/{updates.REPOSITORY}/releases/download/{tag}/BluetoothDisable-{tag}.apk"}]}

    def evidence(self):
        certificate = b"release public certificate fixture"
        badging = "package: name='com.pulse.bluetoothdisable' versionCode='50' versionName='1.2.0'\nsdkVersion:'26'\n"
        return self.release(), badging, [certificate], hashlib.sha256(certificate).hexdigest()

    def manifest(self, code=50):
        result = updates.release_from_evidence(*self.evidence())
        result["release"]["versionCode"] = code
        return result

    def test_manifest_comes_from_apk_evidence_and_localized_summary(self):
        value = updates.release_from_evidence(*self.evidence())
        self.assertEqual(value["release"]["versionCode"], 50)
        self.assertEqual(value["release"]["minSdk"], 26)
        self.assertEqual(value["release"]["notes"]["en"], "Fixed photo rendering.")
        self.assertEqual(updates.strict_json(updates.encode(value)), value)

    def test_draft_prerelease_unpublished_and_missing_apk_are_rejected(self):
        for key, value in (("draft", True), ("prerelease", True), ("published_at", None), ("assets", [])):
            release = self.release(); release[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): updates.release_asset(release)
        for key, value in (("state", "new"), ("size", 0), ("size", updates.MAX_APK + 1), ("browser_download_url", "https://evil.example/a.apk")):
            release = self.release(); release["assets"][0][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): updates.release_asset(release)

    def test_ambiguous_and_foreign_release_assets_are_rejected(self):
        for key, value in (("tag_name", "v1.2.0;evil"), ("html_url", "https://github.com/other/repo/releases/tag/v1.2.0")):
            release = self.release(); release[key] = value
            with self.assertRaises(ValueError): updates.release_asset(release)
        release = self.release(); release["assets"] *= 2
        with self.assertRaises(ValueError): updates.release_asset(release)

    def test_wrong_apk_package_tag_version_debuggability_and_signer_are_rejected(self):
        for old, new in (("com.pulse.bluetoothdisable", "com.pulse.bluetoothdisable.internal"),
                         ("versionName='1.2.0'", "versionName='1.2.1'"), ("versionCode='50'", "versionCode='0'"),
                         ("sdkVersion:'26'", "sdkVersion:'0'"), ("sdkVersion:'26'", "application-debuggable\nsdkVersion:'26'")):
            args = list(self.evidence()); args[1] = args[1].replace(old, new)
            with self.subTest(new=new), self.assertRaises(ValueError): updates.release_from_evidence(*args)
        for certificates in ([], [b"wrong"], [self.evidence()[2][0], b"extra"]):
            args = list(self.evidence()); args[2] = certificates
            with self.assertRaises(ValueError): updates.release_from_evidence(*args)

    def test_1_2_requires_actual_bilingual_summary_not_an_invented_changelog(self):
        args = list(self.evidence()); args[0]["body"] = "General release text"
        with self.assertRaises(ValueError): updates.release_from_evidence(*args)
        for body in ('{"ru":"Исправления"}', '{"ru":"Исправления","en":""}', '{"ru":"A","ru":"B","en":"C"}'):
            args = list(self.evidence()); args[0]["body"] = "<!-- android-update-notes\n" + body + "\n-->"
            with self.assertRaises(ValueError): updates.release_from_evidence(*args)

    def test_old_rerun_and_equal_version_preserve_live_manifest(self):
        live = self.manifest(51)
        self.assertIs(updates.monotonic(live, self.manifest(50)), live)
        self.assertIs(updates.monotonic(live, self.manifest(51)), live)
        candidate = self.manifest(52)
        self.assertIs(updates.monotonic(live, candidate), candidate)
        self.assertIs(updates.monotonic(live, {**updates.BASE, "release": None}), live)
        bad = self.manifest(51); bad["release"]["versionName"] = "1.2.1"
        bad["release"]["releaseUrl"] = f"https://github.com/{updates.REPOSITORY}/releases/tag/v1.2.1"
        with self.assertRaises(ValueError): updates.monotonic(live, bad)

    def test_only_first_deployment_404_can_use_empty_baseline(self):
        for status in (403, 500, 302):
            with patch.object(updates, "read_url", side_effect=urllib.error.HTTPError(updates.MANIFEST_URL, status, "fixture", {}, None)):
                with self.assertRaises(urllib.error.HTTPError): updates.baseline()
        with patch.object(updates, "read_url", side_effect=urllib.error.HTTPError(updates.MANIFEST_URL, 404, "fixture", {}, None)):
            self.assertIsNone(updates.baseline()["release"])
        with patch.object(updates, "read_url", side_effect=TimeoutError()):
            with self.assertRaises(TimeoutError): updates.baseline()

    def test_postdeploy_404_is_never_reported_as_success(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "android.json"
            manifest.write_bytes(updates.encode({**updates.BASE, "release": None}))
            with patch.object(updates, "OUTPUT", manifest), patch.object(updates.time, "sleep"), patch.object(
                    updates, "read_url", side_effect=urllib.error.HTTPError(updates.MANIFEST_URL, 404, "fixture", {}, None)):
                with self.assertRaises(urllib.error.HTTPError): updates.verify_live()

    def test_strict_schema_rejects_duplicate_keys_types_and_invalid_targets(self):
        with self.assertRaises(ValueError): updates.strict_json(b'{"a":null,"a":1}')
        for key, value in (("versionCode", True), ("versionCode", 2147483648), ("minSdk", "26"),
                           ("releaseUrl", "javascript:alert(1)"), ("notes", {"ru": "x", "en": "x" * 1501})):
            item = self.manifest(); item["release"][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): updates.validate_manifest(item)

    def test_every_download_redirect_authority_is_validated(self):
        for address in ("http://github.com/a", "https://github.com.evil.example/a", "https://user@github.com/a",
                        "https://github.com:444/a", "https://github.com\\@evil.example/a", "https://127.0.0.1/a"):
            with self.assertRaises(ValueError): updates.url(address, {"github.com"})
        self.assertEqual(updates.url("https://release-assets.githubusercontent.com/a?sig=value", {"release-assets.githubusercontent.com"}).hostname,
                         "release-assets.githubusercontent.com")

    def test_redirect_destination_is_rejected_before_any_second_request(self):
        initial = self.release()["assets"][0]["browser_download_url"]
        for target in ("http://github.com/a.apk", "https://evil.example/a.apk", "https://user@github.com/a.apk"):
            headers = Message(); headers["Location"] = target
            with patch.object(updates.urllib.request, "build_opener") as factory:
                factory.return_value.open.side_effect = urllib.error.HTTPError(initial, 302, "fixture", headers, io.BytesIO())
                with self.assertRaises(ValueError): updates.read_url(initial, 1000, allow_asset_redirects=True)
                self.assertEqual(factory.return_value.open.call_count, 1)

    def test_api_and_manifest_redirects_are_never_followed(self):
        for address, kwargs in ((updates.API + "/releases/latest", {"api": True}), (updates.MANIFEST_URL, {})):
            with patch.object(updates.urllib.request, "build_opener") as factory:
                factory.return_value.open.side_effect = urllib.error.HTTPError(address, 302, "fixture", {"Location": "https://evil.example"}, None)
                with self.assertRaises(urllib.error.HTTPError): updates.read_url(address, 1000, **kwargs)
                self.assertEqual(factory.return_value.open.call_count, 1)

    def test_no_workflow_token_is_sent_to_public_site_or_apk_host(self):
        class Reply(io.BytesIO):
            status = 200
            headers = Message()
        Reply.headers["Content-Type"] = "application/json"
        for address, kwargs in ((updates.MANIFEST_URL, {}), (self.release()["assets"][0]["browser_download_url"], {"allow_asset_redirects": True})):
            with patch.dict(updates.os.environ, {"GITHUB_TOKEN": "fixture-not-a-real-token"}), patch.object(updates.urllib.request, "build_opener") as factory:
                factory.return_value.open.return_value = Reply(b"{}")
                self.assertEqual(updates.read_url(address, 1000, **kwargs), b"{}")
                request = factory.return_value.open.call_args.args[0]
                self.assertFalse(request.has_header("Authorization"))

    def test_failed_build_or_wrong_commit_cannot_publish(self):
        sha = "a" * 40
        for runs in ([], [{"head_sha": "b" * 40, "conclusion": "success", "event": "push"}],
                     [{"head_sha": sha, "conclusion": "failure", "event": "push"}]):
            with patch.object(updates, "api", side_effect=[{"object": {"type": "commit", "sha": sha}}, {"workflow_runs": runs}]):
                with self.assertRaises(ValueError): updates.require_successful_build("v1.2.0")
        with patch.object(updates, "api", side_effect=[{"object": {"type": "commit", "sha": sha}},
                          {"workflow_runs": [{"head_sha": sha, "conclusion": "success", "event": "push"}]}]):
            updates.require_successful_build("v1.2.0")

    def test_generation_fails_closed_when_apk_not_ready(self):
        release = self.release(); release["assets"] = []
        with patch.object(updates, "baseline", return_value=self.manifest()), patch.object(updates, "api", return_value=release), patch.object(updates, "read_url") as download:
            with self.assertRaises(ValueError): updates.generate()
            download.assert_not_called()

    def test_initial_state_does_not_announce_unverified_legacy_release(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "android.json"
            empty = {**updates.BASE, "release": None}
            release = self.release(); release["tag_name"] = "v1.1.0"
            with patch.object(updates, "OUTPUT", output), patch.object(updates, "baseline", return_value=empty), patch.object(
                    updates, "api", return_value=release), patch.object(updates, "require_successful_build") as build, patch.object(updates, "read_url") as download:
                updates.generate()
                self.assertEqual(updates.strict_json(output.read_bytes()), empty)
                build.assert_not_called()
                download.assert_not_called()

    def test_workflow_uses_serialized_latest_main_and_postdeploy_check(self):
        workflow = (updates.ROOT / ".github/workflows/pages.yml").read_text()
        self.assertIn("cancel-in-progress: false", workflow)
        self.assertIn("ref: main", workflow)
        self.assertIn("workflows: ['Build and Release']", workflow)
        self.assertIn("python3 scripts/android_updates.py --verify-live", workflow)
        self.assertLess(workflow.index("run: python3 scripts/android_updates.py\n"), workflow.index("command: deploy"))
        self.assertIsNone(updates.validate_manifest(updates.strict_json(updates.OUTPUT.read_bytes()))["release"])


if __name__ == "__main__":
    unittest.main()
