package com.catsmoker.app.system

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import com.catsmoker.app.features.spoofdevice.SpoofProfileImportInbox

/**
 * Full-variant incoming device-profile share handler.
 *
 * Same FQN as the playstore no-op: [MainActivity] delegates ACTION_SEND /
 * ACTION_VIEW intents here without referencing spoof classes directly.
 *
 * Guarded by the `catsmoker-device-profile` marker so the broad SEND filter
 * never hijacks unrelated shares — anything else is ignored silently. Content
 * is capped well below the binder limit; the inbox holds text, never a Uri,
 * so a dead granting process cannot break the later import.
 */
object ProfileShareHandler {
    /** Incoming share cap: far below binder limits, far above any real profile. */
    const val MAX_PROFILE_SHARE_CHARS = 200_000

    /**
     * Returns true when the intent carried a device profile for the inbox.
     */
    fun handleIntent(activity: ComponentActivity, intent: Intent?): Boolean {
        try {
            val text = when (intent?.action) {
                Intent.ACTION_SEND -> {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
                        // Bundle.get has no typed-parcelable deprecation on any API level;
                        // the checked cast restores the type safety getParcelableExtra gave.
                        ?: (intent.extras?.get(Intent.EXTRA_STREAM) as? Uri)
                            ?.let { readProfileUri(activity, it) }
                }
                Intent.ACTION_VIEW -> intent.data?.let { readProfileUri(activity, it) }
                else -> null
            }
            if (!text.isNullOrBlank() &&
                text.length < MAX_PROFILE_SHARE_CHARS &&
                text.contains("catsmoker-device-profile")
            ) {
                SpoofProfileImportInbox.offer(text)
                return true
            }
        } catch (_: Exception) {
        }
        return false
    }

    private fun readProfileUri(activity: ComponentActivity, uri: Uri): String? = runCatching {
        activity.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = ByteArray(MAX_PROFILE_SHARE_CHARS)
            val read = stream.read(bytes)
            if (read <= 0) null else bytes.copyOf(read).toString(Charsets.UTF_8)
        }
    }.getOrNull()
}
