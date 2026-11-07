@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.core.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroAccentPalette
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroDynamicColor
import org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled
import org.companerodeescuela.core.designsystem.v8.LocalV8HeaderAction
import org.companerodeescuela.core.designsystem.v8.V8CampusBackdrop
import org.companerodeescuela.core.designsystem.v8.v8ResolvedColorScheme
import org.companerodeescuela.core.motion.CompaneroMotionDuration
import org.companerodeescuela.core.motion.LocalCompaneroMotionPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompaneroScaffold(
    navController: NavHostController = rememberNavController(),
    startDestination: Destination = Destination.Home,
    topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.entries,
    highContrast: Boolean = false,
    campusBackdropEnabled: Boolean = true,
    destinations: NavGraphBuilder.() -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val reducedMotion = LocalCompaneroMotionPreferences.current.reducedMotion
    val enterOffset = with(LocalDensity.current) { 6.dp.roundToPx() }
    val onSecondaryScreen =
        isSecondaryRoute(
            currentRoute = currentDestination?.route,
            startRoute = startDestination.route,
            topLevelRoutes = topLevelDestinations.map { it.destination.route },
        )
    val secondaryTitle = secondaryTitleForRoute(currentDestination?.route)

    val parentColors = MaterialTheme.colorScheme
    val lightTheme = parentColors.background.luminance() > 0.5f
    val accentPalette = LocalCompaneroAccentPalette.current
    val dynamicColor = LocalCompaneroDynamicColor.current
    // Every role uses the same V9 palette and matte surfaces. Only the
    // student/teacher home screens keep the campus photograph as a backdrop.
    val v9Colors =
        v8ResolvedColorScheme(
            parent = parentColors,
            highContrast = highContrast,
            accentPalette = accentPalette,
            preserveDynamicPrimary = dynamicColor,
        )
    val showCampusBackdrop =
        campusBackdropEnabled && (startDestination == Destination.Home || startDestination == Destination.TeacherHome)
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
                if (lightTheme || highContrast || !showCampusBackdrop || onSecondaryScreen) {
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
                                title = { Text(secondaryTitle.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CompaneroBottomBar(
                                    navController = navController,
                                    currentDestination = currentDestination,
                                    destinations = topLevelDestinations,
                                    modifier = Modifier.widthIn(max = 640.dp),
                                )
                            }
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
                            enterTransition = {
                                if (reducedMotion) {
                                    EnterTransition.None
                                } else {
                                    fadeIn(tween(CompaneroMotionDuration.STANDARD)) +
                                        slideInVertically(tween(CompaneroMotionDuration.STANDARD)) { enterOffset }
                                }
                            },
                            exitTransition = { if (reducedMotion) ExitTransition.None else fadeOut(tween(CompaneroMotionDuration.FAST)) },
                            popEnterTransition = {
                                if (reducedMotion) {
                                    EnterTransition.None
                                } else {
                                    fadeIn(
                                        tween(CompaneroMotionDuration.STANDARD),
                                    )
                                }
                            },
                            popExitTransition = {
                                if (reducedMotion) {
                                    ExitTransition.None
                                } else {
                                    fadeOut(
                                        tween(CompaneroMotionDuration.FAST),
                                    )
                                }
                            },
                        ) {
                            destinations()
                        }
                    }
                }
            }
        }
    }
}

internal fun secondaryTitleForRoute(route: String?): String =
    when (route) {
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
        Destination.Mfa.route -> "Seguridad y MFA"
        Destination.StaffApplication.route -> "Solicitud de tutoría"
        Destination.TutorRequests.route -> "Solicitudes de tutoría"
        Destination.TutorGroups.route -> "Grupos tutorados"
        Destination.InvitationAcceptance.route -> "Aceptar invitación"
        Destination.InvitationIssue.route -> "Enviar invitación"
        Destination.RoleUnavailable.route -> "Acceso no disponible"
        Destination.CoordinatorHome.route -> "Supervisión institucional"
        Destination.AdminHome.route -> "Administración"
        Destination.Home.route -> "Inicio"
        Destination.TeacherHome.route -> "Inicio docente"
        Destination.TutorHome.route -> "Tutoría"
        else -> "Volver"
    }
