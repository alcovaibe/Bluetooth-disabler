package com.pulse.bluetoothdisable.ui.tour

import android.content.Context

enum class MainTourKind { NONE, UPDATES, FULL }

internal object MainTourPolicy {
    fun decide(seen: Boolean, firstInstallTime: Long, lastUpdateTime: Long): MainTourKind = when {
        seen -> MainTourKind.NONE
        lastUpdateTime > firstInstallTime -> MainTourKind.FULL
        else -> MainTourKind.UPDATES
    }
}

/** This one-time tour is independent of build numbers, including Internal builds. */
internal class MainTourStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    fun claim(): MainTourKind {
        val installation = context.packageManager.getPackageInfo(context.packageName, 0)
        return claim(installation.firstInstallTime, installation.lastUpdateTime)
    }

    internal fun claim(firstInstallTime: Long, lastUpdateTime: Long): MainTourKind = synchronized(LOCK) {
        // Existing users have seen the old drawer tutorial, not the new bottom navigation.
        // Offer the new navigation once, without restarting it on later app upgrades.
        val oldKind = MainTourPolicy.decide(preferences.getBoolean(KEY_SEEN, false), firstInstallTime, lastUpdateTime)
        val kind = if (preferences.getBoolean(KEY_BOTTOM_NAV_SEEN, false)) MainTourKind.NONE
            else if (oldKind == MainTourKind.NONE) MainTourKind.FULL else oldKind
        // Claim before showing: dismissal and process death must not restart this tour.
        if (kind != MainTourKind.NONE && !preferences.edit()
                .putBoolean(KEY_SEEN, true)
                .putBoolean(KEY_BOTTOM_NAV_SEEN, true).commit()) {
            return@synchronized MainTourKind.NONE
        }
        kind
    }

    companion object {
        const val PREFERENCES_NAME = "main_screen_tour"
        private const val KEY_SEEN = "update_icon_tour_seen"
        private const val KEY_BOTTOM_NAV_SEEN = "floating_bottom_navigation_seen_v1"
        private val LOCK = Any()
    }
}
