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
        val kind = MainTourPolicy.decide(preferences.getBoolean(KEY_SEEN, false), firstInstallTime, lastUpdateTime)
        // Claim before showing: dismissal, process death and future updates never restart this tour.
        if (kind != MainTourKind.NONE && !preferences.edit().putBoolean(KEY_SEEN, true).commit()) {
            return@synchronized MainTourKind.NONE
        }
        kind
    }

    companion object {
        const val PREFERENCES_NAME = "main_screen_tour"
        private const val KEY_SEEN = "update_icon_tour_seen"
        private val LOCK = Any()
    }
}
