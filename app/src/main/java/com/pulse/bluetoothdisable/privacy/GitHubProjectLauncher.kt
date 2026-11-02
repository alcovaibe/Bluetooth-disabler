package com.pulse.bluetoothdisable.privacy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.pulse.bluetoothdisable.R

internal object GitHubProjectLauncher {
    private const val PROJECT_URL = "https://github.com/alcovaibe/Bluetooth-disabler"

    fun open(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showUnavailable(context)
        } catch (_: SecurityException) {
            showUnavailable(context)
        }
    }

    private fun showUnavailable(context: Context) {
        Toast.makeText(context, R.string.project_github_unavailable, Toast.LENGTH_LONG).show()
    }
}
