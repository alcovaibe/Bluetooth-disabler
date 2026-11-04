package com.pulse.bluetoothdisable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.domain.ProtectionError
import com.pulse.bluetoothdisable.domain.ProtectionState
import com.pulse.bluetoothdisable.launcher.LauncherStyle
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.privacy.GitHubHelpLauncher
import com.pulse.bluetoothdisable.privacy.GitHubProjectLauncher
import com.pulse.bluetoothdisable.privacy.PrivacyPolicyActivity
import com.pulse.bluetoothdisable.theme.ThemeManager
import com.pulse.bluetoothdisable.ui.theme.AppColors
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import com.pulse.bluetoothdisable.ui.tour.MainTourKind
import com.pulse.bluetoothdisable.ui.tour.MainTourOverlay
import com.pulse.bluetoothdisable.ui.tour.MainTourTarget

@Composable
fun MainScreen(
    uiState: ProtectionUiState,
    appVersion: String,
    launcherIconHidden: Boolean,
    selectedLauncherStyle: LauncherStyle,
    tileAdded: Boolean,
    canRequestTile: Boolean,
    selectedLanguage: String,
    selectedTheme: String,
    showHideAction: Boolean,
    onHideToCover: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onThemeSelected: (String) -> Unit,
    onEnableProtection: () -> Unit,
    onDisableProtection: () -> Unit,
    onRefresh: () -> Unit,
    onLauncherStyleSelected: (LauncherStyle) -> Unit,
    onRequestAddTile: () -> Unit,
    allowLauncherWithoutDeviceOwner: Boolean = false,
    testAccessControls: @Composable () -> Unit = {},
    updateControls: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    tourKind: MainTourKind = MainTourKind.NONE,
    onTourDismiss: () -> Unit = {},
) {
    var showIconDialog by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<MainSelectionSheet?>(null) }
    val canChangeLauncher = uiState.isDeviceOwner || allowLauncherWithoutDeviceOwner
    LaunchedEffect(canChangeLauncher) {
        if (!canChangeLauncher) showIconDialog = false
    }
    val context = LocalContext.current

    var tourStep by rememberSaveable(tourKind) { mutableIntStateOf(0) }
    val tourTargets = if (tourKind == MainTourKind.FULL) buildList {
        add(MainTourTarget.THEME)
        add(MainTourTarget.LANGUAGE)
        add(MainTourTarget.PROTECTION)
        if (showHideAction) add(MainTourTarget.COVER)
        if (!tileAdded) add(MainTourTarget.TILE)
        if (canChangeLauncher) add(MainTourTarget.LAUNCHER)
        add(MainTourTarget.UPDATES)
    } else if (tourKind == MainTourKind.UPDATES) listOf(MainTourTarget.UPDATES) else emptyList()
    val tourTarget = tourTargets.getOrNull(tourStep)
    val tourActive = tourTarget != null
    val anchors = remember { mutableStateMapOf<MainTourTarget, Rect>() }
    val requesters = remember { MainTourTarget.entries.associateWith { BringIntoViewRequester() } }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    fun targetModifier(target: MainTourTarget) = Modifier
        .bringIntoViewRequester(requesters.getValue(target))
        .onGloballyPositioned { anchors[target] = it.boundsInRoot() }
        .testTag("main_target_${target.name}")
    LaunchedEffect(tourTarget) {
        if (tourTarget != null) {
            withFrameNanos { }
            requesters.getValue(tourTarget).bringIntoView()
        }
    }

    val palette = mainPagePalette(selectedTheme == ThemeManager.DARK)

    // All redesigned colors are intentionally scoped to this screen. Existing sheets
    // and the app-wide MaterialTheme (including Cover Mode) are left untouched.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = palette.background,
        contentColor = palette.primaryText,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().testTag("main_screen")
                .onGloballyPositioned { rootOrigin = it.positionInRoot() },
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .then(if (tourActive) Modifier.clearAndSetSemantics { } else Modifier),
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = stringResource(R.string.protection_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryText,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        color = palette.card,
                        contentColor = palette.primaryText,
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            val statusText = when (uiState.state) {
                                ProtectionState.NOT_PROVISIONED -> R.string.main_status_not_provisioned
                                ProtectionState.READY -> R.string.status_ready
                                ProtectionState.ENABLING -> R.string.status_enabling
                                ProtectionState.PROTECTED -> R.string.status_protected
                                ProtectionState.DISABLING -> R.string.status_disabling
                                ProtectionState.ERROR -> R.string.status_error
                                ProtectionState.UNSUPPORTED -> R.string.status_unsupported
                            }
                            Text(
                                text = stringResource(statusText),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.primaryText,
                            )

                            val messageText = when (uiState.state) {
                                ProtectionState.NOT_PROVISIONED -> stringResource(R.string.message_not_provisioned)
                                ProtectionState.READY -> stringResource(R.string.message_ready)
                                ProtectionState.ENABLING -> stringResource(R.string.message_enabling)
                                ProtectionState.PROTECTED -> stringResource(R.string.message_protected)
                                ProtectionState.DISABLING -> stringResource(R.string.message_disabling)
                                ProtectionState.ERROR -> stringResource(errorMessageResource(uiState.error))
                                ProtectionState.UNSUPPORTED -> stringResource(R.string.message_unsupported)
                            }
                            Text(
                                text = messageText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.secondaryText,
                                modifier = Modifier.fillMaxWidth().then(
                                    if (uiState.state in setOf(
                                            ProtectionState.ENABLING,
                                            ProtectionState.DISABLING,
                                            ProtectionState.UNSUPPORTED,
                                        )
                                    ) targetModifier(MainTourTarget.PROTECTION) else Modifier
                                ),
                            )

                            if (uiState.state == ProtectionState.NOT_PROVISIONED) {
                                MainInlineLink(
                                    text = stringResource(R.string.provisioning_help),
                                    palette = palette,
                                    onClick = { GitHubHelpLauncher.open(context, selectedLanguage) },
                                )
                            }

                            if (uiState.state == ProtectionState.ENABLING ||
                                uiState.state == ProtectionState.DISABLING
                            ) {
                                CircularProgressIndicator(color = palette.accent)
                            }

                            when (uiState.state) {
                                ProtectionState.READY -> ActionButton(
                                    text = stringResource(R.string.action_enable),
                                    onClick = onEnableProtection,
                                    palette = palette,
                                    modifier = targetModifier(MainTourTarget.PROTECTION),
                                )
                                ProtectionState.PROTECTED -> ActionButton(
                                    text = stringResource(R.string.action_disable),
                                    onClick = onDisableProtection,
                                    palette = palette,
                                    modifier = targetModifier(MainTourTarget.PROTECTION),
                                )
                                ProtectionState.NOT_PROVISIONED,
                                ProtectionState.ERROR -> ActionButton(
                                    text = stringResource(R.string.action_refresh),
                                    onClick = onRefresh,
                                    palette = palette,
                                    modifier = targetModifier(MainTourTarget.PROTECTION),
                                )
                                ProtectionState.ENABLING,
                                ProtectionState.DISABLING,
                                ProtectionState.UNSUPPORTED -> Unit
                            }
                        }
                    }

                    if (launcherIconHidden) {
                        Text(
                            text = stringResource(R.string.launcher_hidden),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.secondaryText,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Preserve every original visibility condition and callback.
                    if (showHideAction || !tileAdded || canChangeLauncher) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            color = palette.card,
                            contentColor = palette.primaryText,
                        ) {
                            Column {
                                var hasPreviousRow = false
                                if (showHideAction) {
                                    MainActionRow(
                                        text = stringResource(R.string.action_hide_to_cover),
                                        palette = palette,
                                        onClick = onHideToCover,
                                        modifier = targetModifier(MainTourTarget.COVER),
                                    )
                                    hasPreviousRow = true
                                }
                                if (!tileAdded) {
                                    if (hasPreviousRow) MainRowDivider(palette)
                                    if (canRequestTile) {
                                        MainActionRow(
                                            text = stringResource(R.string.action_add_tile),
                                            palette = palette,
                                            onClick = onRequestAddTile,
                                            modifier = targetModifier(MainTourTarget.TILE),
                                        )
                                    } else {
                                        MainHintRow(
                                            text = stringResource(R.string.tile_manual_hint),
                                            palette = palette,
                                            modifier = targetModifier(MainTourTarget.TILE),
                                        )
                                    }
                                    hasPreviousRow = true
                                }
                                if (canChangeLauncher) {
                                    if (hasPreviousRow) MainRowDivider(palette)
                                    MainActionRow(
                                        text = stringResource(R.string.action_hide_launcher),
                                        palette = palette,
                                        onClick = { showIconDialog = true },
                                        modifier = targetModifier(MainTourTarget.LAUNCHER),
                                    )
                                    MainRowDivider(palette)
                                    MainActionRow(
                                        text = stringResource(R.string.action_project_github),
                                        palette = palette,
                                        onClick = { GitHubProjectLauncher.open(context) },
                                        modifier = Modifier.testTag("project_github"),
                                    )
                                }
                            }
                        }
                    }

                    testAccessControls()

                    Text(
                        text = stringResource(R.string.main_appearance_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryText,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("main_appearance_card"),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        color = palette.card,
                        contentColor = palette.primaryText,
                    ) {
                        Column {
                            MainPreferenceRow(
                                title = stringResource(R.string.main_theme_label),
                                value = stringResource(
                                    if (selectedTheme == ThemeManager.DARK)
                                        R.string.main_theme_dark else R.string.main_theme_light
                                ),
                                palette = palette,
                                onClick = { activeSheet = MainSelectionSheet.THEME },
                                modifier = targetModifier(MainTourTarget.THEME).testTag("main_theme_row"),
                            )
                            MainRowDivider(palette)
                            MainPreferenceRow(
                                title = stringResource(R.string.main_language_label),
                                value = stringResource(
                                    if (selectedLanguage == LanguageManager.RUSSIAN)
                                        R.string.main_language_russian else R.string.main_language_english
                                ),
                                palette = palette,
                                onClick = { activeSheet = MainSelectionSheet.LANGUAGE },
                                modifier = targetModifier(MainTourTarget.LANGUAGE).testTag("main_language_row"),
                            )
                        }
                    }

                    MainActionRow(
                        text = stringResource(R.string.privacy_policy),
                        palette = palette,
                        accentText = true,
                        modifier = Modifier.testTag("main_privacy_policy"),
                        onClick = {
                            context.startActivity(PrivacyPolicyActivity.intent(context, selectedLanguage))
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "v $appVersion",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondaryText,
                    )
                    Box(
                        modifier = targetModifier(MainTourTarget.UPDATES)
                            .testTag("main_updates_area"),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The existing update panel and its dialogs are not restyled.
                        updateControls()
                    }
                }
            }

            val bounds = tourTarget?.let { anchors[it] }?.translate(-rootOrigin)
            if (tourTarget != null && bounds != null && bounds.width > 0 && bounds.height > 0) {
                MainTourOverlay(
                    target = tourTarget,
                    bounds = bounds,
                    step = tourStep + 1,
                    total = tourTargets.size,
                    freshInstall = tourKind == MainTourKind.UPDATES,
                    onAdvance = {
                        if (tourStep + 1 >= tourTargets.size) onTourDismiss() else tourStep++
                    },
                    onDismiss = onTourDismiss,
                )
            }
        }
    }

    when (activeSheet) {
        MainSelectionSheet.THEME -> MainThemeSelectionSheet(
            selectedTheme = selectedTheme,
            palette = palette,
            onSelected = { theme ->
                activeSheet = null
                onThemeSelected(theme)
            },
            onDismiss = { activeSheet = null },
        )
        MainSelectionSheet.LANGUAGE -> MainLanguageSelectionSheet(
            selectedLanguage = selectedLanguage,
            palette = palette,
            onSelected = { language ->
                activeSheet = null
                onLanguageSelected(language)
            },
            onDismiss = { activeSheet = null },
        )
        null -> Unit
    }


    if (showIconDialog && canChangeLauncher) {
        LauncherIconSelectionDialog(
            selectedStyle = selectedLauncherStyle,
            onStyleSelected = { style ->
                showIconDialog = false
                onLauncherStyleSelected(style)
            },
            onDismiss = { showIconDialog = false },
        )
    }
}

