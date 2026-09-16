package com.catsmoker.app.system

import android.os.Bundle
import android.view.Window
import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.catsmoker.app.shared.ui.theme.CatsmokerTheme
import com.catsmoker.app.system.config.AppearanceStore
import com.catsmoker.app.system.config.LocaleHelper
import com.catsmoker.app.system.navigation.AppNavHost
import com.catsmoker.app.system.navigation.Routes
import com.catsmoker.app.system.ui.StartupScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.catsmoker.app.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.res.stringResource

/**
 * Support-dialog prompt state, persisted in `app_prefs` next to `app_launch_count`.
 *
 * The dialog shows every 5th launch; from its second showing it offers a "Do not show
 * again" checkbox. Pure data + pure policy below so the rules stay unit-tested — the
 * Activity only reads/writes the prefs.
 */
internal data class SupportPromptState(
    val timesShown: Int = 0,
    val neverAskAgain: Boolean = false
)

internal fun shouldShowSupportDialog(launchCount: Int, state: SupportPromptState): Boolean =
    !state.neverAskAgain && launchCount > 0 && launchCount % 5 == 0

internal fun showNeverAskOption(state: SupportPromptState): Boolean =
    state.timesShown >= 1

internal fun onSupportDialogShown(state: SupportPromptState): SupportPromptState =
    state.copy(timesShown = state.timesShown + 1)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Request no title bar before super.onCreate
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        super.onCreate(savedInstanceState)
        
        incrementLaunchCount()
        
        // Fix for icon and label in recent apps overview
        updateTaskDescription()
        
        val startDestination = if (isFirstRun()) Routes.PERMISSION else Routes.MAIN
        
        setContent {
            AppearanceStore.init(applicationContext)
            val themeMode by AppearanceStore.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                AppearanceStore.ThemeMode.DARK -> true
                AppearanceStore.ThemeMode.LIGHT -> false
                // System default and Dynamic — the phone's own dark/light setting wins.
                // Dynamic differs only in palette (wallpaper colors, API 31+), not in
                // which side of dark/light is picked.
                AppearanceStore.ThemeMode.SYSTEM,
                AppearanceStore.ThemeMode.DYNAMIC -> isSystemInDarkTheme()
            }
            CatsmokerTheme(
                darkTheme = darkTheme,
                dynamicColor = themeMode == AppearanceStore.ThemeMode.DYNAMIC
            ) {
                var showStartup by remember { mutableStateOf(true) }
                var showSupportDialog by remember { mutableStateOf(shouldShowSupportDialog()) }
                // Prompt state read once: it decides whether this showing offers the
                // "Do not show again" checkbox (2nd showing on). The shown-count itself
                // is recorded by the LaunchedEffect below, once per actual display.
                val supportState = remember { supportPromptState() }
                var neverAskChecked by remember { mutableStateOf(false) }
                val closeSupportDialog: () -> Unit = {
                    if (neverAskChecked) setSupportNeverAsk()
                    showSupportDialog = false
                }
                // Hoisted above the dialog so Donate can navigate: the dialog lives outside
                // the NavHost, and creating a second controller here would split the back stack.
                val navController = rememberNavController()

                if (!showStartup && showSupportDialog) {
                    LaunchedEffect(Unit) { markSupportDialogShown() }
                    val githubUrl = stringResource(R.string.url_github)
                    AlertDialog(
                        onDismissRequest = { closeSupportDialog() },
                        title = { Text(stringResource(R.string.sys_support_title), color = MaterialTheme.colorScheme.onSurface) },
                        text = {
                            Column {
                                Text(
                                    stringResource(R.string.sys_support_text),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (showNeverAskOption(supportState)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Checkbox(
                                            checked = neverAskChecked,
                                            onCheckedChange = { neverAskChecked = it }
                                        )
                                        Text(
                                            stringResource(R.string.sys_support_never),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Row {
                                TextButton(onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, githubUrl.toUri())
                                        startActivity(intent)
                                    } catch (_: Exception) {}
                                    closeSupportDialog()
                                }) { Text(stringResource(R.string.sys_support_star), color = MaterialTheme.colorScheme.onSurface) }
                                TextButton(onClick = {
                                    closeSupportDialog()
                                    navController.navigate(Routes.DONATE)
                                }) { Text(stringResource(R.string.sys_support_donate), color = MaterialTheme.colorScheme.onSurface) }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { closeSupportDialog() }) {
                                Text(stringResource(R.string.sys_later), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LaunchedEffect(showStartup) {
                    if (!showStartup) {
                        // Refresh TaskDescription when UI is ready
                        updateTaskDescription()
                        delay(500.milliseconds)
                        (application as? CatsmokerApp)?.initDeferredTasks()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showStartup) {
                        StartupScreen(onFinished = { showStartup = false })
                    } else {
                        AppNavHost(navController = navController, startDestination = startDestination)
                    }
                }
            }
        }
    }

    private fun updateTaskDescription() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                val taskDesc = ActivityManager.TaskDescription(getString(R.string.app_name), R.mipmap.ic_launcher)
                setTaskDescription(taskDesc)
            }
        } catch (_: Exception) {}
    }

    private fun isFirstRun(): Boolean {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return prefs.getBoolean("is_first_run", true)
    }

    private fun incrementLaunchCount() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val count = prefs.getInt("app_launch_count", 0) + 1
        prefs.edit { putInt("app_launch_count", count) }
    }

    private fun shouldShowSupportDialog(): Boolean {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val count = prefs.getInt("app_launch_count", 0)
        return shouldShowSupportDialog(count, supportPromptState(prefs))
    }

    private fun supportPromptState(
        prefs: SharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
    ): SupportPromptState = SupportPromptState(
        timesShown = prefs.getInt("support_shown_count", 0),
        neverAskAgain = prefs.getBoolean("support_never_ask", false)
    )

    private fun markSupportDialogShown() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val next = onSupportDialogShown(supportPromptState(prefs))
        prefs.edit { putInt("support_shown_count", next.timesShown) }
    }

    private fun setSupportNeverAsk() {
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit { putBoolean("support_never_ask", true) }
    }
}
