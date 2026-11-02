package com.pulse.bluetoothdisable.ui.tour

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

/** Configuration changes retain the session; a new process does not resume a claimed tour. */
internal class MainTourViewModel(application: Application) : AndroidViewModel(application) {
    var kind by mutableStateOf(MainTourStore(application).claim())
        private set

    fun dismiss() { kind = MainTourKind.NONE }
}
