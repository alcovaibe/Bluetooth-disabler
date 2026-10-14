# Gallery Cover Mode

## Purpose

Gallery Cover Mode disguises Bluetooth Disable as a local private gallery. After activation, the launcher opens the Gallery interface while the main Bluetooth Disable UI is available through the configured secret image and tap sequence or through emergency recovery.

## Features

The current implementation supports:

- image import through the Android system Photo Picker;
- copying selected images into application-private storage;
- date-based photo grouping;
- full-screen viewing;
- swipe navigation;
- zooming;
- favorites;
- an automatic Favorites album;
- user-created albums;
- assigning photos to albums;
- deletion;
- image rotation and cropping;
- photo information display;
- light and dark themes.

After import, the application's local copy no longer depends on the original media item.

## Hidden-access setup

1. Select Gallery Cover Mode and confirm the transition.
2. Import photos through the Photo Picker when needed.
3. Select one image as the secret image.
4. Configure a sequence of **three distinct tap zones**.
5. Repeat the sequence for confirmation.
6. When both sequences match, activate the cover.

Five tap zones are available:

- top left;
- top right;
- center;
- bottom left;
- bottom right.

A zone cannot be repeated within the same three-tap sequence. This produces 60 valid sequences. During normal access attempts, no more than 5 seconds may elapse between taps.

## Opening Bluetooth Disable

Open the configured secret image and perform the saved three-zone sequence. Verification is bound to both the image identifier and the sequence.

The plaintext sequence is not persisted. `GalleryAccessManager` stores an HMAC-SHA256 verifier created with a non-exportable Android Keystore key.

## Emergency recovery

On the main Gallery Cover Mode screen, hold the **Gallery** title for **3 seconds**. Android system authentication is requested. After successful authentication, the user must confirm the cover reset.

When the Quick Settings tile is installed, Bluetooth Disable can also be opened from the tile.

Cancelling authentication or declining the reset leaves the active cover unchanged.

## Storage and encryption

Imported images, thumbnails and related metadata are stored locally. Gallery storage uses AES-256-GCM with Android Keystore keys. The hidden-access rule uses a separate HMAC-SHA256 key.

The application requests `INTERNET` for its separate privacy-policy viewer. Gallery Cover Mode does not upload gallery data.

Application data is excluded from Android backup and device-to-device transfer. `DeviceTransferGuard` additionally resets migrated state when app-private files are copied to another device without the corresponding non-exportable Keystore identity.

## Main components

- `GalleryCoverActivity` — cover container and recovery flow;
- `GalleryCoverSetupActivity` — secret image and sequence setup;
- `GalleryViewModel` — UI state;
- `GalleryRepository` — import, persistence and image/album operations;
- `GalleryCipher` — gallery-data encryption;
- `GalleryAccessManager` — hidden-access HMAC verification;
- `GalleryAccessPolicy` and `GallerySequenceDetector` — sequence rules and timeout behavior.

## Verification

Gallery Cover Mode has unit and instrumentation coverage for:

- all 60 valid three-zone sequences;
- repeated-zone rejection;
- sequence timeout reset;
- encrypted import and persistence;
- state restoration across recreation;
- cover/main navigation;
- recovery behavior.

Instrumentation tests run in the shared CI compatibility matrix for Android API 26–36.
