# Public APK download counter

One total sums `download_count` for uploaded `.apk` assets from every public release
in `alcovaibe/Bluetooth-disabler`, including prereleases. Asset IDs are deduplicated
across pagination. QR PNGs, provisioning JSON, source archives, drafts and private
Internal releases are excluded.

Both the website's Device Owner QR and the repository QR download the APK from
GitHub Releases. Those APK requests are already included; scanning or viewing a QR
image is not an APK download and is not added separately. GitHub's aggregate cannot
separate download sources or identify unique users/installations. Repeated downloads,
including automated APK verification, may be included by GitHub.

`Public APK download counter` runs hourly at minute 23, on relevant main changes,
on release changes, and by manual dispatch. GitHub can delay scheduled jobs.
The read-only job token fetches metadata only; no APK is downloaded to count it.
Failures abort before deployment, preserving the previous published snapshot.

The separate assets-only Worker `bluetoothdisable-downloads` serves `downloads.svg`
(Russian), `downloads-en.svg` (English) and `downloads.json` (total and UTC snapshot
time). Both badges display the same single total. Files are generated during CI,
never committed. HTTP caches may add a delay beyond the hourly refresh.
The main public site and the private API deployment are unchanged. The counter has
no runtime code, secrets, storage bindings or application/user telemetry.

Run checks with `node --test cloudflare/downloads-counter/build.test.mjs`.
Run `node cloudflare/downloads-counter/build.mjs` to generate assets locally.
The deployment verifies live JSON and both SVGs against the generated files.
