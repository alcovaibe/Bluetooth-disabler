package com.pulse.bluetoothdisable.transfer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.pulse.bluetoothdisable.cover.calendar.CalendarAccessManager
import com.pulse.bluetoothdisable.cover.calendar.LocalCalendarNotesRepository
import com.pulse.bluetoothdisable.cover.CoverModeStore
import com.pulse.bluetoothdisable.cover.calculator.CalculatorAccessCodeManager
import com.pulse.bluetoothdisable.cover.calculator.CalculatorHistoryStore
import com.pulse.bluetoothdisable.launcher.LauncherIconController
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Last-resort protection against OEM migration tools that copy app-private data
 * even when Android backup / D2D rules exclude it.
 *
 * A random marker is authenticated with a non-exportable HMAC key generated in the
 * Android Keystore. App files and preferences may be copied to another device, but
 * the Keystore key is device-local and is not part of Android app-data backup.
 * Therefore a copied marker cannot be validated on the destination device.
 */
object DeviceTransferGuard {
    private const val IDENTITY_PREFERENCES = "device_identity_preferences"
    private const val KEY_MARKER = "device_marker"
    private const val KEY_MARKER_MAC = "device_marker_mac"
    private const val LEGACY_KEY_DEVICE_ID_HASH = "device_id_hash"

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "bluetooth_disabler_transfer_guard_v1"
    private const val MARKER_SIZE_BYTES = 32

    private val knownPreferences = setOf(
        IDENTITY_PREFERENCES,
        "main_screen_tour",
        "theme_preferences",
        "language_preferences",
        "quick_settings_tile_state",
        LauncherIconController.PREFERENCES_NAME,
        CoverModeStore.PREFERENCES_NAME,
        CalculatorAccessCodeManager.PREFERENCES_NAME,
        CalendarAccessManager.PREFERENCES_NAME,
        CalculatorHistoryStore.PREFERENCES_NAME,
    )

    fun enforce(context: Context) {
        val appContext = context.applicationContext

        try {
            enforceWithKeystore(appContext)
        } catch (_: GeneralSecurityException) {
            // A transient or device-specific Keystore failure must not destroy user data.
            // Android backup/D2D exclusion rules remain the primary migration barrier.
        } catch (_: IOException) {
            // KeyStore.load() can fail because of a platform/storage problem. Fail safely
            // without resetting local state; the guard can retry on the next process start.
        }
    }

    private fun enforceWithKeystore(context: Context) {
        val preferences = context.getSharedPreferences(
            IDENTITY_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val markerEncoded = preferences.getString(KEY_MARKER, null)
        val markerMacEncoded = preferences.getString(KEY_MARKER_MAC, null)

        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }

        // Upgrade path from the previous ANDROID_ID-hash implementation or from versions
        // without a guard: establish a Keystore marker without touching existing app data.
        if (markerEncoded == null && markerMacEncoded == null) {
            initializeGuard(context, keyStore)
            return
        }

        // A half-written marker is treated as corrupted migrated state.
        if (markerEncoded == null || markerMacEncoded == null) {
            resetAndInitialize(context, keyStore)
            return
        }

        val key = existingKey(keyStore)
        if (key == null) {
            // The marker was copied/restored, but its non-exportable Keystore key was not.
            resetAndInitialize(context, keyStore)
            return
        }

        val marker = decode(markerEncoded)
        val storedMac = decode(markerMacEncoded)
        if (marker == null || storedMac == null) {
            resetAndInitialize(context, keyStore)
            return
        }

        val expectedMac = signMarker(key, marker)
        if (!MessageDigest.isEqual(storedMac, expectedMac)) {
            resetAndInitialize(context, keyStore)
            return
        }

        // Remove the identifier-derived value left by the previous implementation.
        if (preferences.contains(LEGACY_KEY_DEVICE_ID_HASH)) {
            preferences.edit {
                remove(LEGACY_KEY_DEVICE_ID_HASH)
            }
        }
    }

    private fun initializeGuard(context: Context, keyStore: KeyStore) {
        val key = existingKey(keyStore) ?: generateKey()
        val marker = ByteArray(MARKER_SIZE_BYTES).also(SecureRandom()::nextBytes)
        val markerMac = signMarker(key, marker)

        context.getSharedPreferences(IDENTITY_PREFERENCES, Context.MODE_PRIVATE)
            .edit(commit = true) {
                remove(LEGACY_KEY_DEVICE_ID_HASH)
                    .putString(KEY_MARKER, encode(marker))
                    .putString(KEY_MARKER_MAC, encode(markerMac))
            }
    }

    private fun resetAndInitialize(context: Context, keyStore: KeyStore) {
        clearMigratedLocalState(context)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }

        initializeGuard(context, keyStore)
    }

    private fun existingKey(keyStore: KeyStore): SecretKey? =
        keyStore.getKey(KEY_ALIAS, null) as? SecretKey

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            KEYSTORE_PROVIDER,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun signMarker(key: SecretKey, marker: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(key)
            doFinal(marker)
        }

    private fun encode(value: ByteArray): String =
        Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray? =
        try {
            Base64.decode(value, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun clearMigratedLocalState(context: Context) {
        // Delete the Calculator Cover verifier and its device-local Keystore key explicitly.
        CalculatorAccessCodeManager(context).clearCode()
        CalendarAccessManager(context).clear()
        LocalCalendarNotesRepository(context).clear()
        clearAllSharedPreferences(context)

        context.databaseList().forEach { databaseName ->
            context.deleteDatabase(databaseName)
        }

        clearDirectoryContents(context.filesDir)
        clearDirectoryContents(context.noBackupFilesDir)
        clearDirectoryContents(context.cacheDir)
        clearDirectoryContents(context.codeCacheDir)

        context.getExternalFilesDirs(null)
            .filterNotNull()
            .forEach(::clearDirectoryContents)
        context.externalCacheDirs
            .filterNotNull()
            .forEach(::clearDirectoryContents)

        clearDirectDataFiles(context)

        // Component enabled state is managed by PackageManager rather than app files.
        // Restore the launcher to the clean-install state as well.
        LauncherIconController(context).show()
    }

    private fun clearAllSharedPreferences(context: Context) {
        val preferencesDirectory = File(context.applicationInfo.dataDir, "shared_prefs")
        val preferenceNames = preferencesDirectory.listFiles()
            ?.asSequence()
            ?.filter { file -> file.isFile && file.name.endsWith(".xml") }
            ?.map { file -> file.name.removeSuffix(".xml") }
            ?.toMutableSet()
            ?: mutableSetOf()

        preferenceNames += knownPreferences

        preferenceNames.forEach { preferenceName ->
            context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
                .edit(commit = true) {
                    clear()
                }
        }
    }

    private fun clearDirectoryContents(directory: File?) {
        directory?.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }
    }

    private fun clearDirectDataFiles(context: Context) {
        File(context.applicationInfo.dataDir)
            .listFiles()
            ?.filter { child -> child.isFile }
            ?.forEach { child -> child.delete() }
    }
}
