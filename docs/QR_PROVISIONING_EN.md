# Device Owner QR provisioning

## Overview

Bluetooth Disable is distributed as a Device Policy Controller. Android Setup Wizard QR provisioning is used for the fully managed Device Owner flow.

All new releases use tags strictly in this format:

```text
v<version>
```

For example:

```text
v1.0.20
```

The tag must exactly match `versionName` in `app/build.gradle.kts`. The release workflow rejects mismatched tags.

## Automatic QR generation

`.github/workflows/release.yml` performs the following sequence:

1. build the signed release APK;
2. publish the GitHub Release;
3. calculate SHA-256 from that exact signed APK;
4. encode the digest as URL-safe Base64 without padding;
5. build the provisioning JSON;
6. generate the QR image;
7. attach both JSON and PNG files to the GitHub Release;
8. update the stable files on `main`:
   - `docs/bluetooth-disable-device-owner-qr.png`;
   - `docs/bluetooth-disable-device-owner-provisioning.json`.

This keeps the QR checksum bound to the exact APK bytes published in the release.

## Release asset naming

The signed APK is named:

```text
BluetoothDisable-v<version>.apk
```

For `v1.0.20`:

```text
BluetoothDisable-v1.0.20.apk
```

The provisioning download URL is derived from the current GitHub tag and asset name.

## Provisioning payload

The workflow generates JSON with this structure:

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.pulse.bluetoothdisable/.admin.AppDeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://github.com/alcovaibe/Bluetooth-disabler/releases/download/v<version>/BluetoothDisable-v<version>.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "<URL_SAFE_BASE64_SHA256>"
}
```

The checksum must never be reused for a rebuilt or modified APK. Any byte-level APK change produces a new digest and therefore requires a new QR payload.

## Release files

After a successful workflow, the GitHub Release contains:

- `BluetoothDisable-v<version>.apk`;
- `bluetooth-disable-device-owner-qr.png`;
- `bluetooth-disable-device-owner-provisioning.json`.

The stable `docs/bluetooth-disable-device-owner-qr.png` file is updated automatically and is intended for README installation instructions and provisioning of the latest published version.

The legacy `docs/bluetooth-disable-1.0.6-device-owner-qr.png` file remains only as a historical artifact of the old release flow and should not be used for new installations after the next `v*` release is published.

## Website QR dialog

Website card 01 in `docs/pages/` contains numbered instructions, with a
“Show QR code” button in the final step. Each opening refreshes the latest
published release using the GitHub API and displays that release's
`bluetooth-disable-device-owner-qr.png`. No website redeployment is needed
after a release.

If the PNG is missing or unavailable, the website generates the QR locally
from that same release's APK URL and GitHub `sha256` digest, converted to
URL-safe Base64 without padding exactly as in the release workflow. The
dialog identifies the APK version. No external QR-generation service is used.
If current release metadata or a valid checksum is unavailable, an error and
retry button are shown instead of a stale QR.

## Recommended release check

Before creating a production tag, verify that:

- `versionName` and `versionCode` are correct;
- PR CI is green;
- `main` is green in Android Full Compatibility;
- release secrets contain the current signing keystore and passwords.

After publication, perform one physical-device check of the newly generated QR after factory reset. Setup Wizard should download the APK, validate the checksum, and provision Bluetooth Disable as Device Owner.
