package com.pulse.bluetoothdisable.privacy

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GitHubHelpLauncherInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun availableGitHubReceivesTheSelectedLanguageWithoutLaunchingAnotherViewer() {
        for (language in listOf("ru", "en")) {
            val intents = mutableListOf<Intent>()
            GitHubHelpLauncher.open(context, language) { intents += it }
            assertEquals(1, intents.size)
            assertEquals(Intent.ACTION_VIEW, intents.single().action)
            assertEquals("com.github.android", intents.single().`package`)
            assertEquals(GitHubHelpUrlPolicy.requireDocument(GitHubHelpUrlPolicy.documentForLanguage(language)).toASCIIString(), intents.single().dataString)
        }
    }

    @Test fun missingOrRestrictedGitHubOpensOnlyTheNonExportedInternalViewer() {
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            val intents = mutableListOf<Intent>()
            GitHubHelpLauncher.open(context, "ru") {
                intents += it
                if (intents.size == 1) throw failure
            }
            assertEquals(2, intents.size)
            assertEquals(ComponentName(context, GitHubHelpActivity::class.java), intents.last().component)
            assertNull(intents.last().data)
        }
        @Suppress("DEPRECATION")
        val info = context.packageManager.getActivityInfo(ComponentName(context, GitHubHelpActivity::class.java), 0)
        assertFalse(info.exported)
    }
}
