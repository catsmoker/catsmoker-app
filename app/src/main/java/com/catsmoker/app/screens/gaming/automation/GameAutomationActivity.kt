package com.catsmoker.app.screens.gaming.automation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.catsmoker.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Visible execution surface, not a settings destination or background activity trampoline. */
@AndroidEntryPoint
class GameAutomationActivity : ComponentActivity() {
    @Inject lateinit var automation: GameAutomation
    private var started = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (automation.parse(intent) == null) { finish(); return }
        started = savedInstanceState?.getBoolean("started") ?: false
        setContent {
            val status by automation.status.collectAsStateWithLifecycle()
            MaterialTheme {
                Surface {
                    Column(Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.gt_automation))
                        Text(status)
                        Button(onClick = { finish() }) { Text(stringResource(R.string.gt_automation_close)) }
                    }
                }
            }
        }
        if (started) return
        lifecycleScope.launch {
            // onResume() can return before AndroidX dispatches RESUMED. A launch-only
            // request can finish inline, so start it only after the lifecycle is ready.
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            val request = automation.parse(intent) ?: run { finish(); return@launch }
            started = true
            if (request.operation == AutomationProtocol.Operation.QUERY_STATUS ||
                request.operation == AutomationProtocol.Operation.QUERY_SELECTION) {
                setResult(RESULT_OK, android.content.Intent().putExtra("status", automation.query()))
                finish()
                return@launch
            }
            val result = JSONObject(automation.execute(intent).await()).optString("result")
            val shouldLaunch = request.operation == AutomationProtocol.Operation.LAUNCH_GAME ||
                request.operation == AutomationProtocol.Operation.APPLY_AND_LAUNCH
            if (shouldLaunch && result in setOf("launch_ready", "applied", "partial", "already_applied")) {
                val pkg = request.game ?: return@launch
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                    automation.parse(intent) == null) {
                    automation.record("foreground_required")
                } else if (automation.gameAvailable(pkg)) {
                    try {
                        val launch = packageManager.getLaunchIntentForPackage(pkg)
                        if (launch == null) automation.record("game_unavailable") else {
                            startActivity(launch)
                            automation.record("launched")
                        }
                    } catch (_: Exception) { automation.record("launch_failed") }
                }
            }
            setResult(RESULT_OK, android.content.Intent().putExtra("status", automation.query()))
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("started", started)
        super.onSaveInstanceState(outState)
    }
}
