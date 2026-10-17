# Gallery Cover Mode

## Purpose

Gallery Cover Mode disguises Bluetooth Disable as a local private gallery. After activation, the launcher opens the Gallery interface while the main Bluetooth Disable UI is available through emergency recovery.

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

## Setup

1. Select Gallery Cover Mode and confirm the transition.
2. Import photos through the Photo Picker when needed.
3. Tap Enable Gallery mode. Activation does not require any photos.

Normal access through a secret image and tap sequence has been removed. Old access credentials and their key are cleared on upgrade without deleting photos or albums.

## Emergency recovery

On the main Gallery Cover Mode screen or in the photo viewer, hold the **Gallery** title for **3 seconds**. Android system authentication is requested. After successful authentication, the user must confirm the cover reset.

When the Quick Settings tile is installed, Bluetooth Disable can also be opened from the tile.

Cancelling authentication or declining the reset leaves the active cover unchanged.

## Storage and encryption

Imported images, thumbnails and related metadata are stored locally. Gallery storage uses AES-256-GCM with Android Keystore keys.

The application requests `INTERNET` for its separate privacy-policy viewer. Gallery Cover Mode does not upload gallery data.

Application data is excluded from Android backup and device-to-device transfer. `DeviceTransferGuard` additionally resets migrated state when app-private files are copied to another device without the corresponding non-exportable Keystore identity.

## Main components

- `GalleryCoverActivity` — cover container and recovery flow;
- `GalleryCoverSetupActivity` — photo import and cover activation;
- `GalleryViewModel` — UI state;
- `GalleryRepository` — import, persistence and image/album operations;
- `GalleryCipher` — gallery-data encryption;
- `GalleryAccessManager` — removal of legacy hidden-access credentials.

## Verification

Gallery Cover Mode has unit and instrumentation coverage for:

- activation without a secret image and removal of legacy access credentials;
- memory-bounded preview caching;
- encrypted import and persistence;
- state restoration across recreation;
- cover/main navigation;
- recovery behavior.

Instrumentation tests run in the shared CI compatibility matrix for Android API 26–36.
