package com.pulse.bluetoothdisable.cover.calendar

import android.app.Activity
import androidx.compose.runtime.Composable
import com.pulse.bluetoothdisable.cover.CoverTheme

@Composable
internal fun CalendarCoverTheme(activity: Activity, content: @Composable () -> Unit) {
    CoverTheme(
        activity = activity,
        content = content,
    )
}
