package com.pulse.bluetoothdisable.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.theme.ThemeManager

// Local colors only. Do not modify BluetoothDisableTheme or Cover Mode palettes.
internal data class MainPagePalette(
    val background: Color,
    val card: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val onAccent: Color,
    val divider: Color,
)

internal fun mainPagePalette(dark: Boolean): MainPagePalette =
    if (dark) MainPagePalette(
        background = Color(0xFF121110),
        card = Color(0xFF25221F),
        primaryText = Color(0xFFF4F0E9),
        secondaryText = Color(0xFFC4BDB3),
        accent = Color(0xFFD8C4AE),
        onAccent = Color(0xFF121110),
        divider = Color(0xFF4C453E),
    ) else MainPagePalette(
        background = Color(0xFFF7F4EE),
        card = Color(0xFFEEEAE3),
        primaryText = Color(0xFF20211F),
        secondaryText = Color(0xFF62645D),
        accent = Color(0xFF786859),
        onAccent = Color.White,
        divider = Color(0xFFD6CFC4),
    )

internal enum class MainSelectionSheet { THEME, LANGUAGE }

@Composable
internal fun MainRowDivider(palette: MainPagePalette) {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = palette.divider,
    )
}

@Composable
internal fun MainActionRow(
    text: String,
    palette: MainPagePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentText: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (accentText) palette.accent else palette.primaryText,
        )
    }
}

@Composable
internal fun MainHintRow(
    text: String,
    palette: MainPagePalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText)
    }
}

@Composable
internal fun MainInlineLink(
    text: String,
    palette: MainPagePalette,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = palette.accent,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

@Composable
internal fun MainPreferenceRow(
    title: String,
    value: String,
    palette: MainPagePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = palette.primaryText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = palette.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = palette.secondaryText,
        )
    }
}

@Composable
internal fun MainThemeSelectionSheet(
    selectedTheme: String,
    palette: MainPagePalette,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    MainChoiceSheet(
        title = stringResource(R.string.main_theme_sheet_title),
        palette = palette,
        onDismiss = onDismiss,
    ) {
        MainChoiceRow(
            label = stringResource(R.string.main_theme_light),
            selected = selectedTheme == ThemeManager.LIGHT,
            palette = palette,
            modifier = Modifier.testTag("main_theme_light_option"),
            onClick = { onSelected(ThemeManager.LIGHT) },
        )
        MainRowDivider(palette)
        MainChoiceRow(
            label = stringResource(R.string.main_theme_dark),
            selected = selectedTheme == ThemeManager.DARK,
            palette = palette,
            modifier = Modifier.testTag("main_theme_dark_option"),
            onClick = { onSelected(ThemeManager.DARK) },
        )
    }
}

@Composable
internal fun MainLanguageSelectionSheet(
    selectedLanguage: String,
    palette: MainPagePalette,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    MainChoiceSheet(
        title = stringResource(R.string.main_language_sheet_title),
        palette = palette,
        onDismiss = onDismiss,
    ) {
        MainChoiceRow(
            label = stringResource(R.string.main_language_russian),
            selected = selectedLanguage == LanguageManager.RUSSIAN,
            palette = palette,
            modifier = Modifier.testTag("main_language_ru_option"),
            onClick = { onSelected(LanguageManager.RUSSIAN) },
        )
        MainRowDivider(palette)
        MainChoiceRow(
            label = stringResource(R.string.main_language_english),
            selected = selectedLanguage == LanguageManager.ENGLISH,
            palette = palette,
            modifier = Modifier.testTag("main_language_en_option"),
            onClick = { onSelected(LanguageManager.ENGLISH) },
        )
    }
}

@Composable
private fun MainChoiceSheet(
    title: String,
    palette: MainPagePalette,
    onDismiss: () -> Unit,
    choices: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = palette.background,
        contentColor = palette.primaryText,
        dragHandle = { BottomSheetDefaults.DragHandle(color = palette.secondaryText) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.primaryText,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = palette.card,
                contentColor = palette.primaryText,
            ) {
                Column(Modifier.selectableGroup()) { choices() }
            }
        }
    }
}

@Composable
private fun MainChoiceRow(
    label: String,
    selected: Boolean,
    palette: MainPagePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.primaryText,
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = palette.accent)
        }
    }
}
