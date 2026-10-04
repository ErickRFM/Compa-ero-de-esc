package org.companerodeescuela.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompaneroScaffold(
    navController: NavHostController = rememberNavController(),
    startDestination: Destination = Destination.Home,
    topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.entries,
    destinations: NavGraphBuilder.() -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val secondaryTitle = when (currentDestination?.route) {
        Destination.Profile.route -> "Perfil"
        Destination.AppearanceSettings.route -> "Tema y accesibilidad"
        Destination.DesignSystemCatalog.route -> "Sistema visual"
        else -> null
    }
    val onSecondaryScreen = secondaryTitle != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    secondaryTitle?.let { Text(it) }
                },
                navigationIcon = {
                    if (onSecondaryScreen) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                            )
                        }
                    }
                },
                actions = {
                    if (!onSecondaryScreen) {
                        IconButton(
                            onClick = {
                                navController.navigate(Destination.Profile.route) {
                                    launchSingleTop = true
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Abrir perfil",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!onSecondaryScreen) {
                CompaneroBottomBar(
                    navController = navController,
                    currentDestination = currentDestination,
                    destinations = topLevelDestinations,
                )
            }
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
