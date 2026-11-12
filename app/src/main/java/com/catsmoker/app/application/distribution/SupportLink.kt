package com.catsmoker.app.application.distribution

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.catsmoker.app.R

internal data class SupportLink(
    val url: String,
    val preferredPackage: String? = null,
) {
    fun open(context: Context): Boolean {
        val opened = launch { targetUrl, targetPackage ->
            val intent = Intent(Intent.ACTION_VIEW, targetUrl.toUri()).apply {
                targetPackage?.let { setPackage(it) }
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
        if (!opened) {
            Toast.makeText(context, R.string.sys_support_link_failed, Toast.LENGTH_LONG).show()
        }
        return opened
    }

    // A successful launch says nothing about whether a star or rating was submitted.
    internal fun launch(launcher: (String, String?) -> Boolean): Boolean {
        if (launcher(url, preferredPackage)) return true
        return preferredPackage != null && launcher(url, null)
    }
}
