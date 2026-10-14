package com.pulse.bluetoothdisable.privacy

import android.content.Context
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pulse.bluetoothdisable.localization.LanguageManager
import com.pulse.bluetoothdisable.ui.theme.BluetoothDisableTheme
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** No Internet dependency: exercises the real renderer with a controlled native transport. */
@RunWith(AndroidJUnit4::class)
class PrivacyViewerInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var previousLanguage = LanguageManager.ENGLISH

    @Before fun before() {
        previousLanguage = LanguageManager.getSelectedLanguage(context)
        LanguageManager.setSelectedLanguage(context, LanguageManager.ENGLISH)
    }
    @After fun after() { LanguageManager.setSelectedLanguage(context, previousLanguage) }

    @Test fun rendererReadsBothLanguagesAndThemesWithNetworkingAndActiveFeaturesDisabled() {
        // Switching the page inputs recreates a sealed snapshot without needing a network server.
        val case = androidx.compose.runtime.mutableStateOf(0)
        val cases = listOf("en" to false, "en" to true, "ru" to false, "ru" to true)
        val requests = AtomicInteger()
        compose.setContent {
            val (language, dark) = cases[case.value]
            BluetoothDisableTheme(darkTheme = dark) {
                PrivacyPolicyScreen(language, dark, {}, loaderFactory = {
                    PrivacyPageLoader(TestTransport(requests))
                })
            }
        }
        cases.indices.forEach { index ->
            compose.runOnIdle { case.value = index }
            compose.waitUntil(15_000) {
                requests.get() >= index + 1 &&
                    compose.onAllNodes(androidx.compose.ui.test.hasText("Loading privacy policy…"))
                        .fetchSemanticsNodes().isEmpty()
            }
            onView(isAssignableFrom(WebView::class.java)).check(matches(isDisplayed())).check { view, error ->
                if (error != null) throw error
                val web = view as WebView
                assertTrue(web.settings.blockNetworkLoads)
                assertFalse(web.settings.javaScriptEnabled)
                assertFalse(web.settings.domStorageEnabled)
                assertFalse(web.settings.allowFileAccess)
                assertFalse(web.settings.allowContentAccess)
            }
        }
        assertEquals(4, requests.get())
    }

    @Test fun failureCanBeRetriedAndCloseRemainsAvailable() {
        val attempts = AtomicInteger()
        val closed = AtomicBoolean()
        compose.setContent {
            BluetoothDisableTheme() {
                PrivacyPolicyScreen("en", false, { closed.set(true) }, loaderFactory = {
                    val fail = attempts.incrementAndGet() == 1
                    PrivacyPageLoader(TestTransport(AtomicInteger(), fail))
                })
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Retry")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
        compose.waitUntil(15_000) {
            attempts.get() == 2 &&
                compose.onAllNodes(androidx.compose.ui.test.hasText("Loading privacy policy…"))
                    .fetchSemanticsNodes().isEmpty()
        }
        onView(isAssignableFrom(WebView::class.java)).check(matches(isDisplayed()))
        compose.onNodeWithText("Close").performClick()
        assertTrue(closed.get())
    }

    private class TestTransport(private val requests: AtomicInteger, private val fail: Boolean = false) : PrivacyTransport {
        override fun get(uri: URI): PrivacyTransport.Reply {
            requests.incrementAndGet()
            if (fail) throw IOException("Offline fixture")
            val body = "<main><article class=card><h1>Privacy policy</h1>" +
                "<p>Readable policy text</p></article></main>"
            return object : PrivacyTransport.Reply {
                override fun code() = 200
                override fun header(name: String) = if (name == "Content-Type") "text/html; charset=utf-8" else null
                override fun body() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
                override fun close() = Unit
            }
        }
        override fun close() = Unit
    }
}
