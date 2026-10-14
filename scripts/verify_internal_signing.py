"""Verify internal APK certificates through Android's binary ApkVerifier API."""
import base64
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile

# Use the verifier bundled with the installed SDK; no extra network dependency.
VERIFIER_SOURCE = """
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.cert.X509Certificate;
import java.util.Base64;
public class InternalApkCertificates {
    public static void main(String[] args) throws Exception {
        ApkVerifier.Result result = new ApkVerifier.Builder(new File(args[0])).build().verify();
        if (!result.isVerified()) throw new IllegalStateException("APK signature verification failed");
        for (X509Certificate certificate : result.getSignerCertificates()) {
            System.out.println(Base64.getEncoder().encodeToString(certificate.getEncoded()));
        }
    }
}
"""


def validate_evidence(badging: str, certificates: list[bytes], certificate: bytes, version_code: int) -> str:
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)'", badging)
    if not package or package.group(1) != "com.pulse.bluetoothdisable.internal":
        raise ValueError("APK is not the internal application")
    if int(package.group(2)) != version_code or version_code < 1_000_000:
        raise ValueError("APK does not have the expected increasing CI version code")
    expected = hashlib.sha256(certificate).hexdigest()
    actual = [hashlib.sha256(value).hexdigest() for value in certificates]
    if len(actual) != 1 or actual[0] != expected:
        raise ValueError(f"Persistent certificate mismatch: expected={expected}; APK certificates={actual}")
    return expected


def apk_certificates(verifier_jar: Path, apk: Path) -> list[bytes]:
    with tempfile.TemporaryDirectory(prefix="internal-apk-verifier-") as directory:
        source = Path(directory) / "InternalApkCertificates.java"
        source.write_text(VERIFIER_SOURCE)
        # Source-file mode uses JDK 17 and the SDK's own verification library.
        output = subprocess.check_output([
            "java", "-cp", str(verifier_jar), str(source), str(apk),
        ], text=True)
    return [base64.b64decode(line, validate=True) for line in output.splitlines() if line]


def verify() -> None:
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    tools = sorted((sdk / "build-tools").glob("*/lib/apksigner.jar"))
    apks = list(Path("app/build/outputs/apk/internal").glob("*.apk"))
    if not tools or len(apks) != 1:
        raise RuntimeError("Expected Android build tools and exactly one internal APK")
    jar = tools[-1]
    apk = apks[0]
    certificates = apk_certificates(jar, apk)
    certificate = subprocess.check_output([
        "keytool", "-exportcert", "-keystore", os.environ["INTERNAL_STORE_FILE"],
        "-alias", os.environ["INTERNAL_KEY_ALIAS"], "-storepass:env", "INTERNAL_STORE_PASSWORD",
    ])
    badging = subprocess.check_output([jar.parent.parent / "aapt2", "dump", "badging", apk], text=True)
    version = int(os.environ["INTERNAL_VERSION_CODE"])
    fingerprint = validate_evidence(badging, certificates, certificate, version)
    print(f"Verified persistent internal signature SHA-256: {fingerprint}; versionCode={version}")


if __name__ == "__main__":
    verify()
