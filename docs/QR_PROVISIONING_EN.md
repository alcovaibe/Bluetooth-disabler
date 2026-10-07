# Device Owner QR provisioning

## Purpose

Bluetooth Disable uses Android Device Policy and can operate as a Device Policy Controller (DPC).

For system-level Bluetooth blocking, the application must be assigned as **Device Owner**. One supported assignment method is QR provisioning through Android Setup Wizard during the device's initial setup.

Full step-by-step instructions for **QR** and **ADB** are published on the project website:

https://bluetoothdisable.app/

## Requirements

QR provisioning requires:

- Android 8.0 (API 26) or newer;
- Device Owner provisioning support from the device and firmware;
- Android initial setup;
- network access to download the APK;
- no incompatible Device Owner or other device state that blocks provisioning.

On most devices, QR provisioning is used after a factory reset. Exact requirements and the way QR mode is opened depend on the Android version and device manufacturer.

Back up important data before resetting the device.

## What the QR code contains

The QR code contains a standard Android provisioning payload with:

- the application's Device Admin component;
- the APK download URL;
- the APK checksum.

The fields used are:

```text
android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME
android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION
android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM
```

The provisioning payload does not contain user data, passwords, or application signing keys.

## APK integrity verification

SHA-256 is calculated for the published APK. The checksum is converted to URL-safe Base64 and included directly in the provisioning payload, which is then encoded into the QR code.

Android Setup Wizard uses this checksum to verify the downloaded APK before assigning Device Owner.

The checksum is bound to the exact APK bytes. If the APK is rebuilt or modified, a new checksum and a new QR code are required.

## Current QR code

The stable QR code for the latest published version is stored at:

```text
docs/bluetooth-disable-device-owner-qr.png
```

It is updated automatically when a new release is published and is used by the root README.

The QR image is also attached to the GitHub Release. The project website loads the QR from the latest release; if the PNG is unavailable, the website can generate the same provisioning payload locally from the APK URL and the published SHA-256 digest.

A separate provisioning JSON file is not required because all data needed by Android Setup Wizard is already encoded inside the QR code.

## Compatibility

Device Owner provisioning behavior depends on:

- the Android version;
- the device manufacturer;
- the Android Setup Wizard implementation;
- firmware or enterprise restrictions;
- whether initial device setup has already been completed.

On some devices, QR provisioning may be unavailable or may start differently from standard Android.

If QR provisioning is unavailable, the alternative ADB assignment method is documented on the project website.

## Additional information

The release workflow that builds the provisioning payload, calculates SHA-256, and generates the QR code:

[`.github/workflows/release.yml`](../.github/workflows/release.yml)

Full user instructions for Device Owner setup through QR and ADB:

https://bluetoothdisable.app/
