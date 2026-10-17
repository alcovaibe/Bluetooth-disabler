package com.pulse.bluetoothdisable.cover.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.cover.CoverIcons
import com.pulse.bluetoothdisable.cover.coverRecoveryHold

private const val HISTORY_SINGLE_LINE_CHARACTER_LIMIT = 24

@Composable
fun CalculatorHistoryDrawer(
    history: List<CalculatorHistoryEntry>,
    useComma: Boolean,
    onClear: () -> Unit,
    recoveryEnabled: Boolean = false,
    onRecoveryHold: () -> Unit = {},
) {
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = Modifier
            .width(290.dp)
            .fillMaxHeight(),
        drawerShape = RoundedCornerShape(0.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .statusBarsPadding()
                .testTag("calculator_history_header")
                .coverRecoveryHold(recoveryEnabled, onRecoveryHold)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.calculator_history),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.calculator_history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                reverseLayout = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    items = history,
                    key = { index, entry -> "${entry.expression}|${entry.result}|$index" },
                ) { _, entry ->
                    CalculatorHistoryRow(entry = entry, useComma = useComma)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(
            onClick = { showClearConfirmation = true },
            enabled = history.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("calculator_history_clear"),
        ) {
            Icon(CoverIcons.Trash, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.calculator_clear_history),
                fontWeight = FontWeight.Bold,
            )
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.calculator_clear_history_title)) },
            text = { Text(stringResource(R.string.calculator_clear_history_confirmation)) },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClear()
                    },
                    modifier = Modifier.testTag("calculator_history_clear_confirm"),
                ) {
                    Text(stringResource(R.string.calculator_clear_history))
                }
            },
        )
    }
}

@Composable
private fun CalculatorHistoryRow(
    entry: CalculatorHistoryEntry,
    useComma: Boolean,
) {
    val expression = CalculatorFormatter.localize(entry.expression, useComma)
    val result = CalculatorFormatter.localize(entry.result, useComma)
    val historyLine = "$expression = $result"

    // 290 dp drawer - 32 dp list padding - 16 dp row padding = 242 dp of text width.
    // At bodyLarge's 16 sp monospace size this fits about 25 glyphs, so 24 leaves
    // a small safety margin for font scaling and rendering differences.
    val maxLines = if (historyLine.length <= HISTORY_SINGLE_LINE_CHARACTER_LIMIT) 1 else 2

    Text(
        text = historyLine,
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}
