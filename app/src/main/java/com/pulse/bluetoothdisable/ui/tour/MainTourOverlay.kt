package com.pulse.bluetoothdisable.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pulse.bluetoothdisable.R
import kotlin.math.roundToInt

internal enum class MainTourTarget(val title: Int, val body: Int) {
    THEME(R.string.tour_theme_title, R.string.tour_theme_body),
    LANGUAGE(R.string.tour_language_title, R.string.tour_language_body),
    PROTECTION(R.string.tour_protection_title, R.string.tour_protection_body),
    COVER(R.string.tour_cover_title, R.string.tour_cover_body),
    TILE(R.string.tour_tile_title, R.string.tour_tile_body),
    LAUNCHER(R.string.tour_launcher_title, R.string.tour_launcher_body),
    UPDATES(R.string.tour_updates_title, R.string.tour_updates_body),
}

@Composable
internal fun MainTourOverlay(
    target: MainTourTarget,
    bounds: Rect,
    step: Int,
    total: Int,
    freshInstall: Boolean,
    onAdvance: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val density = LocalDensity.current
    val title = stringResource(target.title)
    val instruction = stringResource(if (step == total) R.string.tour_tap_finish else R.string.tour_tap_next)
    var cardHeight by remember { mutableIntStateOf(0) }
    val topInset = WindowInsets.statusBars.getTop(density)
    val bottomInset = WindowInsets.navigationBars.getBottom(density)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("main_tour").semantics { paneTitle = title }) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val gap = with(density) { 12.dp.toPx() }
        val margin = with(density) { 16.dp.toPx() }
        val minTop = topInset + gap
        val maxBottom = height - bottomInset - gap
        val hole = Rect(
            (bounds.left - gap / 2).coerceIn(0f, width),
            (bounds.top - gap / 2).coerceIn(0f, height),
            (bounds.right + gap / 2).coerceIn(0f, width),
            (bounds.bottom + gap / 2).coerceIn(0f, height),
        )
        val above = (hole.top - gap - minTop).coerceAtLeast(0f)
        val below = (maxBottom - hole.bottom - gap).coerceAtLeast(0f)
        // Constrain the scrollable card to one side of the target, including short windows.
        val available = maxOf(above, below).coerceAtLeast(1f)
        val cardTop = (if (above >= cardHeight || above >= below) hole.top - gap - cardHeight
            else hole.bottom + gap).coerceIn(minTop, (maxBottom - cardHeight).coerceAtLeast(minTop))
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { /* Block background actions. */ } }) {
            val shade = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(RoundRect(hole, CornerRadius(16.dp.toPx())))
            }
            drawPath(shade, Color.Black.copy(alpha = 0.4f))
            drawRoundRect(Color.White.copy(alpha = 0.9f), hole.topLeft, hole.size,
                CornerRadius(16.dp.toPx()), style = Stroke(2.dp.toPx()))
        }
        Box(
            Modifier.offset { IntOffset(hole.left.roundToInt(), hole.top.roundToInt()) }
                .size(with(density) { hole.width.toDp() }, with(density) { hole.height.toDp() })
                .clickable(role = Role.Button, onClickLabel = instruction, onClick = onAdvance)
                .semantics { contentDescription = "$title. $instruction" }
                .testTag("main_tour_target_${target.name}"),
        )
        Surface(
            modifier = Modifier.offset { IntOffset(margin.roundToInt(), cardTop.roundToInt()) }
                .width(maxWidth - 32.dp)
                .heightIn(max = with(density) { available.toDp() })
                .onSizeChanged { cardHeight = it.height }
                .testTag("main_tour_card"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(if (total == 1) stringResource(R.string.tour_short_label) else "$step / $total",
                        style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("main_tour_skip")) {
                        Text(stringResource(R.string.tour_skip))
                    }
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(if (freshInstall) R.string.tour_updates_fresh_body else target.body), modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge)
                Text(instruction, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
