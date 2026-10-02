package org.companerodeescuela.core.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private val TABLET_NAVIGATION_BREAKPOINT = 600.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompaneroScaffold(
    navController: NavHostController = rememberNavController(),
    startDestination: Destination = Destination.Home,
    destinations: NavGraphBuilder.() -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onProfile = currentDestination?.route == Destination.Profile.route

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= TABLET_NAVIGATION_BREAKPOINT

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (onProfile) "Perfil" else "Compañero")
                    },
                    navigationIcon = {
                        if (onProfile) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Volver",
                                )
                            }
                        }
                    },
                    actions = {
                        if (!onProfile) {
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
                if (!onProfile && !useRail) {
                    CompaneroBottomBar(
                        navController = navController,
                        currentDestination = currentDestination,
                    )
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (!onProfile && useRail) {
                    CompaneroNavigationRail(
                        navController = navController,
                        currentDestination = currentDestination,
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination.route,
                    modifier = Modifier.weight(1f),
                ) {
                    destinations()
                }
            }
        }
    }
}
