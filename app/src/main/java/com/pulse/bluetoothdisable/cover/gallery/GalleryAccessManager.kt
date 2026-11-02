package com.pulse.bluetoothdisable.cover.gallery

import android.content.Context
import java.security.KeyStore

/** Removes legacy photo-tap credentials on upgrade; Gallery no longer accepts a tap sequence. */
class GalleryAccessManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    // Keep verifier cleanup separate until the cover transition commits, for rollback compatibility.
    internal fun clearVerifier() {
        check(preferences.edit().clear().commit()) { "Unable to clear legacy gallery access rule" }
    }

    fun clear() {
        clearVerifier()
        deleteKey()
    }

    internal fun deleteKey() {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
            // No stored verifier or runtime verification path remains.
        }
    }

    companion object {
        const val PREFERENCES_NAME = "gallery_access_preferences"
        private const val KEY_ALIAS = "bluetooth_disabler_gallery_access_v1"
    }
}
