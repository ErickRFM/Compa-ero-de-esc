package org.companerodeescuela.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

@Composable
fun CompaneroNavigationRail(
    navController: NavHostController,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
) {
    NavigationRail(modifier = modifier) {
        TopLevelDestination.entries.forEach { topLevel ->
            val selected = currentDestination
                ?.hierarchy
                ?.any { it.route == topLevel.destination.route } == true

            NavigationRailItem(
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
                            TopLevelDestination.Schedule -> Icons.Filled.DateRange
                        },
                        contentDescription = topLevel.label,
                    )
                },
                label = { Text(topLevel.label) },
            )
        }
    }
}
