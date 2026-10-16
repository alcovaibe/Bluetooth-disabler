# Android stable update checks

Ordinary Android builds starting with 1.2.0 check a static same-origin JSON manifest,
not the GitHub API. Signing configuration is unchanged.
Old installed builds without the checker cannot discover this feature retroactively.

The manifest is intentionally public, including when opened directly in a browser.
It contains only public release metadata, never credentials or private application data.
User-Agent, Referer, CORS or a secret embedded in the APK cannot enforce app-only access;
no such access-control claim is made for this static endpoint.

## Client

Only `MainActivity` enters the checker. `onPause` cancels metadata requests and
clears their transient UI; generation checks prevent late metadata results.
User-started downloads and ready APK state survive pause for the permission and
installer round trips, and are shown only in the main interface.
No workers, services, alarms or notification permissions are introduced.
The public client can download and hand a verified APK to the system installer. Internal sets `PUBLIC_UPDATE_CHECKS=false` and makes no public update
request. Its independent version sequence is never compared with stable.

The ordinary client checks once per 24 hours when the main interface resumes. The
timestamp is recorded at the start, including failed/cancelled automatic attempts.
Clock rollback resets the timestamp without starting another request. Manual checks
ignore that interval but have a five-second cooldown and a process-wide single-flight
lock. Failed manual requests have a retry action after five seconds; automatic errors
are silent. A missing/null, older, equal or incompatible release is not advertised.
The manual result says no *newer compatible* version, rather than claiming every
device can install the latest release.

Deferral is persisted by versionCode when choosing Later or cancelling a download. A later version
can be shown; manual checks can show a deferred release again. All messages are native
Compose plain text using the selected RU/EN language and existing light/dark palette.
The bottom sheet reuses `CoverModeBottomSheet`, including the calculator setup's
spacing, typography, colours and action buttons. Download starts only after a tap.
The fixed asset URL is derived from the site's validated version name:
`https://github.com/alcovaibe/Bluetooth-disabler/releases/download/v<version>/BluetoothDisable-v<version>.apk`.
The existing schema and website publisher remain unchanged; no GitHub API token or
release-list API call is needed. RU/EN progress, cancellation, retry and install states
are available in both themes. Tests use fixtures and never publish updates.

The separate cookie-free download client permits at most five redirects, only to
HTTPS `release-assets.githubusercontent.com` or `objects.githubusercontent.com` on
port 443 without userinfo/fragments. Downloads are limited to 100 MiB, checked
against Content-Length when present, and written into app-private cache. A cancelled
or failed download removes its partial file. Downloading survives Activity pause
and rotation through the ViewModel, but is not a persistent background service:
process termination requires downloading again.

Before offering installation, and again on each Install tap, PackageManager checks
the APK package, exact manifest versionCode/versionName/minSdk, newer installed code,
non-debuggable status and matching current signing certificates. Android's system
installer performs final cryptographic verification and installation confirmation.
Signing-key rotation is intentionally rejected pending an explicit migration.
A non-exported FileProvider grants temporary read access only to the update cache.
No shared-storage permission is used. The public builds declare
REQUEST_INSTALL_PACKAGES; internal removes it and never enters this download flow.
If installation from this source is disabled, Install opens Android settings; after
granting permission, return and tap Install again. A denied permission or cancelled
installer keeps the APK available for another attempt. No silent installation is requested.

The independent OkHttp client uses platform TLS/hostname verification, no cookies,
authentication, analytics or app identifiers in headers, and a fixed generic User-Agent.
It rejects redirects and all non-fixed manifest URLs, limits the complete call to 15s,
and enforces a 16 KiB limit on decoded streaming content. Validated ETag + manifest are
cached only in the current ViewModel; 304 without that cache fails. A 200 response must
be UTF-8 application/json. Unsupported schemas, unknown fields, duplicate/escaped
duplicate keys, malformed UTF-8, deep nesting, invalid ranges/types/strings and unsafe
links fail closed. The JSON parser accepts only the types required by this schema.
After one verified response, a lower code in that session is rejected.

## Schema 1

`https://bluetoothdisable.app/updates/android.json` is a new assets-only Worker route.
The checked-in initial state is deliberately `release: null`, not a fictitious release:

```json
{
  "schemaVersion": 1,
  "platform": "android",
  "channel": "stable",
  "applicationId": "com.pulse.bluetoothdisable",
  "release": null
}
```

A populated `release` has exactly `versionCode` (1..2100000000), `versionName`
(2..4 numeric components), `minSdk` (1..10000), `releaseUrl` and `notes` (`ru`, `en`,
nonblank, at most 1500 UTF-16 code units each). `releaseUrl` must identify exactly
`https://github.com/alcovaibe/Bluetooth-disabler/releases/tag/v<versionName>`.
The Android parser also accepts explicit HTTPS port 443. URLs cannot contain queries,
fragments, userinfo, percent encoding, backslashes, control/space or non-ASCII characters.
`versionCode` is compared numerically, never versionName lexicographically.

