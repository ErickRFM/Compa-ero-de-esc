package org.companerodeescuela

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.companerodeescuela.core.attendance.AttendanceSyncScheduler
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.motion.ProvideCompaneroMotionPreferences
import org.companerodeescuela.core.navigation.AppExperience
import org.companerodeescuela.core.navigation.CompaneroScaffold
import org.companerodeescuela.core.navigation.Destination
import org.companerodeescuela.core.navigation.RoleExperienceResolver
import org.companerodeescuela.feature.admin.AdminHomeScreen
import org.companerodeescuela.feature.attendance.AttendanceMode
import org.companerodeescuela.feature.attendance.AttendanceScreen
import org.companerodeescuela.feature.attendance.TeacherHomeScreen
import org.companerodeescuela.feature.auth.LoginScreen
import org.companerodeescuela.feature.auth.RegistrationScreen
import org.companerodeescuela.feature.auth.SessionViewModel
import org.companerodeescuela.feature.channel.ChannelScreen
import org.companerodeescuela.feature.classroom.ClassroomScreen
import org.companerodeescuela.feature.coordinator.CoordinatorHomeScreen
import org.companerodeescuela.feature.designsystem.DesignSystemCatalogScreen
import org.companerodeescuela.feature.home.HomeScreen
import org.companerodeescuela.feature.grading.GradebookScreen
import org.companerodeescuela.feature.profile.ActiveExperiencePreferences
import org.companerodeescuela.feature.profile.ProfileScreen
import org.companerodeescuela.feature.schedule.ScheduleScreen
import org.companerodeescuela.feature.tutoring.TutorHomeScreen
import org.companerodeescuela.feature.tutoring.TutorRequestsScreen
import org.companerodeescuela.feature.settings.AppThemeMode
import org.companerodeescuela.feature.settings.AppearancePreferences
import org.companerodeescuela.feature.settings.AppearanceSettingsScreen
import org.companerodeescuela.feature.settings.IntegrationSettingsScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var attendanceSyncScheduler: AttendanceSyncScheduler

    @Inject
    lateinit var appearancePreferences: AppearancePreferences

    @Inject
    lateinit var activeExperiencePreferences: ActiveExperiencePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val appearance by appearancePreferences.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appearance.themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            CompaneroTheme(
                darkTheme = darkTheme,
                fontScaleMultiplier = appearance.textScale,
                highContrast = appearance.highContrast,
            ) {
                ProvideCompaneroMotionPreferences(
                    reducedMotion = appearance.reducedMotion,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        val sessionViewModel: SessionViewModel = hiltViewModel()
                        val session by sessionViewModel.state.collectAsStateWithLifecycle()
                        var activatingAccess by remember { mutableStateOf(false) }

                        LaunchedEffect(session.authenticated) {
                            if (session.authenticated) {
                                activatingAccess = false
                                attendanceSyncScheduler.schedule()
                            }
                        }

                        when {
                            session.checking -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            !session.authenticated -> {
                                if (activatingAccess) {
                                    RegistrationScreen(
                                        state = session,
                                        onRegister = sessionViewModel::register,
                                        onBackToLogin = { activatingAccess = false },
                                    )
                                } else {
                                    LoginScreen(
                                        state = session,
                                        onLogin = sessionViewModel::login,
                                        onCreateAccount = { activatingAccess = true },
                                    )
                                }
                            }
                            else -> {
                                val availableExperiences = remember(session.roles) {
                                    RoleExperienceResolver.available(session.roles)
                                }
                                val userId = session.userId
                                var preferredExperience by remember(userId, session.roles) {
                                    mutableStateOf(
                                        userId
                                            ?.let(activeExperiencePreferences::read)
                                            ?.takeIf(availableExperiences::contains),
                                    )
                                }

                                if (
                                    availableExperiences.size > 1 &&
                                    preferredExperience == null &&
                                    userId != null
                                ) {
                                    ExperiencePickerScreen(
                                        experiences = availableExperiences,
                                        onSelect = { selected ->
                                            activeExperiencePreferences.write(userId, selected)
                                            preferredExperience = selected
                                        },
                                    )
                                } else {
                                    val roleConfig = remember(session.roles, preferredExperience) {
                                        RoleExperienceResolver.resolve(
                                            roles = session.roles,
                                            preferredExperience = preferredExperience,
                                        )
                                    }
                                    val navController = rememberNavController()

                                    CompaneroScaffold(
                                        navController = navController,
                                        startDestination = roleConfig.startDestination,
                                        topLevelDestinations = roleConfig.topLevelDestinations,
                                    ) {
                                        composable(Destination.Home.route) {
                                            HomeScreen(
                                                onOpenSchedule = {
                                                    navController.navigate(Destination.Schedule.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onOpenClassrooms = {
                                                    navController.navigate(Destination.Classrooms.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                            )
                                        }
                                        composable(Destination.TeacherHome.route) {
                                            TeacherHomeScreen(
                                                onOpenAttendance = {
                                                    navController.navigate(Destination.Attendance.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onOpenChannel = {
                                                    navController.navigate(Destination.Channel.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onOpenClassrooms = {
                                                    navController.navigate(Destination.Classrooms.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onOpenGrading = {
                                                    navController.navigate(Destination.Grading.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onOpenSchedule = {
                                                    navController.navigate(Destination.Schedule.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                displayName = session.displayName,
                                            )
                                        }
                                        composable(Destination.TutorHome.route) {
                                            TutorHomeScreen(
                                                onOpenRequests = {
                                                    navController.navigate(Destination.TutorRequests.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                            )
                                        }
                                        composable(Destination.TutorRequests.route) {
                                            TutorRequestsScreen()
                                        }
                                        composable(Destination.CoordinatorHome.route) {
                                            CoordinatorHomeScreen(
                                                onOpenSchedule = {
                                                    navController.navigate(Destination.Schedule.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onOpenAttendance = {
                                                    navController.navigate(Destination.Attendance.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onOpenChannel = {
                                                    navController.navigate(Destination.Channel.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                            )
                                        }
                                        composable(Destination.AdminHome.route) {
                                            AdminHomeScreen(
                                                onOpenSchedule = {
                                                    navController.navigate(Destination.Schedule.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onOpenAttendance = {
                                                    navController.navigate(Destination.Attendance.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onOpenChannel = {
                                                    navController.navigate(Destination.Channel.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onOpenClassrooms = {
                                                    navController.navigate(Destination.Classrooms.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                            )
                                        }
                                        composable(Destination.Schedule.route) { ScheduleScreen() }
                                        composable(Destination.Classrooms.route) {
                                            ClassroomScreen(
                                                roles = session.roles,
                                                teacherExperience = roleConfig.experience == AppExperience.TEACHER,
                                                onOpenAttendance = { navController.navigate(Destination.Attendance.route) { launchSingleTop = true } },
                                                onOpenChannel = { navController.navigate(Destination.Channel.route) { launchSingleTop = true } },
                                                onOpenGrading = { navController.navigate(Destination.Grading.route) { launchSingleTop = true } },
                                            )
                                        }
                                        composable(Destination.Grading.route) { GradebookScreen() }
                                        composable(Destination.Channel.route) { ChannelScreen() }
                                        composable(Destination.Attendance.route) {
                                            AttendanceScreen(
                                                requestedMode = when (roleConfig.experience) {
                                                    AppExperience.STUDENT -> AttendanceMode.STUDENT
                                                    AppExperience.TEACHER -> AttendanceMode.TEACHER
                                                    else -> null
                                                },
                                            )
                                        }
                                        composable(Destination.RoleUnavailable.route) {
                                            RoleUnavailableScreen(roleConfig.experience)
                                        }
                                        composable(Destination.Profile.route) {
                                            ProfileScreen(
                                                displayName = session.displayName,
                                                roles = session.roles,
                                                canSwitchExperience = availableExperiences.size > 1,
                                                onSwitchExperience = {
                                                    userId?.let(activeExperiencePreferences::clear)
                                                    preferredExperience = null
                                                },
                                                onAppearance = {
                                                    navController.navigate(
                                                        Destination.AppearanceSettings.route,
                                                    )
                                                },
                                                onIntegrations = {
                                                    navController.navigate(
                                                        Destination.IntegrationSettings.route,
                                                    )
                                                },
                                                onLogout = sessionViewModel::logout,
                                            )
                                        }
                                        composable(Destination.IntegrationSettings.route) {
                                            IntegrationSettingsScreen(
                                                onOpenSchedule = {
                                                    navController.navigate(Destination.Schedule.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                            )
                                        }
                                        composable(Destination.AppearanceSettings.route) {
                                            AppearanceSettingsScreen(
                                                settings = appearance,
                                                onThemeMode = appearancePreferences::setThemeMode,
                                                onTextScale = appearancePreferences::setTextScale,
                                                onReducedMotion = appearancePreferences::setReducedMotion,
                                                onHighContrast = appearancePreferences::setHighContrast,
                                                onOpenDebugCatalog = if (BuildConfig.DEBUG) {
                                                    {
                                                        navController.navigate(
                                                            Destination.DesignSystemCatalog.route,
                                                        )
                                                    }
                                                } else {
                                                    null
                                                },
                                            )
                                        }
                                        if (BuildConfig.DEBUG) {
                                            composable(Destination.DesignSystemCatalog.route) {
                                                DesignSystemCatalogScreen()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@androidx.compose.runtime.Composable
private fun RoleUnavailableScreen(experience: AppExperience) {
    val title = when (experience) {
        AppExperience.COORDINATOR -> "Coordinación"
        AppExperience.ADMIN -> "Administración"
        AppExperience.SUPER_ADMIN -> "Administración general"
        AppExperience.UNSUPPORTED -> "Acceso no disponible"
        AppExperience.STUDENT -> "Estudiante"
        AppExperience.TEACHER -> "Docente"
        AppExperience.TUTOR -> "Tutor académico"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = org.companerodeescuela.core.designsystem.theme.CompaneroSpacing.page),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = if (experience == AppExperience.UNSUPPORTED) {
                "Tu cuenta está creada, pero todavía no tiene un rol operativo habilitado."
            } else {
                "Tu perfil está activo. Las herramientas para este rol se integrarán en el workspace V6 sin enviarte a pantallas de alumno."
            },
            modifier = Modifier.padding(top = org.companerodeescuela.core.designsystem.theme.CompaneroSpacing.sm),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


@androidx.compose.runtime.Composable
private fun ExperiencePickerScreen(
    experiences: List<AppExperience>,
    onSelect: (AppExperience) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = org.companerodeescuela.core.designsystem.theme.CompaneroSpacing.page),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "¿Cómo quieres usar Compañero?",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Tu cuenta tiene más de un perfil. Puedes cambiar de modo después desde Perfil.",
            modifier = Modifier.padding(
                top = org.companerodeescuela.core.designsystem.theme.CompaneroSpacing.xs,
                bottom = org.companerodeescuela.core.designsystem.theme.CompaneroSpacing.md,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        experiences.forEach { experience ->
            Button(
                onClick = { onSelect(experience) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = org.companerodeescuela.core.designsystem.theme.CompaneroSpacing.xs),
            ) {
                Text(experienceLabel(experience))
            }
        }
    }
}

private fun experienceLabel(experience: AppExperience): String = when (experience) {
    AppExperience.STUDENT -> "Continuar como estudiante"
    AppExperience.TEACHER -> "Continuar como docente"
    AppExperience.TUTOR -> "Continuar como tutor académico"
    AppExperience.COORDINATOR -> "Continuar como coordinación"
    AppExperience.ADMIN -> "Continuar como administración"
    AppExperience.SUPER_ADMIN -> "Continuar como administración general"
    AppExperience.UNSUPPORTED -> "Continuar"
}
