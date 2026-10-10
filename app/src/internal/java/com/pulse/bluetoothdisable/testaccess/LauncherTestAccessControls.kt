package com.pulse.bluetoothdisable.testaccess

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pulse.bluetoothdisable.R

@Composable
fun LauncherTestAccessControls(
    state: TestAccessUiState,
    onSaveToken: (String) -> Unit,
    onRefresh: () -> Unit,
    onClearToken: () -> Unit,
) {
    // Deliberately not rememberSaveable: the token must not enter saved UI state.
    var token by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.test_access_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.test_access_description), style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        val status = when (state.status) {
            TestAccessStatus.UNCONFIGURED -> R.string.test_access_unconfigured
            TestAccessStatus.CHECKING -> R.string.test_access_checking
            TestAccessStatus.ALLOWED -> R.string.test_access_allowed
            TestAccessStatus.DENIED -> R.string.test_access_denied
            TestAccessStatus.EXPIRED -> R.string.test_access_expired
            TestAccessStatus.UNAVAILABLE -> if (state.allowed) {
                R.string.test_access_unavailable_cached
            } else R.string.test_access_unavailable
            TestAccessStatus.INVALID_TOKEN -> R.string.test_access_invalid_token
            TestAccessStatus.STORAGE_ERROR -> R.string.test_access_storage_error
        }
        Text(stringResource(status), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = token,
            onValueChange = { token = it.take(128) },
            label = { Text(stringResource(R.string.test_access_token)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                val submitted = token
                token = ""
                onSaveToken(submitted)
            },
            enabled = !state.busy && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.test_access_save)) }
        if (state.hasToken) {
            OutlinedButton(
                onClick = onRefresh,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.test_access_refresh)) }
        }
        TextButton(
            onClick = { token = ""; onClearToken() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.test_access_clear)) }
    }
}
