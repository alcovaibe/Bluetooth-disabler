"""Offline PR-CI integration check against the actual release APK and ephemeral CI key.

No GitHub release, production manifest, signing configuration or deployment is changed.
"""
import hashlib
import os
from pathlib import Path
import re
import subprocess

import android_updates as updates
from verify_internal_signing import apk_certificates


def verify():
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    jars = sorted((sdk / "build-tools").glob("*/lib/apksigner.jar"))
    apks = list(Path("app/build/outputs/apk/release").glob("*.apk"))
    if not jars or len(apks) != 1:
        raise ValueError("Expected SDK verification tools and one release APK")
    apk, jar = apks[0], jars[-1]
    badging = subprocess.check_output([str(jar.parent.parent / "aapt2"), "dump", "badging", str(apk)], text=True, timeout=60)
    name = re.search(r"^package: .* versionName='([^']+)'", badging, re.M)
    if not name:
        raise ValueError("APK versionName missing")
    tag = "v" + name[1]
    expected_certificate = subprocess.check_output([
        "keytool", "-exportcert", "-keystore", os.environ["RELEASE_STORE_FILE"],
        "-alias", os.environ["RELEASE_KEY_ALIAS"], "-storepass:env", "RELEASE_STORE_PASSWORD",
    ])
    fixture = {
        "tag_name": tag, "draft": False, "prerelease": False, "published_at": "offline-CI-fixture",
        "html_url": f"https://github.com/{updates.REPOSITORY}/releases/tag/{tag}",
        "body": '<!-- android-update-notes\n{"ru":"Проверка CI.","en":"CI fixture."}\n-->',
        "assets": [{"name": apk.name, "state": "uploaded", "size": apk.stat().st_size,
                    "browser_download_url": f"https://github.com/{updates.REPOSITORY}/releases/download/{tag}/{apk.name}"}],
    }
    manifest = updates.release_from_evidence(fixture, badging, apk_certificates(jar, apk), hashlib.sha256(expected_certificate).hexdigest())
    print("Offline actual-APK update schema/signature/package/version/minSdk check passed; versionCode:", manifest["release"]["versionCode"])


if __name__ == "__main__":
    verify()
