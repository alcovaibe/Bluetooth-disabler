package com.pulse.bluetoothdisable.cover.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onUnlock: () -> Unit,
    recoveryEnabled: Boolean = false,
    onRecoveryHold: () -> Unit = {},
) {
    val state = viewModel.uiState
    val useComma = true
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CalculatorHistoryDrawer(
                history = state.history,
                useComma = useComma,
                onClear = viewModel::clearHistory,
                // Keep the listener armed while the Activity is eligible for recovery. Gating it
                // on drawer animation state made holds started during opening silently fail.
                recoveryEnabled = recoveryEnabled,
                onRecoveryHold = onRecoveryHold,
            )
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
                            contentDescription = stringResource(R.string.calculator_history),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                val previous = CalculatorFormatter.localize(state.previousExpression, useComma)
                val display = CalculatorFormatter.localize(state.display, useComma)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = previous,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = display,
                        fontSize = when {
                            display.length > 34 -> 28.sp
                            display.length > 20 -> 36.sp
                            else -> 52.sp
                        },
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }

                state.error?.let { error ->
                    Text(
                        text = stringResource(
                            when (error) {
                                CalculatorEngineError.DIVISION_BY_ZERO ->
                                    R.string.calculator_error_divide_by_zero
                                CalculatorEngineError.INVALID_EXPRESSION ->
                                    R.string.calculator_error_invalid_expression
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        textAlign = TextAlign.End,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                CalculatorKeypad(
                    onKey = { key ->
                        when (key) {
                            "C" -> viewModel.clear()
                            "⌫" -> viewModel.backspace()
                            "%" -> viewModel.inputPercent()
                            "÷" -> viewModel.inputOperator('÷')
                            "×" -> viewModel.inputOperator('×')
                            "−" -> viewModel.inputOperator('-')
                            "+" -> viewModel.inputOperator('+')
                            "()" -> viewModel.inputParenthesis()
                            ",", "." -> viewModel.inputDecimal()
                            "=" -> if (viewModel.equalsPressed()) onUnlock()
                            else -> viewModel.inputDigit(key[0])
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CalculatorKeypad(
    onKey: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val keyGap = 8.dp
        val railGap = 12.dp
        val calculatedKeySize = (maxWidth - (keyGap * 2f) - railGap) / 4f
        val keySize = calculatedKeySize.coerceIn(56.dp, 92.dp)
        val keypadWidth = (keySize * 4f) + (keyGap * 2f) + railGap

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.width(keypadWidth),
                horizontalArrangement = Arrangement.spacedBy(railGap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(keyGap)) {
                    CalculatorThreeKeyRow(listOf("C", "⌫", "%"), keySize, keyGap, onKey)
                    CalculatorThreeKeyRow(listOf("7", "8", "9"), keySize, keyGap, onKey)
                    CalculatorThreeKeyRow(listOf("4", "5", "6"), keySize, keyGap, onKey)
                    CalculatorThreeKeyRow(listOf("1", "2", "3"), keySize, keyGap, onKey)
                    CalculatorThreeKeyRow(
                        listOf("()", "0", ","),
                        keySize,
                        keyGap,
                        onKey,
                    )
                }

                CalculatorOperatorRail(
                    keySize = keySize,
                    keyGap = keyGap,
                    onKey = onKey,
                )
            }
        }
    }
}

@Composable
private fun CalculatorThreeKeyRow(
    keys: List<String>,
    keySize: Dp,
    keyGap: Dp,
    onKey: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(keyGap)) {
        keys.forEach { key ->
            CalculatorCircleKey(
                key = key,
                keySize = keySize,
                onClick = { onKey(key) },
                isClear = key == "C",
            )
        }
    }
}

@Composable
private fun CalculatorCircleKey(
    key: String,
    keySize: Dp,
    onClick: () -> Unit,
    isClear: Boolean = false,
) {
    val colors = if (isClear) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Button(
        onClick = onClick,
        modifier = Modifier.size(keySize),
        shape = CircleShape,
        colors = colors,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = key,
            fontSize = if (keySize >= 80.dp) 26.sp else 22.sp,
        )
    }
}

@Composable
private fun CalculatorOperatorRail(
    keySize: Dp,
    keyGap: Dp,
    onKey: (String) -> Unit,
) {
    val railHeight = (keySize * 5f) + (keyGap * 4f)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = Modifier
            .width(keySize)
            .height(railHeight)
            .background(
                color = if (dark) AppColors.CalculatorRailDark else AppColors.CalculatorRailLight,
                shape = RoundedCornerShape(keySize / 2f),
            ),
        verticalArrangement = Arrangement.spacedBy(keyGap),
    ) {
        listOf("÷", "×", "−", "+", "=").forEach { key ->
            val isEquals = key == "="
            Button(
                onClick = { onKey(key) },
                modifier = Modifier.size(keySize),
                shape = CircleShape,
                colors = if (isEquals) {
                    ButtonDefaults.buttonColors(
                        containerColor = AppColors.CalculatorEquals,
                        contentColor = Color.White,
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = if (dark) AppColors.CalculatorOnRailDark else AppColors.CalculatorOnRailLight,
                    )
                },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = key,
                    fontSize = if (keySize >= 80.dp) 28.sp else 24.sp,
                    fontWeight = if (isEquals) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
