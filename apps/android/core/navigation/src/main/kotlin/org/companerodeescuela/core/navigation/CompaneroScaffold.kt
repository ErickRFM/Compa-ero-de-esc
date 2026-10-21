@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled
import org.companerodeescuela.core.designsystem.v8.LocalV8HeaderAction
import org.companerodeescuela.core.designsystem.v8.V8CampusBackdrop
import org.companerodeescuela.core.designsystem.v8.v8ResolvedColorScheme

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
    val onSecondaryScreen =
        isSecondaryRoute(
            currentRoute = currentDestination?.route,
            startRoute = startDestination.route,
            topLevelRoutes = topLevelDestinations.map { it.destination.route },
        )
    val secondaryTitle =
        when (currentDestination?.route) {
            Destination.Profile.route -> "Perfil"
            Destination.AppearanceSettings.route -> "Tema y accesibilidad"
            Destination.IntegrationSettings.route -> "Integración institucional"
            Destination.DesignSystemCatalog.route -> "Sistema visual"
            Destination.Grading.route -> "Calificaciones"
            Destination.Channel.route -> "Canal"
            Destination.Classrooms.route -> "Clases"
            Destination.Schedule.route -> "Horario"
            Destination.Attendance.route -> "Asistencia"
            Destination.RepresentativeInvitations.route -> "Representantes"
            else -> "Volver"
        }

    val parentColors = MaterialTheme.colorScheme
    val lightTheme = parentColors.background.luminance() > 0.5f
    // Every role uses the same V9 palette and matte surfaces. Only the
    // student/teacher home screens keep the campus photograph as a backdrop.
    val v9Colors = v8ResolvedColorScheme(parentColors, highContrast)
    val showCampusBackdrop = startDestination == Destination.Home || startDestination == Destination.TeacherHome
    val headerAction: (@Composable () -> Unit)? =
        if (!onSecondaryScreen && topLevelDestinations.none { it.destination == Destination.Profile }) {
            {
                IconButton(onClick = {
                    navController.navigate(Destination.Profile.route) { launchSingleTop = true }
                }) {
                    Icon(Icons.Filled.Person, contentDescription = "Abrir perfil")
                }
            }
        } else {
            null
        }
    MaterialTheme(colorScheme = v9Colors) {
        CompositionLocalProvider(
            LocalV8GlassEnabled provides true,
            LocalV8HeaderAction provides headerAction,
            LocalContentColor provides v9Colors.onSurface,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (lightTheme || highContrast || !showCampusBackdrop) {
                    Box(Modifier.matchParentSize().background(v9Colors.background))
                } else {
                    V8CampusBackdrop(Modifier.matchParentSize())
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        if (onSecondaryScreen) {
                            TopAppBar(
                                colors =
                                    TopAppBarDefaults.topAppBarColors(
                                        containerColor = Color.Transparent,
                                        scrolledContainerColor = Color.Transparent,
                                    ),
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
                        modifier =
                            Modifier
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
                    }
                }
            }
        }
    }
}
