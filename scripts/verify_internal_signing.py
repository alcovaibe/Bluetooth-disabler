"""Verify the uploaded internal APK against the persistent signing certificate."""
import hashlib
import os
from pathlib import Path
import re
import subprocess


def validate_evidence(badging: str, signature: str, certificate: bytes, version_code: int) -> str:
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)'", badging)
    if not package or package.group(1) != "com.pulse.bluetoothdisable.internal":
        raise ValueError("APK is not the internal application")
    if int(package.group(2)) != version_code or version_code < 1_000_000:
        raise ValueError("APK does not have the expected increasing CI version code")
    expected = hashlib.sha256(certificate).hexdigest()
    # SDK 37 reports v3.1 certificates by API range, rather than "Signer #1".
    # The same certificate can appear for both v3 and v3.1; every one must match.
    signers = re.findall(
        r"^Signer(?: #\d+| \(minSdkVersion=[^\r\n]+\)) certificate SHA-256 digest: ([0-9a-fA-F]{64})$",
        signature, re.MULTILINE,
    )
    counts = re.findall(r"^Number of signers: (\d+)$", signature, re.MULTILINE)
    actual = {value.lower() for value in signers}
    if not signers or actual != {expected} or any(int(value) != 1 for value in counts):
        raise ValueError(f"Persistent certificate mismatch: expected={expected}; APK certificates={sorted(actual)}")
    return expected


def verify() -> None:
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    tools = sorted((sdk / "build-tools").glob("*/apksigner"))
    apks = list(Path("app/build/outputs/apk/internal").glob("*.apk"))
    if not tools or len(apks) != 1:
        raise RuntimeError("Expected Android build tools and exactly one internal APK")
    tool = tools[-1]
    apk = apks[0]
    signature = subprocess.check_output([tool, "verify", "--verbose", "--print-certs", apk], text=True)
    certificate = subprocess.check_output([
        "keytool", "-exportcert", "-keystore", os.environ["INTERNAL_STORE_FILE"],
        "-alias", os.environ["INTERNAL_KEY_ALIAS"], "-storepass:env", "INTERNAL_STORE_PASSWORD",
    ])
    badging = subprocess.check_output([tool.parent / "aapt2", "dump", "badging", apk], text=True)
    version = int(os.environ["INTERNAL_VERSION_CODE"])
    fingerprint = validate_evidence(badging, signature, certificate, version)
    print(f"Verified persistent internal signature SHA-256: {fingerprint}; versionCode={version}")


if __name__ == "__main__":
    verify()
