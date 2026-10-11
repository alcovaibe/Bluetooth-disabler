# Bluetooth Disable

**Language:** [Русский](README.md) | [English](README_EN.md)

Bluetooth Disable is an Android application for system-level Bluetooth blocking through Android Device Policy.

When assigned as **Device Owner**, the application applies the system restriction `UserManager.DISALLOW_BLUETOOTH`. While the restriction is active, Android prevents normal Bluetooth enabling and use. The application also makes a best-effort attempt to immediately turn off an already active adapter.

## Download

The current version is published in [GitHub Releases](https://github.com/alcovaibe/Bluetooth-disabler/releases/latest).

For Device Owner installation, an automatically updated QR code is also available — see [“Device Owner QR installation”](#device-owner-qr-installation).

## Features

- system-level Bluetooth blocking through Device Owner;
- persistence of the restriction after the application is closed or the device is rebooted;
- Quick Settings tile for control and access to the application;
- four complete Cover Modes: Calculator, Notes, Calendar, and Gallery;
- a separate hidden access method for each Cover Mode;
- emergency recovery through Android system authentication;
- launcher icon and application name switching;
- English and Russian interfaces;
- light and dark themes;
- operation without root, Shizuku, Magisk, or Accessibility Service.

## How Bluetooth protection works

Main mechanism:

```text
Device Owner
    ↓
DevicePolicyManager
    ↓
UserManager.DISALLOW_BLUETOOTH
    ↓
Android prevents Bluetooth use
```

After applying the restriction, the application checks the effective policy state again. A direct Bluetooth shutdown command is used only as a best-effort way to speed up the visible shutdown of an already active adapter.

## Cover Mode

Cover Modes change the launcher identity of the application and its start screen. Each mode is a separate functional local interface rather than just an icon change.

| Mode | Hidden access | Emergency recovery | Documentation |
| --- | --- | --- | --- |
| **Calculator** | Five-digit code and pressing `=` | Hold the “History” header for 3 seconds | [Calculator Cover Mode](docs/covermode/calculator/en/README.md) |
| **Notes** | Tap the configured secret fragment in the selected note | Hold the `+` button for 3 seconds | [Notes Cover Mode](docs/covermode/notes/en/NOTES_COVER_MODE.md) |
| **Calendar** | Open a note with the configured text on the secret date | Hold the calendar title or the control that returns to today for 3 seconds | [Calendar Cover Mode](docs/covermode/calendar/en/CALENDAR_COVER_MODE.md) |
| **Gallery** | Secret photo and a sequence of three screen zones | Hold the “Gallery” header for 3 seconds | [Gallery Cover Mode](docs/covermode/gallery/en/GALLERY_COVER_MODE.md) |

Access secrets are not stored in plaintext. Android Keystore-based mechanisms are used for verification.

### Emergency recovery

The recovery flow uses Android system authentication: biometrics or available device credentials — PIN, pattern, or password.

After successful authentication, the user separately confirms the reset of the active Cover Mode. Cancelling authentication or confirmation does not change the current mode.

If the Quick Settings tile has already been added, Bluetooth Disable can also be opened through the tile.

## Privacy and data storage

Bluetooth Disable follows a minimal-permission approach.

The application:

- contains no advertising;
- contains no analytics or trackers;
- does not request the `INTERNET` permission;
- does not access location;
- does not scan for or enumerate remote or paired Bluetooth devices;
- does not connect to remote Bluetooth devices;
- does not send notes, photos, or other user data to a server.

On Android 12+, the `BLUETOOTH_CONNECT` permission is used only for a best-effort attempt to immediately turn off the local Bluetooth adapter. Device Owner can grant this permission to the application through Device Policy.

User data is excluded from Android backup and device-to-device transfer. Additional protection of local state uses Android Keystore keys: if app-private data is transferred to another device without the corresponding non-exportable keys, the application resets the transferred state to a clean-install state.

## Requirements

- Android 8.0 (API 26) or newer;
- for system-level Bluetooth blocking, the application must be assigned as Device Owner.

Device Owner assignment is usually performed during Android initial setup and may require a device reset. Back up any required data before resetting the device.

## Device Owner QR installation

Stable QR code for the latest published build:

![Device Owner QR](docs/bluetooth-disable-device-owner-qr.png)

Basic flow:

1. back up any required data and prepare the device for initial setup;
2. open QR provisioning in Android Setup Wizard and connect to Wi-Fi;
3. scan the QR code above and wait for the APK to be downloaded and verified;
4. complete provisioning and make sure Bluetooth Disable is assigned as Device Owner.

Setup Wizard behavior and the way QR provisioning is opened may vary depending on the Android version and device manufacturer.

[Detailed Device Owner QR provisioning documentation](docs/QR_PROVISIONING_EN.md)

## Build and testing

The project uses Kotlin, Jetpack Compose, Material 3, Android Device Policy, and Android Keystore.

CI runs unit tests, Android Lint, application builds, and instrumentation tests. Cover Mode compatibility is tested on Android API 26–36.

Production releases use a separate signing keystore through GitHub Actions secrets or a local `keystore.properties` file.

## Feedback

Bugs and reproducible issues can be reported through [GitHub Issues](https://github.com/alcovaibe/Bluetooth-disabler/issues).

Reports should preferably include the device model, Android version, provisioning method, and exact reproduction steps.

## License

Bluetooth Disable is distributed under the **GNU General Public License v3.0 or later (GPL-3.0-or-later)**.

The full license text is available in [LICENSE](LICENSE), with additional information in [LICENSE-NOTICE](LICENSE-NOTICE).
