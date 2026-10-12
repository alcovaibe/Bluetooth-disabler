package com.pulse.bluetoothdisable.testaccess

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No token storage, UI, HTTP client or endpoint is compiled into debug/release. */
class LauncherTestAccessViewModel(application: Application) : AndroidViewModel(application) {
    val state: StateFlow<TestAccessUiState> = MutableStateFlow(TestAccessUiState()).asStateFlow()
    fun refresh() = Unit
    fun allowsLauncherChange(): Boolean = false
    @Suppress("UNUSED_PARAMETER")
    fun saveToken(token: String) = Unit
    fun clearToken() = Unit
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun LauncherTestAccessControls(
    state: TestAccessUiState,
    onSaveToken: (String) -> Unit,
    onRefresh: () -> Unit,
    onClearToken: () -> Unit,
) = Unit
