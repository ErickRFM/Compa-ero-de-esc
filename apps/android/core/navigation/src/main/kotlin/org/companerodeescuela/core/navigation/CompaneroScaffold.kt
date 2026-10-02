package org.companerodeescuela.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * App shell: bottom bar plus a nav host that the app module fills in.
 *
 * Destinations are registered by the caller rather than listed here, because
 * this module must not depend on any feature. That keeps the dependency
 * pointing one way (features know about core, never the reverse) and lets a
 * feature ship its own graph without touching this file.
 */
@Composable
fun CompaneroScaffold(
    navController: NavHostController = rememberNavController(),
    startDestination: Destination = Destination.Home,
    destinations: NavGraphBuilder.() -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            CompaneroBottomBar(
                navController = navController,
                currentDestination = currentDestination,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            destinations()
        }
    }
}