## Publication

The existing Cloudflare deployment is the sole publisher. Every deployment checks out
the current main, not an old release tag, and shares a non-cancelling concurrency group.
It runs the existing release snapshot generator, installs public APK inspection tools,
then executes `python3 scripts/android_updates.py` before `wrangler deploy`.

The generator reads `/releases/latest`, requires a public stable release and exactly
one uploaded `BluetoothDisable-v<versionName>.apk`, verifies a successful Build and
Release run for the tag commit, downloads the actual asset with bounded validated
redirects, checks size/digest, verifies Android APK signatures through the existing
SDK ApkVerifier helper and compares the signer with `config/android-updates.json`.
The configured public fingerprint is that of the existing persistent release signing
certificate already verified by main CI; no signing key is read or changed here.
Package, numeric code, name, minimum SDK and non-debuggable status come from that APK.
The release/asset are rechecked after inspection to catch publication/replacement races.

The initial manifest stays null while the latest public release is older than 1.2.0.
At implementation time GitHub latest was v1.1.0, whose historical Build and Release run
did not finish successfully; it is not retroactively advertised or assigned metadata.
This also lets the site ship the new route before the first updater-aware release.

Before deployment the generator reads the live manifest with cache revalidation.
It preserves a higher/equal verified version, rejects conflicting names for the same
code and aborts on unavailable/malformed live state. Only a genuine HTTP 404 permits
first-install initialization. The endpoint has a five-minute revalidation cache and
nosniff. After deployment a bounded retry loop requires a real HTTP 200 JSON response
equal to the prepared manifest; a 404 is never treated as verification success.

Releases can be published before assets finish uploading. Such attempts fail before
deployment. A successful Build and Release `workflow_run` retries publication using
trusted main code without executing/downloading workflow artifacts. Published/edited
release events, the existing daily schedule and workflow_dispatch also retry. Later
manual asset uploads without any such event may wait until the daily retry; deploy
manually if needed. A tag, main push or internal build alone cannot announce a release.

To start a new release without a pre-existing tag, manually run **Build and Release**
on `main` with the desired `tag` and `create_tag=true`. It builds the exact dispatch
commit, checks the APK metadata and signature against the configured public certificate,
and creates a non-overwriting tag only after the signed APK build and checks succeed.
The usual draft release and provisioning QR are then produced;
review/add the notes before publishing the draft. Leave `create_tag=false` for an
already existing tag. No tag is moved, and the manual bootstrap is rejected outside
`main`.

For stable **1.2.0 and later**, put a short bilingual plain-text summary in the GitHub
release body (usually while it is still a draft):

```text
<!-- android-update-notes
{"ru":"Краткое описание фактических изменений.","en":"A short description of the actual changes."}
-->
```

Missing summaries get a neutral localized pointer to the release page. An explicitly
present but malformed summary still blocks publication. The generator does not render release Markdown or translate it.

## Verification and limits

Run publisher tests with `python3 -m unittest discover -s scripts -p android_updates_tests.py`.
The separate Update manifest checks workflow runs them on PR/main. Existing Platform CI
also exercises Android unit tests, RU/EN light/dark Compose tests, debug/internal/release
builds and lint. PR CI also runs `scripts/verify_update_release_apk.py` against the actual
release APK signed with its ephemeral CI key, without contacting/publishing any release.
`python3 scripts/verify_site_routes.py` checks the actual local Cloudflare
asset router, JSON MIME/cache/no-redirect behavior, home/privacy pages and their CSS.

No release, production deployment, valid notification or live-site success is fabricated
in tests. A PR preview can prove route behavior but cannot prove a production deployment.
Initial production probes returned HTTP 403. A repeat on 2026-10-15 using the viewer
User-Agent for both HTML and CSS verified the live home page, both policy pages,
their canonical URLs and required CSS. The new JSON endpoint returned HTTP 404:
it is not deployed before this PR is merged. Its production HTTP 200/schema check
remains the responsibility of the post-deploy workflow; no such success is claimed here.
The separate pre-merge read-only site probe records production HTML/CSS availability;
404 for the undeployed new endpoint is explicitly reported, not called JSON success.
That external availability probe is non-blocking and its step log must be reviewed.

This mechanism trusts HTTPS and the controlled site deployment. It is not a separate
cryptographically signed update protocol and cannot prevent
an administrator from manually deploying unrelated historical site code outside this
workflow. Revoking a previously announced release or changing the release signing key
needs an explicit reviewed maintenance change; neither is an automatic downgrade path.
