package com.pulse.bluetoothdisable.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.privacy.GitHubProjectLauncher
import com.pulse.bluetoothdisable.privacy.PrivacyPolicyActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class AboutDestination { MAIN, LICENSES, APP_LICENSE, COMPONENTS, COMPONENT_DETAIL }

private data class ComponentLicense(
    val name: String,
    val owner: String,
    val spdx: String = "Apache-2.0",
)

// Major application/runtime components. Full resolved dependency inventory and
// license audit are published in the CI dependency-license-audit artifact.
private val componentLicenses = listOf(
    ComponentLicense("Kotlin", "JetBrains / Kotlin contributors"),
    ComponentLicense("Kotlin Coroutines", "JetBrains / contributors"),
    ComponentLicense("Jetpack Compose", "Android Open Source Project / contributors"),
    ComponentLicense("Material 3", "Android Open Source Project / contributors"),
    ComponentLicense("AndroidX Activity", "Android Open Source Project / contributors"),
    ComponentLicense("AndroidX Lifecycle", "Android Open Source Project / contributors"),
    ComponentLicense("AndroidX Core", "Android Open Source Project / contributors"),
    ComponentLicense("AndroidX AppCompat", "Android Open Source Project / contributors"),
    ComponentLicense("AndroidX Biometric", "Android Open Source Project / contributors"),
    ComponentLicense("AndroidX Browser", "Android Open Source Project / contributors"),
    ComponentLicense("OkHttp", "Square / contributors"),
    ComponentLicense("Okio", "Square / contributors"),
    ComponentLicense("jsoup", "Jonathan Hedley / jsoup contributors", "MIT"),
)

@Composable
internal fun AboutAppScreen(
    appVersion: String,
    selectedLanguage: String,
    onBack: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(AboutDestination.MAIN) }
    var selectedComponent by rememberSaveable { mutableIntStateOf(-1) }
    val context = LocalContext.current

    fun back() {
        destination = when (destination) {
            AboutDestination.MAIN -> { onBack(); AboutDestination.MAIN }
            AboutDestination.LICENSES -> AboutDestination.MAIN
            AboutDestination.APP_LICENSE, AboutDestination.COMPONENTS -> AboutDestination.LICENSES
            AboutDestination.COMPONENT_DETAIL -> AboutDestination.COMPONENTS
        }
    }

    BackHandler { back() }
    Surface(
        modifier = Modifier.fillMaxSize().testTag("about_app_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::back, modifier = Modifier.testTag("about_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.about_back))
                }
                Text(
                    when (destination) {
                        AboutDestination.MAIN -> stringResource(R.string.about_app_title)
                        AboutDestination.LICENSES -> stringResource(R.string.about_licenses_title)
                        AboutDestination.APP_LICENSE -> stringResource(R.string.about_app_license)
                        AboutDestination.COMPONENTS -> stringResource(R.string.about_components)
                        AboutDestination.COMPONENT_DETAIL -> componentLicenses.getOrNull(selectedComponent)?.name
                            ?: stringResource(R.string.about_components)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (destination) {
                    AboutDestination.MAIN -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = colorResource(R.color.ic_launcher_background),
                            ) {
                                Image(
                                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp).testTag("about_app_logo"),
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                stringResource(R.string.app_name),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.about_version, appVersion),
                                modifier = Modifier.testTag("about_version"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AboutMenuCard {
                            AboutMenuRow(R.drawable.ic_license_document, stringResource(R.string.about_licenses_title),
                                "about_open_licenses") { destination = AboutDestination.LICENSES }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            AboutMenuRow(R.drawable.ic_privacy_document, stringResource(R.string.privacy_policy),
                                "about_open_privacy") {
                                context.startActivity(PrivacyPolicyActivity.intent(context, selectedLanguage))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            AboutMenuRow(R.drawable.ic_github_project, stringResource(R.string.about_github_project),
                                "about_open_github") { GitHubProjectLauncher.open(context) }
                        }
                    }
                    AboutDestination.LICENSES -> AboutMenuCard {
                        AboutMenuRow(R.drawable.ic_license_document, stringResource(R.string.about_app_license),
                            "about_license_app") { destination = AboutDestination.APP_LICENSE }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AboutMenuRow(R.drawable.ic_license_document, stringResource(R.string.about_components),
                            "about_license_components") { destination = AboutDestination.COMPONENTS }
                    }
                    AboutDestination.APP_LICENSE -> AboutLegalText("legal/app_license.txt")
                    AboutDestination.COMPONENTS -> AboutMenuCard {
                        componentLicenses.forEachIndexed { index, item ->
                            if (index != 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            AboutMenuRow(R.drawable.ic_license_document, item.name,
                                "about_component_" + index) {
                                selectedComponent = index
                                destination = AboutDestination.COMPONENT_DETAIL
                            }
                        }
                    }
                    AboutDestination.COMPONENT_DETAIL -> {
                        componentLicenses.getOrNull(selectedComponent)?.let { component ->
                            Text(stringResource(R.string.about_component_owner, component.owner))
                            Text(
                                stringResource(R.string.about_license_label, component.spdx),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AboutLegalText(
                                if (component.spdx == "MIT") "legal/mit.txt" else "legal/apache_2_0.txt"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutMenuCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun AboutMenuRow(
    icon: Int,
    title: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon), null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutLegalText(path: String) {
    val context = LocalContext.current
    val error = stringResource(R.string.about_document_error)
    val loading = stringResource(R.string.about_loading)
    val content by produceState(loading, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).bufferedReader().use { it.readText() }
            }.getOrDefault(error)
        }
    }
    Text(
        text = content,
        modifier = Modifier.fillMaxWidth().testTag("about_license_text"),
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
    )
}
