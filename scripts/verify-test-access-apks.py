"""Check actual APK manifests and DEX files, not just source-set declarations."""

import os
import re
import subprocess
import zipfile
from pathlib import Path


def verify() -> None:
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    tools = sorted((sdk / "build-tools").glob("*/aapt2"))
    if not tools:
        raise RuntimeError("Android aapt2 was not found")
    aapt = tools[-1]
    for variant in ("debug", "internal", "release"):
        apks = list(Path(f"app/build/outputs/apk/{variant}").glob("*.apk"))
        if len(apks) != 1:
            raise RuntimeError(f"Expected one {variant} APK, found {len(apks)}")
        apk = apks[0]
        permissions = subprocess.check_output([aapt, "dump", "permissions", apk], text=True)
        has_internet = "android.permission.INTERNET" in permissions
        assert has_internet == (variant == "internal"), f"Unexpected INTERNET permission in {variant}"
        badging = subprocess.check_output([aapt, "dump", "badging", apk], text=True)
        package = re.search(r"package: name='([^']+)'", badging).group(1)
        expected = "com.pulse.bluetoothdisable" + (".internal" if variant == "internal" else "")
        assert package == expected, f"Incorrect application ID in {variant}: {package}"
        manifest = subprocess.check_output([aapt, "dump", "xmltree", apk, "--file", "AndroidManifest.xml"], text=True)
        for suffix in ("", "Calculator", "Notes", "Calendar", "Gallery"):
            assert f"{package}.LauncherAlias{suffix}" in manifest, f"Incorrect launcher alias in {variant}"
        with zipfile.ZipFile(apk) as archive:
            dex = b"".join(archive.read(name) for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name))
        for marker in (b"https://bluetoothdisable.app/api/test-config", b"InternalTestTokenStore", b"TestConfigClient"):
            assert (marker in dex) == (variant == "internal"), f"Test access source-set isolation failed: {variant}, {marker!r}"
        print(f"Verified {variant}: package, aliases, INTERNET and test-client isolation")


if __name__ == "__main__":
    verify()
