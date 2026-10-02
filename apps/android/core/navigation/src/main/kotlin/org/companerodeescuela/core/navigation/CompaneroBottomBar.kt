package org.companerodeescuela.core.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person

/**
 * Top-level navigation bar.
 *
 * Uses `saveState`/`restoreState`/`popUpTo(startDestination)` so switching
 * tabs does not grow the back stack without bound, which is the usual cause
 * of a bottom bar that feels broken after a few taps.
 */
@Composable
fun CompaneroBottomBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        TopLevelDestination.entries.forEach { topLevel ->
            val selected = currentDestination
                ?.hierarchy
                ?.any { it.route == topLevel.destination.route } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(topLevel.destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = when (topLevel) {
                            TopLevelDestination.Home -> Icons.Filled.Home
                            TopLevelDestination.Profile -> Icons.Filled.Person
                        },
                        contentDescription = topLevel.label,
                    )
                },
                label = { Text(topLevel.label) },
            )
        }
    }
}
