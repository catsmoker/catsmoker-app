package com.catsmoker.app.application.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.catsmoker.app.screens.about.AboutRoute
import com.catsmoker.app.screens.donate.DonateRoute
import com.catsmoker.app.screens.editgamefiles.EditGameFilesRoute
import com.catsmoker.app.screens.gaming.GamingToolsRoute
import com.catsmoker.app.screens.gaming.GamingToolsViewModel
import com.catsmoker.app.screens.gaming.crosshair.CrosshairLibraryRoute
import com.catsmoker.app.screens.home.HomeRoute
import com.catsmoker.app.screens.legal.LicensesRoute
import com.catsmoker.app.screens.legal.PrivacyPolicyRoute
import com.catsmoker.app.screens.legal.TermsRoute
import com.catsmoker.app.screens.logs.LogsRoute
import com.catsmoker.app.screens.permissions.PermissionRoute
import com.catsmoker.app.screens.settings.SettingsRoute

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Routes.MAIN) {
            HomeRoute(onNavigate = { navController.navigate(it) })
        }
        composable(Routes.PERMISSION) {
            PermissionRoute(
                onDone = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.PERMISSION) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.GAMING_TOOLS) {
            GamingToolsRoute(
                onBack = { navController.popBackStack() },
                onOpenCrosshairLibrary = { navController.navigate(Routes.CROSSHAIR_LIBRARY) }
            )
        }
        composable(Routes.CROSSHAIR_LIBRARY) {
            val parentEntry = remember(navController) {
                navController.getBackStackEntry(Routes.GAMING_TOOLS)
            }
            val gamingToolsViewModel: GamingToolsViewModel = hiltViewModel(parentEntry)
            val uiState by gamingToolsViewModel.uiState.collectAsStateWithLifecycle()
            CrosshairLibraryRoute(
                selectedAsset = uiState.selectedCrosshair,
                onBack = { navController.popBackStack() },
                onUse = { asset ->
                    gamingToolsViewModel.onSelectCrosshair(asset)
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.EDIT_GAME_FILES) {
            EditGameFilesRoute(onBack = { navController.popBackStack() })
        }
        // Variant destinations: full links the spoof graph, playstore links an empty
        // block with the same FQN — src/main never imports spoof classes directly.
        spoofGraph(navController)
        composable(Routes.ABOUT) {
            AboutRoute(
                onBack = { navController.popBackStack() },
                onDonate = { navController.navigate(Routes.DONATE) },
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
                onTerms = { navController.navigate(Routes.TERMS) },
                onOpenLicenses = { navController.navigate(Routes.LICENSES) }
            )
        }
        composable(Routes.LICENSES) {
            LicensesRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.PRIVACY) {
            PrivacyPolicyRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.TERMS) {
            TermsRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onOpenPermissions = { navController.navigate(Routes.PERMISSION) },
                onOpenLogs = { navController.navigate(Routes.LOGS) },
                onOpenDonate = { navController.navigate(Routes.DONATE) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }
        composable(Routes.LOGS) {
            LogsRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.DONATE) {
            DonateRoute(onBack = { navController.popBackStack() })
        }
    }
}
