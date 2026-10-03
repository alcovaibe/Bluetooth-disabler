package com.pulse.bluetoothdisable.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class LauncherStyle(
    val preferenceValue: String,
    internal val aliasSuffix: String,
    internal val enabledByDefault: Boolean = false,
) {
    DEFAULT("default", ".LauncherAlias", enabledByDefault = true),
    CALCULATOR("calculator", ".LauncherAliasCalculator"),
    NOTES("notes", ".LauncherAliasNotes"),
    CALENDAR("calendar", ".LauncherAliasCalendar"),
    GALLERY("gallery", ".LauncherAliasGallery"),
}

class LauncherIconController(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun isHidden(): Boolean = LauncherStyle.values().none(::isEnabled)

    fun isExclusivelyEnabled(style: LauncherStyle): Boolean =
        LauncherStyle.entries.all { isEnabled(it) == (it == style) }

    fun selectedStyle(): LauncherStyle {
        val stored = storedStyle()
        if (isEnabled(stored)) return stored

        return LauncherStyle.values().firstOrNull(::isEnabled) ?: stored
    }

    fun setStyle(style: LauncherStyle) {
        check(preferences.edit()
            .putString(KEY_SELECTED_STYLE, style.preferenceValue)
            .commit()) { "Unable to persist launcher style" }
        applyStyle(style)
    }

    fun hide() {
        applyStyle(null)
    }

    fun show() {
        applyStyle(storedStyle())
    }

    private fun applyStyle(style: LauncherStyle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val settings = LauncherStyle.entries.map { candidate ->
                PackageManager.ComponentEnabledSetting(
                    component(candidate),
                    if (candidate == style) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    },
                    PackageManager.DONT_KILL_APP,
                )
            }
            packageManager.setComponentEnabledSettings(settings)
            return
        }

        // Before API 33 PackageManager has no atomic batch API. Read every alias state before
        // the first mutation: each component change emits PACKAGE_CHANGED, and querying
        // PackageManager again between those broadcasts can stall old Android versions long
        // enough to leave the cover transition pending. Enable the replacement first so there
        // is still no intentional gap without a launcher entry, then disable only aliases that
        // were enabled in the initial snapshot.
        val enabledBeforeChange = LauncherStyle.entries.associateWith(::isEnabled)
        if (style != null && enabledBeforeChange[style] != true) {
            writeEnabledState(style, true)
        }
        LauncherStyle.entries
            .filter { candidate -> candidate != style && enabledBeforeChange[candidate] == true }
            .forEach { candidate -> writeEnabledState(candidate, false) }
    }

    private fun storedStyle(): LauncherStyle {
        val value = preferences.getString(KEY_SELECTED_STYLE, null)
        return LauncherStyle.values().firstOrNull { it.preferenceValue == value }
            ?: LauncherStyle.DEFAULT
    }

    private fun isEnabled(style: LauncherStyle): Boolean =
        when (packageManager.getComponentEnabledSetting(component(style))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> style.enabledByDefault
            else -> false
        }

    private fun writeEnabledState(style: LauncherStyle, enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            component(style),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun component(style: LauncherStyle): ComponentName =
        ComponentName(
            appContext,
            "${appContext.packageName}${style.aliasSuffix}",
        )

    companion object {
        const val PREFERENCES_NAME = "launcher_icon_preferences"
        private const val KEY_SELECTED_STYLE = "selected_launcher_style"
    }
}
