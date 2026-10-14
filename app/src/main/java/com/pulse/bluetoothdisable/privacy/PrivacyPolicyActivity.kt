package com.pulse.bluetoothdisable.privacy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pulse.bluetoothdisable.R
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.theme.ThemeManager
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class PrivacyPolicyActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only an enumerated language identifier is accepted, never a caller-supplied URL.
        val language = when (intent.getStringExtra(EXTRA_LANGUAGE)) {
            LanguageManager.RUSSIAN -> LanguageManager.RUSSIAN
            LanguageManager.ENGLISH -> LanguageManager.ENGLISH
            else -> LanguageManager.getSelectedLanguage(this)
        }
        setContent {
            val dark = when (ThemeManager.getSelectedTheme(this)) {
                ThemeManager.LIGHT -> false
                ThemeManager.DARK -> true
                else -> isSystemInDarkTheme()
            }
            val view = LocalView.current
            DisposableEffect(view, dark) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                onDispose { }
            }
            BluetoothDisableTheme(darkTheme = dark) {
                PrivacyPolicyScreen(language, dark, onClose = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_LANGUAGE = "privacy_language"
        fun intent(context: Context, language: String): Intent =
            Intent(context, PrivacyPolicyActivity::class.java).putExtra(
                EXTRA_LANGUAGE,
                if (language == LanguageManager.RUSSIAN) LanguageManager.RUSSIAN else LanguageManager.ENGLISH,
            )
    }
}

@Composable
internal fun PrivacyPolicyScreen(
    language: String,
    dark: Boolean,
    onClose: () -> Unit,
    loaderFactory: () -> PrivacyPageLoader = { PrivacyPageLoader() },
) {
    var documentUrl by remember(language) { mutableStateOf(PrivacyUrlPolicy.documentForLanguage(language)) }
    var attempt by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf<PrivacyPageLoader.Page?>(null) }
    var failed by remember { mutableStateOf(false) }
    var rendering by remember { mutableStateOf(true) }
    val active = remember { AtomicReference<PrivacyPageLoader?>() }
    DisposableEffect(Unit) {
        onDispose { runCatching { active.getAndSet(null)?.close() } }
    }
    LaunchedEffect(attempt, documentUrl, dark) {
        page = null
        failed = false
        rendering = true
        var loader: PrivacyPageLoader? = null
        try {
            val session = loaderFactory()
            loader = session
            active.set(session)
            page = withContext(Dispatchers.IO) { session.load(documentUrl, dark) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        } finally {
            loader?.let { session ->
                active.compareAndSet(session, null)
                runCatching { session.close() }
            }
        }
    }
    LaunchedEffect(page) {
        if (page != null) {
            delay(15_000)
            if (rendering) failed = true
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.privacy_close)) }
                Text(
                    stringResource(R.string.privacy_policy),
                    modifier = Modifier.weight(1f).padding(8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (failed) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.privacy_load_error))
                        Button(onClick = { attempt++ }) { Text(stringResource(R.string.privacy_retry)) }
                    }
                } else {
                    page?.let { snapshot ->
                        key(snapshot) {
                            PrivacyPageView(
                                snapshot,
                                onReady = { if (page === snapshot && !failed) rendering = false },
                                onFailure = { if (page === snapshot) failed = true },
                                onNavigate = { if (page === snapshot) documentUrl = it },
                            )
                        }
                    }
                    if (page == null || rendering) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.privacy_loading), Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyPageView(
    page: PrivacyPageLoader.Page,
    onReady: () -> Unit,
    onFailure: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { AtomicReference<android.webkit.WebView?>() }
    val background = MaterialTheme.colorScheme.surface.toArgb()
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.get()?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.get()?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).also { container ->
                try {
                    val webView = createPrivacyWebView(context)
                    holder.set(webView)
                    webView.setBackgroundColor(background)
                    container.addView(webView, FrameLayout.LayoutParams(-1, -1))
                    configurePrivacyWebView(
                        webView, page, onReady,
                        onFailure = {
                            // A crashed renderer is already destroyed in onRenderProcessGone.
                            if (webView.parent == null) holder.compareAndSet(webView, null)
                            onFailure()
                        },
                        onNavigate = onNavigate,
                    )
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) webView.onResume()
                } catch (_: RuntimeException) {
                    holder.getAndSet(null)?.let { runCatching { releasePrivacyWebView(it) } }
                    container.post { onFailure() }
                } catch (_: LinkageError) {
                    holder.getAndSet(null)?.let { runCatching { releasePrivacyWebView(it) } }
                    container.post { onFailure() }
                }
            }
        },
        onRelease = {
            holder.getAndSet(null)?.let { webView -> runCatching { releasePrivacyWebView(webView) } }
        },
    )
}
