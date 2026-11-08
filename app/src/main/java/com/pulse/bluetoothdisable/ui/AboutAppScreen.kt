package com.pulse.bluetoothdisable.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class AboutPage { OVERVIEW, LICENSES, APP_LICENSE, COMPONENTS, COMPONENT_LICENSE }

private data class AboutLicenseComponent(
    val name: String,
    val copyright: String,
    val license: String,
    val asset: String,
)

// Principal Android runtime components. The build's dependency license audit
// remains authoritative for transitive dependencies and checks metadata in CI.
private val aboutLicenseComponents = listOf(
    AboutLicenseComponent("Kotlin", "JetBrains and contributors", "Apache-2.0", "legal/apache_2_0.txt"),
    AboutLicenseComponent("Jetpack Compose", "Android Open Source Project contributors", "Apache-2.0", "legal/apache_2_0.txt"),
    AboutLicenseComponent("Material 3", "Android Open Source Project contributors", "Apache-2.0", "legal/apache_2_0.txt"),
    AboutLicenseComponent("AndroidX", "Android Open Source Project contributors", "Apache-2.0", "legal/apache_2_0.txt"),
    AboutLicenseComponent("OkHttp", "Square and contributors", "Apache-2.0", "legal/apache_2_0.txt"),
    AboutLicenseComponent("jsoup", "Jonathan Hedley and contributors", "MIT", "legal/mit.txt"),
    AboutLicenseComponent("Primer Octicons (GitHub icon)", "GitHub Inc.", "MIT", "licenses/primer-octicons.txt"),
)

@Composable
internal fun AboutAppScreen(
    appVersion: String,
    selectedTheme: String,
    onBack: () -> Unit,
    onPrivacyClick: () -> Unit,
    onGitHubClick: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(AboutPage.OVERVIEW) }
    var selectedComponent by rememberSaveable { mutableIntStateOf(0) }
    val dark = selectedTheme == ThemeManager.DARK
    val foreground = if (dark) Color.White else Color.Black
    val background = if (dark) Color.Black else Color.White
    val cardColor = if (dark) Color(0xFF1B1B1B) else Color(0xFFF2F2F2)
    val lineColor = if (dark) Color(0xFF454545) else Color(0xFFD6D6D6)

    fun goBack() {
        page = when (page) {
            AboutPage.OVERVIEW -> { onBack(); return }
            AboutPage.LICENSES -> AboutPage.OVERVIEW
            AboutPage.APP_LICENSE, AboutPage.COMPONENTS -> AboutPage.LICENSES
            AboutPage.COMPONENT_LICENSE -> AboutPage.COMPONENTS
        }
    }

    BackHandler { goBack() }

    val header = when (page) {
        AboutPage.OVERVIEW -> stringResource(R.string.about_title)
        AboutPage.LICENSES -> stringResource(R.string.about_licenses)
        AboutPage.APP_LICENSE -> stringResource(R.string.about_license_app)
        AboutPage.COMPONENTS -> stringResource(R.string.about_license_components)
        AboutPage.COMPONENT_LICENSE -> aboutLicenseComponents[selectedComponent].name
    }

    Surface(
        modifier = Modifier.fillMaxSize().testTag("about_app_screen"),
        color = background,
        contentColor = foreground,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::goBack, modifier = Modifier.testTag("about_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.about_back))
                }
                Text(
                    header,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Column(
                Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (page) {
                    AboutPage.OVERVIEW -> {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Image(
                                painter = painterResource(R.mipmap.ic_launcher),
                                contentDescription = null,
                                modifier = Modifier.size(104.dp).testTag("about_app_icon"),
                            )
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("about_app_name"),
                            )
                            Text(
                                stringResource(R.string.about_version, appVersion),
                                style = MaterialTheme.typography.bodyMedium,
                                color = foreground.copy(alpha = 0.7f),
                                modifier = Modifier.testTag("about_app_version"),
                            )
                        }
                        Surface(
                            color = cardColor,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("about_links_card"),
                        ) {
                            Column {
                                AboutRow(
                                    title = stringResource(R.string.about_licenses),
                                    icon = R.drawable.ic_about_license,
                                    tag = "about_licenses",
                                    tint = foreground,
                                ) { page = AboutPage.LICENSES }
                                HorizontalDivider(color = lineColor, modifier = Modifier.padding(horizontal = 16.dp))
                                AboutRow(
                                    title = stringResource(R.string.privacy_policy),
                                    icon = R.drawable.ic_about_privacy,
                                    tag = "about_privacy",
                                    tint = foreground,
                                    onClick = onPrivacyClick,
                                )
                                HorizontalDivider(color = lineColor, modifier = Modifier.padding(horizontal = 16.dp))
                                AboutRow(
                                    title = stringResource(R.string.about_github),
                                    icon = R.drawable.ic_github_project,
                                    tag = "about_github",
                                    tint = foreground,
                                    onClick = onGitHubClick,
                                )
                            }
                        }
                    }

                    AboutPage.LICENSES -> {
                        Surface(color = cardColor, shape = RoundedCornerShape(14.dp)) {
                            Column {
                                AboutRow(
                                    stringResource(R.string.about_license_app),
                                    R.drawable.ic_about_license,
                                    "license_app_item",
                                    foreground,
                                ) { page = AboutPage.APP_LICENSE }
                                HorizontalDivider(color = lineColor, modifier = Modifier.padding(horizontal = 16.dp))
                                AboutRow(
                                    stringResource(R.string.about_license_components),
                                    R.drawable.ic_about_license,
                                    "license_components_item",
                                    foreground,
                                ) { page = AboutPage.COMPONENTS }
                            }
                        }
                    }

                    AboutPage.APP_LICENSE -> {
                        Text(stringResource(R.string.about_license_gpl), style = MaterialTheme.typography.titleMedium)
                        AboutOfflineLicenseText(asset = "legal/gpl_3.txt", color = foreground)
                    }

                    AboutPage.COMPONENTS -> {
                        Text(
                            stringResource(R.string.about_components_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = foreground.copy(alpha = 0.7f),
                        )
                        Surface(color = cardColor, shape = RoundedCornerShape(14.dp)) {
                            Column {
                                aboutLicenseComponents.forEachIndexed { index, component ->
                                    AboutRow(
                                        title = component.name,
                                        icon = R.drawable.ic_about_license,
                                        tag = "about_component_$index",
                                        tint = foreground,
                                    ) {
                                        selectedComponent = index
                                        page = AboutPage.COMPONENT_LICENSE
                                    }
                                    if (index != aboutLicenseComponents.lastIndex) {
                                        HorizontalDivider(color = lineColor, modifier = Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                            }
                        }
                    }

                    AboutPage.COMPONENT_LICENSE -> {
                        val component = aboutLicenseComponents[selectedComponent]
                        Text(
                            stringResource(R.string.about_copyright, component.copyright),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.about_component_license, component.license),
                            style = MaterialTheme.typography.bodyMedium,
                            color = foreground.copy(alpha = 0.7f),
                        )
                        AboutOfflineLicenseText(asset = component.asset, color = foreground)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutRow(
    title: String,
    icon: Int,
    tag: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .testTag(tag).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = tint,
        )
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = tint.copy(alpha = 0.7f))
    }
}

@Composable
private fun AboutOfflineLicenseText(asset: String, color: Color) {
    val context = LocalContext.current
    val text by produceState(initialValue = "", key1 = asset) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(asset).bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }
    }
    Text(
        text.ifEmpty { stringResource(R.string.about_license_unavailable) },
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.fillMaxWidth().testTag("about_license_full_text"),
    )
}
