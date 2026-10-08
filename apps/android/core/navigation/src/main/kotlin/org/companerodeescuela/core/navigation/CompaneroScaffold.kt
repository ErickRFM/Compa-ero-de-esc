package org.companerodeescuela.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import org.companerodeescuela.core.designsystem.v8.V8ColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    highContrast: Boolean = false,
    destinations: NavGraphBuilder.() -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onSecondaryScreen = isSecondaryRoute(
        currentRoute = currentDestination?.route,
        startRoute = startDestination.route,
        topLevelRoutes = topLevelDestinations.map { it.destination.route },
    )
    val secondaryTitle = when (currentDestination?.route) {
        Destination.Profile.route -> "Perfil"
        Destination.AppearanceSettings.route -> "Tema y accesibilidad"
        Destination.IntegrationSettings.route -> "Integración institucional"
        Destination.DesignSystemCatalog.route -> "Sistema visual"
        Destination.Grading.route -> "Calificaciones"
        Destination.Channel.route -> "Canal"
        Destination.Classrooms.route -> "Clases"
        Destination.Schedule.route -> "Horario"
        Destination.Attendance.route -> "Asistencia"
        else -> "Volver"
    }

    val studentColors = if (highContrast) V8ColorScheme.copy(
        outline = V8ColorScheme.onSurfaceVariant,
        outlineVariant = V8ColorScheme.onSurface.copy(alpha = 0.65f),
    ) else V8ColorScheme
    MaterialTheme(colorScheme = if (startDestination == Destination.Home) studentColors else MaterialTheme.colorScheme) {
    Scaffold(
        topBar = {
            if (onSecondaryScreen) {
                TopAppBar(
                    title = { Text(secondaryTitle.orEmpty()) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!onSecondaryScreen && topLevelDestinations.isNotEmpty()) {
                CompaneroBottomBar(
                    navController = navController,
                    currentDestination = currentDestination,
                    destinations = topLevelDestinations,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                destinations()
            }

            if (!onSecondaryScreen && topLevelDestinations.none { it.destination == Destination.Profile }) {
                IconButton(
                    onClick = {
                        navController.navigate(Destination.Profile.route) {
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Abrir perfil",
                    )
                }
            }
        }
    }
    }
}
