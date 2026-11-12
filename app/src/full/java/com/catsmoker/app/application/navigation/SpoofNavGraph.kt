package com.catsmoker.app.application.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.catsmoker.app.screens.spoofing.appassignment.AppAssignmentScreen
import com.catsmoker.app.screens.spoofing.diagnostics.DiagnosticsScreen
import com.catsmoker.app.screens.spoofing.hub.SpoofRoute
import com.catsmoker.app.screens.spoofing.profileeditor.ProfileEditorScreen
import com.catsmoker.app.screens.spoofing.profiles.ProfilesListScreen
import com.catsmoker.app.screens.spoofing.safemode.SafeModeScreen
import com.catsmoker.app.screens.spoofing.state.SpoofDeviceViewModel

/**
 * Full-variant spoof destinations.
 *
 * Same FQN/extension as the playstore variant's no-op: the shared [AppNavHost]
 * calls [spoofGraph] unconditionally and each variant links its own body, so
 * `src/main` never imports spoof classes directly (they are absent from the
 * Play Store compile). The spoof screens share one ViewModel via
 * `hiltViewModel(parentEntry)` — preserved from the pre-flavor AppNavHost.
 */
fun NavGraphBuilder.spoofGraph(navController: NavHostController) {
    composable(SpoofRoutes.SPOOF_DEVICE) {
        SpoofRoute(
            onNavigate = { navController.navigate(it) },
            onBack = { navController.popBackStack() }
        )
    }
    composable(SpoofRoutes.SPOOF_PROFILES) { backStackEntry ->
        val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(SpoofRoutes.SPOOF_DEVICE) }
        val viewModel: SpoofDeviceViewModel = hiltViewModel(parentEntry)
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        ProfilesListScreen(
            uiState = uiState,
            presets = viewModel.presetsForPicker(),
            onNavigateToEditor = { navController.navigate(SpoofRoutes.SPOOF_EDITOR.replace("{profileId}", it)) },
            onCreateProfile = { name, preset -> viewModel.createProfile(name, preset) },
            onDeleteProfile = { viewModel.deleteProfile(it) },
            onShareProfile = { viewModel.shareProfile(it) },
            onConfirmImportProfile = { preview, chosenName ->
                viewModel.confirmImport(preview, chosenName)
            },
            onConfirmImportPreset = { preview, chosenName ->
                viewModel.confirmImportAsPreset(preview, chosenName)
            },
            onDeletePreset = { viewModel.deleteUserPreset(it) },
            onBack = { navController.popBackStack() }
        )
    }
    composable(SpoofRoutes.SPOOF_EDITOR) { backStackEntry ->
        val profileId = backStackEntry.arguments?.getString("profileId") ?: return@composable
        val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(SpoofRoutes.SPOOF_DEVICE) }
        val viewModel: SpoofDeviceViewModel = hiltViewModel(parentEntry)
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        ProfileEditorScreen(
            profileId = profileId,
            uiState = uiState,
            presets = viewModel.presetsForPicker(),
            onSave = { id, name, profile ->
                viewModel.updateProfile(id, name, profile)
                navController.popBackStack()
            },
            onSavePreset = { name, profile -> viewModel.createUserPreset(name, profile) },
            onDeletePreset = { viewModel.deleteUserPreset(it) },
            onBack = { navController.popBackStack() }
        )
    }
    composable(SpoofRoutes.SPOOF_APPS) {
        val parentEntry = remember(it) { navController.getBackStackEntry(SpoofRoutes.SPOOF_DEVICE) }
        val viewModel: SpoofDeviceViewModel = hiltViewModel(parentEntry)
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        AppAssignmentScreen(
            uiState = uiState,
            onLoadApps = { viewModel.loadApps() },
            onAssignProfile = { pkg, id -> viewModel.assignProfile(pkg, id) },
            onForceStop = { pkg -> viewModel.forceStopApp(pkg) },
            onBack = { navController.popBackStack() }
        )
    }
    composable(SpoofRoutes.SPOOF_SAFE_MODE) {
        val parentEntry = remember(it) { navController.getBackStackEntry(SpoofRoutes.SPOOF_DEVICE) }
        val viewModel: SpoofDeviceViewModel = hiltViewModel(parentEntry)
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        SafeModeScreen(
            uiState = uiState,
            onLoadApps = { viewModel.loadApps() },
            onToggleSafeMode = { pkg, enabled -> viewModel.toggleSafeMode(pkg, enabled) },
            onToggleVersionBypass = { pkg, enabled -> viewModel.toggleVersionBypass(pkg, enabled) },
            onBack = { navController.popBackStack() }
        )
    }
    composable(SpoofRoutes.SPOOF_DIAGNOSTICS) {
        DiagnosticsScreen(onBack = { navController.popBackStack() })
    }
}
