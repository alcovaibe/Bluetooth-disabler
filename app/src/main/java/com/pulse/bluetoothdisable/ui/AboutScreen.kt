package com.pulse.bluetoothdisable.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.privacy.GitHubProjectLauncher
import com.pulse.bluetoothdisable.privacy.PrivacyPolicyActivity
import com.pulse.bluetoothdisable.theme.ThemeManager

private enum class AboutPage { HOME, LICENSES, APP_LICENSE, COMPONENTS, APACHE, MIT }

@Composable
internal fun AboutScreen(
    appVersion: String,
    selectedLanguage: String,
    selectedTheme: String,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(AboutPage.HOME) }
    val context = LocalContext.current
    val isDark = selectedTheme == ThemeManager.DARK
    val background = if (isDark) Color.Black else Color.White
    val foreground = if (isDark) Color.White else Color.Black
    val cardColor = if (isDark) Color(0xFF1D2432) else Color(0xFFF1F3F7)

    fun goBack() {
        page = when (page) {
            AboutPage.HOME -> { onBack(); AboutPage.HOME }
            AboutPage.LICENSES -> AboutPage.HOME
            AboutPage.APP_LICENSE, AboutPage.COMPONENTS -> AboutPage.LICENSES
            AboutPage.APACHE, AboutPage.MIT -> AboutPage.COMPONENTS
        }
    }
    BackHandler { goBack() }

    Surface(Modifier.fillMaxSize().testTag(
        when (page) {
            AboutPage.HOME -> "about_app_screen"
            AboutPage.LICENSES -> "licenses_screen"
            AboutPage.APP_LICENSE, AboutPage.APACHE, AboutPage.MIT -> "license_document_screen"
            AboutPage.COMPONENTS -> "license_components_screen"
        }
    ), color = background, contentColor = foreground) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::goBack, modifier = Modifier.testTag("about_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.about_back))
                }
                Text(
                    stringResource(
                        when (page) {
                            AboutPage.HOME -> R.string.about_app_title
                            AboutPage.LICENSES -> R.string.about_licenses
                            AboutPage.APP_LICENSE -> R.string.about_app_license
                            AboutPage.COMPONENTS -> R.string.about_third_party
                            AboutPage.APACHE -> R.string.about_license_apache
                            AboutPage.MIT -> R.string.about_license_mit
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (page) {
                    AboutPage.HOME -> {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(96.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = colorResource(R.color.ic_launcher_background),
                            ) {
                                Image(
                                    painterResource(R.mipmap.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Text(stringResource(R.string.about_app_name),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center)
                            Text("v $appVersion", modifier = Modifier.testTag("about_version"),
                                color = foreground.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        AboutCard(cardColor) {
                            AboutRow(
                                R.drawable.ic_about_license, stringResource(R.string.about_licenses),
                                "about_licenses", foreground
                            ) { page = AboutPage.LICENSES }
                            HorizontalDivider(color = foreground.copy(alpha = 0.15f))
                            AboutRow(
                                R.drawable.ic_about_privacy, stringResource(R.string.privacy_policy),
                                "about_privacy", foreground
                            ) { context.startActivity(PrivacyPolicyActivity.intent(context, selectedLanguage)) }
                            HorizontalDivider(color = foreground.copy(alpha = 0.15f))
                            AboutRow(
                                R.drawable.ic_github_project, stringResource(R.string.about_project_github),
                                "about_github", foreground
                            ) { GitHubProjectLauncher.open(context) }
                        }
                    }
                    AboutPage.LICENSES -> AboutCard(cardColor) {
                        AboutRow(R.drawable.ic_about_license, stringResource(R.string.about_app_license),
                            "licenses_application", foreground) { page = AboutPage.APP_LICENSE }
                        HorizontalDivider(color = foreground.copy(alpha = 0.15f))
                        AboutRow(R.drawable.ic_about_license, stringResource(R.string.about_third_party),
                            "licenses_components", foreground) { page = AboutPage.COMPONENTS }
                    }
                    AboutPage.APP_LICENSE -> LicenseText("legal/gpl-3.0.txt", foreground)
                    AboutPage.COMPONENTS -> {
                        Text(stringResource(R.string.about_dependencies_notice),
                            style = MaterialTheme.typography.bodyMedium)
                        AboutCard(cardColor) {
                            AboutRow(R.drawable.ic_about_license, stringResource(R.string.about_license_apache),
                                "licenses_apache", foreground) { page = AboutPage.APACHE }
                            HorizontalDivider(color = foreground.copy(alpha = 0.15f))
                            AboutRow(R.drawable.ic_about_license, stringResource(R.string.about_license_mit),
                                "licenses_mit", foreground) { page = AboutPage.MIT }
                        }
                        Text(
                            "Kotlin, Jetpack Compose, AndroidX, Material 3, OkHttp, jsoup, Primer Octicons",
                            style = MaterialTheme.typography.bodySmall,
                            color = foreground.copy(alpha = 0.75f),
                        )
                    }
                    AboutPage.APACHE -> LicenseText("legal/apache-2.0.txt", foreground)
                    AboutPage.MIT -> LicenseText("licenses/primer-octicons.txt", foreground)
                }
            }
        }
    }
}

@Composable
private fun AboutCard(color: Color, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = color, modifier = Modifier.fillMaxWidth()) {
        Column(content = { content() })
    }
}

@Composable
private fun AboutRow(icon: Int, label: String, tag: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(onClick = onClick).testTag(tag)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = tint.copy(alpha = 0.65f))
    }
}

@Composable
private fun LicenseText(path: String, tint: Color) {
    val context = LocalContext.current
    val text = remember(context, path) {
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }
            .getOrNull()
    }
    Text(
        text ?: stringResource(R.string.about_license_missing),
        color = tint,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().testTag("about_license_text"),
    )
}