private fun errorMessageResource(error: ProtectionError?): Int = when (error) {
    ProtectionError.READ_POLICY -> R.string.error_read_policy
    ProtectionError.ENABLE_NOT_CONFIRMED -> R.string.error_enable_not_confirmed
    ProtectionError.ENABLE_FAILED -> R.string.error_enable_failed
    ProtectionError.DISABLE_NOT_CONFIRMED -> R.string.error_disable_not_confirmed
    ProtectionError.DISABLE_FAILED -> R.string.error_disable_failed
    null -> R.string.message_error
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    palette: MainPagePalette,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.accent,
            contentColor = palette.onAccent,
        ),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, name = "Light / English")
@Composable
private fun MainScreenPreview() {
    BluetoothDisableTheme {
        MainScreen(
            uiState = ProtectionUiState(state = ProtectionState.READY, isDeviceOwner = true),
            appVersion = "1.0.6",
            launcherIconHidden = false,
            selectedLauncherStyle = LauncherStyle.DEFAULT,
            tileAdded = false,
            canRequestTile = true,
            selectedLanguage = LanguageManager.ENGLISH,
            selectedTheme = ThemeManager.LIGHT,
            showHideAction = true,
            onHideToCover = {},
            onLanguageSelected = {},
            onThemeSelected = {},
            onEnableProtection = {},
            onDisableProtection = {},
            onRefresh = {},
            onLauncherStyleSelected = {},
            onRequestAddTile = {},
        )
    }
}
