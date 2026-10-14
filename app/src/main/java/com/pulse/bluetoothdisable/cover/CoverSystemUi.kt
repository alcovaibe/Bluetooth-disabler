package com.pulse.bluetoothdisable.cover

import android.app.Activity
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pulse.bluetoothdisable.theme.ThemeManager
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme

@Suppress("DEPRECATION")
fun ComponentActivity.enableCoverEdgeToEdge() {
    enableEdgeToEdge()
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.navigationBarDividerColor = Color.TRANSPARENT
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
}

@Composable
fun CoverTheme(
    activity: Activity,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (ThemeManager.getSelectedTheme(activity)) {
        ThemeManager.LIGHT -> false
        ThemeManager.DARK -> true
        else -> systemDark
    }
    val view = LocalView.current

    SideEffect {
        configureCoverSystemBars(activity, view, darkTheme)
    }

    BluetoothDisableTheme(
        darkTheme = darkTheme,
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Suppress("DEPRECATION")
private fun configureCoverSystemBars(activity: Activity, view: android.view.View, darkTheme: Boolean) {
    activity.window.statusBarColor = Color.TRANSPARENT
    activity.window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        activity.window.navigationBarDividerColor = Color.TRANSPARENT
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        activity.window.isNavigationBarContrastEnforced = false
    }
    WindowCompat.getInsetsController(activity.window, view).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
    }
}
