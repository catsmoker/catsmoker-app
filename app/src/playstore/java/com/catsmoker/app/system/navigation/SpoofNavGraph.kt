package com.catsmoker.app.system.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

/**
 * Play Store variant: spoof destinations are absent.
 *
 * Same FQN/extension as the full variant's [spoofGraph] so the shared
 * [AppNavHost] links without referencing spoof classes (which are not
 * compiled into this variant at all).
 */
fun NavGraphBuilder.spoofGraph(navController: NavHostController) {
    // No spoof destinations on Play. Intentionally empty.
}
