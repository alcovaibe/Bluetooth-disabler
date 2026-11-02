#!/usr/bin/env python3
"""Generate the public stable manifest only from a published, verified release APK.

No deployment, release creation, repository writes or signing keys are used here.
The serialized site workflow owns deployment and checks the live monotonic baseline.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

from verify_internal_signing import apk_certificates

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "alcovaibe/Bluetooth-disabler"
API = "https://api.github.com/repos/" + REPOSITORY
MANIFEST_URL = "https://bluetoothdisable.app/updates/android.json"
OUTPUT = ROOT / "docs/pages/updates/android.json"
MAX_JSON = 16384
MAX_APK = 200 * 1024 * 1024
VERSION = r"[0-9]{1,6}(?:\.[0-9]{1,6}){1,3}"
BASE = {"schemaVersion": 1, "platform": "android", "channel": "stable", "applicationId": "com.pulse.bluetoothdisable"}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def url(value, hosts):
    if not isinstance(value, str) or len(value) > 8192 or re.search(r"[\x00-\x20\x7f\\]", value):
        raise ValueError("Invalid URL")
    parsed = urllib.parse.urlsplit(value)
    if (parsed.scheme != "https" or parsed.hostname not in hosts or parsed.username is not None
            or parsed.password is not None or parsed.port not in (None, 443) or parsed.fragment):
        raise ValueError("Unapproved URL authority")
    if parsed.netloc not in {parsed.hostname, parsed.hostname + ":443"}:
        raise ValueError("Ambiguous URL authority")
    return parsed


def read_url(address, maximum, *, api=False, allow_asset_redirects=False):
    hosts = {"api.github.com"} if api else {"bluetoothdisable.app"}
    if allow_asset_redirects:
        hosts = {"github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"}
    deadline = time.monotonic() + 120
    opener = urllib.request.build_opener(NoRedirect())
    for _ in range(6):
        parsed = url(address, hosts)
        headers = {"Accept": "application/vnd.github+json" if api else "application/json",
                   "User-Agent": "Bluetooth-Disable-Update-Publisher/1", "Cache-Control": "no-cache"}
        # Never forward the workflow credential to a download host or the public site.
        if api and parsed.hostname == "api.github.com" and os.environ.get("GITHUB_TOKEN"):
            headers["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]
        try:
            response = opener.open(urllib.request.Request(address, headers=headers), timeout=15)
        except urllib.error.HTTPError as error:
            if allow_asset_redirects and error.code in (301, 302, 303, 307, 308):
                location = error.headers.get("Location")
                error.close()
                if not location:
                    raise ValueError("Missing APK redirect location")
                address = urllib.parse.urljoin(address, location)
                url(address, hosts)  # validate this hop before sending any request
                continue
            raise
        with response:
            if response.status != 200:
                raise ValueError("Unexpected HTTP status")
            if not api and not allow_asset_redirects and response.headers.get_content_type() != "application/json":
                raise ValueError("Manifest content type")
            length = response.headers.get("Content-Length")
            if length is not None and (not length.isdigit() or int(length) > maximum):
                raise ValueError("Response size limit")
            chunks, size = [], 0
            while True:
                if time.monotonic() > deadline:
                    raise TimeoutError("Response deadline")
                chunk = response.read(min(65536, maximum + 1 - size))
                if not chunk:
                    return b"".join(chunks)
                size += len(chunk)
                if size > maximum:
                    raise ValueError("Response size limit")
                chunks.append(chunk)
    raise ValueError("Too many APK redirects")


def strict_json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("Duplicate JSON key")
            result[key] = value
        return result
    return json.loads(raw.decode("utf-8", errors="strict"), object_pairs_hook=pairs,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError("JSON constant")))


def validate_manifest(value):
    if not isinstance(value, dict) or set(value) != {*BASE, "release"}:
        raise ValueError("Manifest fields")
    if type(value["schemaVersion"]) is not int or any(value[k] != v for k, v in BASE.items()):
        raise ValueError("Manifest identity")
    release = value["release"]
    if release is None:
        return value
    if not isinstance(release, dict) or set(release) != {"versionCode", "versionName", "minSdk", "releaseUrl", "notes"}:
        raise ValueError("Release fields")
    for key, maximum in (("versionCode", 2100000000), ("minSdk", 10000)):
        if type(release[key]) is not int or not 1 <= release[key] <= maximum:
            raise ValueError("Release integer")
    name = release["versionName"]
    if not isinstance(name, str) or not re.fullmatch(VERSION, name):
        raise ValueError("Version name")
    if release["releaseUrl"] != f"https://github.com/{REPOSITORY}/releases/tag/v{name}":
        raise ValueError("Release URL")
    notes = release["notes"]
    if not isinstance(notes, dict) or set(notes) != {"ru", "en"}:
        raise ValueError("Localized notes")
    for text in notes.values():
        if (not isinstance(text, str) or not text.strip() or len(text.encode("utf-16-le")) // 2 > 1500
                or re.search(r"[\x00-\x08\x0b-\x1f\x7f-\x9f\u202a-\u202e\u2066-\u2069]", text)):
            raise ValueError("Notes text")
    if len(encode(value)) > MAX_JSON:
        raise ValueError("Manifest size")
    return value


def encode(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def api(path):
    return strict_json(read_url(API + path, 4 * 1024 * 1024, api=True))


def baseline():
    try:
        return validate_manifest(strict_json(read_url(MANIFEST_URL, MAX_JSON)))
    except urllib.error.HTTPError as error:
        if error.code == 404:
            # First installation only. Other failures must not reset the live version.
            return {**BASE, "release": None}
        raise


def release_asset(release):
    if (not isinstance(release, dict) or release.get("draft") is not False or release.get("prerelease") is not False
            or not release.get("published_at") or not isinstance(release.get("tag_name"), str)
            or not re.fullmatch("v" + VERSION, release["tag_name"])):
        raise ValueError("Expected a published stable release")
    tag = release["tag_name"]
    if release.get("html_url") != f"https://github.com/{REPOSITORY}/releases/tag/{tag}":
        raise ValueError("Release repository mismatch")
    assets = [a for a in release.get("assets", []) if a.get("name") == f"BluetoothDisable-{tag}.apk"]
    if len(assets) != 1:
        raise ValueError("Expected exactly one ordinary release APK")
    asset = assets[0]
    if asset.get("state") != "uploaded" or type(asset.get("size")) is not int or not 0 < asset["size"] <= MAX_APK:
        raise ValueError("Release APK is not ready")
    expected = f"https://github.com/{REPOSITORY}/releases/download/{tag}/{asset['name']}"
    if asset.get("browser_download_url") != expected:
        raise ValueError("APK URL mismatch")
    return asset


def require_successful_build(tag):
    ref = api("/git/ref/tags/" + urllib.parse.quote(tag, safe=""))["object"]
    for _ in range(4):
        if ref.get("type") == "commit":
            break
        if ref.get("type") != "tag" or not re.fullmatch(r"[a-f0-9]{40}", ref.get("sha", "")):
            raise ValueError("Tag object")
        ref = api("/git/tags/" + ref["sha"])["object"]
    sha = ref.get("sha", "")
    if ref.get("type") != "commit" or not re.fullmatch(r"[a-f0-9]{40}", sha):
        raise ValueError("Tag commit")
    runs = api(f"/actions/workflows/release.yml/runs?head_sha={sha}&status=success&per_page=100")["workflow_runs"]
    if not any(run.get("head_sha") == sha and run.get("conclusion") == "success"
               and run.get("event") in ("push", "workflow_dispatch") for run in runs):
        raise ValueError("No successful release build for the tag commit")


def release_from_evidence(release, badging, certificates, expected_certificate):
    release_asset(release)
    package_line = re.search(r"^package: (.*)$", badging, re.M)
    pairs = re.findall(r"([A-Za-z][A-Za-z0-9]*)='([^']*)'", package_line[1]) if package_line else []
    package = dict(pairs)
    # SDK tool versions can add/reorder package fields (e.g. versionCodeMajor).
    # A zero minor SDK component is equivalent to its integer API level.
    sdk = re.search(r"^(?:sdkVersion|minSdkVersion):'(\d+)(?:\.0)?'\s*$", badging, re.M)
    debuggable = re.search(r"^application-debuggable(?:\s|:|$)", badging, re.M)
    if (len(package) != len(pairs) or package.get("name") != BASE["applicationId"] or not sdk or debuggable
            or package.get("versionCodeMajor", "0") != "0" or not re.fullmatch(r"[0-9]+", package.get("versionCode", ""))):
        raise ValueError("Not an ordinary non-debuggable APK: " + repr(package) + "; SDK line="
                         + repr([line for line in badging.splitlines() if "sdkVersion" in line or "SdkVersion" in line]))
    code, name = int(package["versionCode"]), package.get("versionName", "")
    if release["tag_name"] != "v" + name:
        raise ValueError("Tag/APK version mismatch")
    digests = [hashlib.sha256(certificate).hexdigest() for certificate in certificates]
    if digests != [expected_certificate]:
        raise ValueError("Release signing certificate mismatch")
    # Optional localized summaries override neutral text. A plain release description
    # must not block a verified update or be interpreted as app content.
    # The full author-written changelog remains on the explicitly opened release page.
    notes = {"ru": f"Опубликована версия {name}. Список изменений доступен на странице релиза.",
             "en": f"Version {name} has been published. See the release page for the changelog."}
    block = re.search(r"<!-- android-update-notes\s*\n([\s\S]*?)\n-->", release.get("body") or "")
    if block:
        if len(block[1].encode("utf-8")) > MAX_JSON:
            raise ValueError("Release summary size")
        notes = strict_json(block[1].encode("utf-8"))
    elif "android-update-notes" in (release.get("body") or ""):
        raise ValueError("Malformed android-update-notes block")
    result = {**BASE, "release": {
        "versionCode": code, "versionName": name, "minSdk": int(sdk.group(1)),
        "releaseUrl": release["html_url"],
        "notes": notes,
    }}
    return validate_manifest(result)


def monotonic(previous, candidate):
    validate_manifest(previous)
    validate_manifest(candidate)
    old = previous["release"]["versionCode"] if previous["release"] else 0
    new = candidate["release"]["versionCode"] if candidate["release"] else 0
    if old and new == old and candidate["release"]["versionName"] != previous["release"]["versionName"]:
        raise ValueError("Same versionCode assigned to different versions")
    return previous if old >= new else candidate


def generate():
    previous = baseline()
    try:
        release = api("/releases/latest")
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        release = None
    candidate = {**BASE, "release": None}
    # The checker first ships in 1.2.0. Do not bootstrap an older legacy release
    # whose historical build may not satisfy the new verification contract.
    legacy = False
    if release is not None:
        tag = release.get("tag_name", "")
        if isinstance(tag, str) and re.fullmatch("v" + VERSION, tag):
            parts = tuple(map(int, tag[1:].split(".")))
            legacy = parts + (0,) * (4 - len(parts)) < (1, 2, 0, 0)
    if legacy:
        print("Latest release predates the 1.2.0 checker; preserving the verified baseline (initially null)")
    elif release is not None:
        asset = release_asset(release)
        require_successful_build(release["tag_name"])
        raw = read_url(asset["browser_download_url"], MAX_APK, allow_asset_redirects=True)
        if len(raw) != asset["size"]:
            raise ValueError("APK size mismatch")
        if asset.get("digest") and asset["digest"] != "sha256:" + hashlib.sha256(raw).hexdigest():
            raise ValueError("APK digest mismatch")
        sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
        tools = sorted((sdk / "build-tools").glob("*/lib/apksigner.jar"))
        if not tools:
            raise ValueError("Android APK verification tools missing")
        jar = tools[-1]
        config = strict_json((ROOT / "config/android-updates.json").read_bytes())
        if config["applicationId"] != BASE["applicationId"]:
            raise ValueError("Publisher application mismatch")
        with tempfile.TemporaryDirectory(prefix="published-apk-") as directory:
            apk = Path(directory) / "release.apk"
            apk.write_bytes(raw)
            certificates = apk_certificates(jar, apk)
            badging = subprocess.check_output([str(jar.parent.parent / "aapt2"), "dump", "badging", str(apk)], text=True, timeout=60)
        candidate = release_from_evidence(release, badging, certificates, config["certificateSha256"])
        # Recheck publication after download/verification: no withdrawn or replaced asset.
        current = api("/releases/latest")
        if current.get("id") != release.get("id") or release_asset(current) != asset:
            raise ValueError("Release changed during verification; retry deployment")
    result = monotonic(previous, candidate)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(encode(result))
    print("Verified update manifest versionCode:", result["release"]["versionCode"] if result["release"] else "none")


def verify_live():
    expected = validate_manifest(strict_json(OUTPUT.read_bytes()))
    for attempt in range(6):
        try:
            actual = validate_manifest(strict_json(read_url(MANIFEST_URL, MAX_JSON)))
            if actual == expected:
                print("Live update manifest matches the deployed verified manifest")
                return
        except (OSError, ValueError):
            if attempt == 5:
                raise
        if attempt < 5:
            time.sleep(10)
    raise ValueError("Live manifest did not match after deployment")


def probe_site():
    """Read-only pre-merge availability check; never creates a manifest or deploys."""
    from verify_site_routes import PageLinks
    opener = urllib.request.build_opener(NoRedirect())
    origin = "https://bluetoothdisable.app"
    for route in ("/", "/privacy/ru/privacy", "/privacy/en/privacy"):
        with opener.open(urllib.request.Request(origin + route, headers={"User-Agent": "Bluetooth-Disable-Privacy/1.0"}), timeout=15) as response:
            if response.status != 200 or response.headers.get_content_type() != "text/html":
                raise ValueError("Public HTML page is unavailable: " + route)
            body = response.read(512 * 1024 + 1)
            if len(body) > 512 * 1024:
                raise ValueError("Public HTML page size")
        page = PageLinks(body.decode("utf-8", errors="strict"))
        if page.canonical != [origin + route]:
            raise ValueError("Public page canonical: " + route)
        for href in page.styles:
            address = urllib.parse.urljoin(origin + route, href)
            parsed = url(address, {"bluetoothdisable.app"})
            if not re.fullmatch(r"/assets/css/(variables|themes|main|components|responsive)\.css", parsed.path):
                raise ValueError("Unexpected public stylesheet")
            request = urllib.request.Request(address, headers={"User-Agent": "Bluetooth-Disable-Privacy/1.0", "Accept": "text/css"})
            with opener.open(request, timeout=15) as response:
                if response.status != 200 or response.headers.get_content_type() != "text/css":
                    raise ValueError("Public CSS is unavailable")
                if len(response.read(256 * 1024 + 1)) > 256 * 1024:
                    raise ValueError("Public CSS size")
        print("Live HTML, canonical and CSS verified:", route)
    try:
        value = validate_manifest(strict_json(read_url(MANIFEST_URL, MAX_JSON)))
        print("Live update JSON is available; versionCode:", value["release"]["versionCode"] if value["release"] else "none")
    except urllib.error.HTTPError as error:
        if error.code == 404:
            print("Live update endpoint is not deployed yet (HTTP 404); no JSON success claimed.")
        else:
            raise


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--verify-live", action="store_true")
    mode.add_argument("--probe-site", action="store_true")
    args = parser.parse_args()
    if args.verify_live:
        verify_live()
    elif args.probe_site:
        probe_site()
    else:
        generate()
