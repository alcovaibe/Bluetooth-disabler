package com.pulse.bluetoothdisable.privacy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

internal object GitHubHelpLauncher {
    // Explicit package routing does not need QUERY_ALL_PACKAGES or package visibility queries.
    fun open(context: Context, language: String, launch: (Intent) -> Unit = { context.startActivity(it) }) {
        val url = GitHubHelpUrlPolicy.requireDocument(GitHubHelpUrlPolicy.documentForLanguage(language))
        val github = Intent(Intent.ACTION_VIEW, Uri.parse(url.toASCIIString())).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage("com.github.android")
        }
        try {
            launch(github)
            return
        } catch (_: ActivityNotFoundException) {
            // No installed/enabled GitHub handler: stay inside the application.
        } catch (_: SecurityException) {
            // An unavailable or restricted handler must not force a browser fallback.
        }
        launch(GitHubHelpActivity.intent(context, language))
    }
}
