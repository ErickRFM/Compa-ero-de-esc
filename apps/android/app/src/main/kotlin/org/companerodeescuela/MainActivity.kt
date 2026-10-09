package org.companerodeescuela

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
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
import androidx.compose.runtime.SideEffect
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
import org.companerodeescuela.shared.contracts.TeacherClassContext
import org.companerodeescuela.feature.profile.ActiveExperiencePreferences
import org.companerodeescuela.feature.profile.ProfileScreen
import org.companerodeescuela.feature.schedule.ScheduleScreen
import org.companerodeescuela.feature.tutoring.TutorHomeScreen
import org.companerodeescuela.feature.tutoring.TutorGroupsScreen
import org.companerodeescuela.feature.tutoring.TutorRequestsScreen
import org.companerodeescuela.feature.settings.AppThemeMode
import org.companerodeescuela.feature.settings.AppearancePreferences
import org.companerodeescuela.feature.settings.AppearanceSettingsScreen
import org.companerodeescuela.feature.settings.IntegrationSettingsScreen

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var attendanceSyncScheduler: AttendanceSyncScheduler

    @Inject
    lateinit var appearancePreferences: AppearancePreferences

    @Inject
    lateinit var activeExperiencePreferences: ActiveExperiencePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appearancePreferences.applySavedLanguage()

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
                        SideEffect {
                            val v8Dark = session.authenticated && session.roles == setOf(org.companerodeescuela.shared.contracts.UserRole.STUDENT)
                            val style = if (v8Dark || darkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                        }
                        var activatingAccess by rememberSaveable { mutableStateOf(false) }
                        var loginExperienceName by rememberSaveable { mutableStateOf<String?>(null) }

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
                                        onLogin = { username, password, experience ->
                                            loginExperienceName = experience.name
                                            sessionViewModel.login(username, password)
                                        },
                                        onCreateAccount = { loginExperienceName = null; activatingAccess = true },
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
                                        RoleExperienceResolver.preferredAfterLogin(
                                            roles = session.roles,
                                            requested = AppExperience.entries.firstOrNull { it.name == loginExperienceName },
                                            saved = userId?.let(activeExperiencePreferences::read),
                                        ),
                                    )
                                }

                                LaunchedEffect(userId, session.roles, preferredExperience) {
                                    val selected = preferredExperience
                                    if (userId != null && selected in availableExperiences && selected != null) {
                                        activeExperiencePreferences.write(userId, selected)
                                        // Consume intent once authorized; recreation now reads the saved choice.
                                        loginExperienceName = null
                                    }
                                }

                                if (
                                    availableExperiences.isNotEmpty() &&
                                    preferredExperience == null &&
                                    userId != null
                                ) {
                                    ExperiencePickerScreen(
                                        experiences = availableExperiences,
                                        selectionUnavailable = loginExperienceName != null && AppExperience.entries.firstOrNull { it.name == loginExperienceName } !in availableExperiences,
                                        onSelect = { selected ->
                                            activeExperiencePreferences.write(userId, selected)
                                            loginExperienceName = null
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
                                    val navController = key(userId, roleConfig.experience, session.roles) { rememberNavController() }
                                    SideEffect {
                                        val isDark = roleConfig.startDestination in setOf(Destination.Home, Destination.TeacherHome) || darkTheme
                                        val style = if (isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                                            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                                        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                                    }

                                    CompaneroScaffold(
                                        navController = navController,
                                        startDestination = roleConfig.startDestination,
                                        topLevelDestinations = roleConfig.topLevelDestinations,
                                        highContrast = appearance.highContrast,
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
                                                onOpenChannel = {
                                                    navController.navigate(Destination.Channel.route) {
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
                                        composable(Destination.TutorGroups.route) {
                                            TutorGroupsScreen()
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
                                                teacherUserId = session.userId,
                                                onOpenAttendance = { classroom ->
                                                    navController.navigate(Destination.Attendance.route) { launchSingleTop = true }
                                                    navController.currentBackStackEntry?.savedStateHandle?.set(TeacherClassContext.STATE_KEY, TeacherClassContext.from(classroom))
                                                },
                                                onOpenChannel = { classroom ->
                                                    navController.navigate(Destination.Channel.route) { launchSingleTop = true }
                                                    navController.currentBackStackEntry?.savedStateHandle?.set(TeacherClassContext.STATE_KEY, classroom?.let(TeacherClassContext::from))
                                                },
                                                onOpenGrading = { classroom ->
                                                    navController.navigate(Destination.Grading.route) { launchSingleTop = true }
                                                    navController.currentBackStackEntry?.savedStateHandle?.set(TeacherClassContext.STATE_KEY, TeacherClassContext.from(classroom))
                                                },
                                            )
                                        }
                                        composable(Destination.Grading.route) { entry ->
                                            val selection by entry.savedStateHandle.getStateFlow<TeacherClassContext?>(TeacherClassContext.STATE_KEY, null).collectAsStateWithLifecycle()
                                            GradebookScreen(requestedClassroom = selection)
                                        }
                                        composable(Destination.Channel.route) { entry ->
                                            val selection by entry.savedStateHandle.getStateFlow<TeacherClassContext?>(TeacherClassContext.STATE_KEY, null).collectAsStateWithLifecycle()
                                            ChannelScreen(requestedClassroom = selection)
                                        }
                                        composable(Destination.Attendance.route) { entry ->
                                            val selection by entry.savedStateHandle.getStateFlow<TeacherClassContext?>(TeacherClassContext.STATE_KEY, null).collectAsStateWithLifecycle()
                                            AttendanceScreen(
                                                requestedClassroom = selection,
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
                                                    loginExperienceName = null
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
                                                onLogout = { loginExperienceName = null; sessionViewModel.logout() },
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
                                                onLanguage = appearancePreferences::setLanguage,
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
        org.companerodeescuela.core.designsystem.v8.V8ScreenHeader {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
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
    selectionUnavailable: Boolean = false,
    onSelect: (AppExperience) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = org.companerodeescuela.core.designsystem.theme.CompaneroSpacing.page),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.login_experience_picker_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(if (selectionUnavailable) R.string.login_experience_unavailable else R.string.login_experience_picker_description),
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

@androidx.compose.runtime.Composable
private fun experienceLabel(experience: AppExperience): String = stringResource(when (experience) {
    AppExperience.STUDENT -> R.string.login_continue_student
    AppExperience.TEACHER -> R.string.login_continue_teacher
    AppExperience.TUTOR -> R.string.login_continue_tutor
    AppExperience.COORDINATOR -> R.string.login_continue_coordinator
    AppExperience.ADMIN -> R.string.login_continue_admin
    AppExperience.SUPER_ADMIN -> R.string.login_continue_super_admin
    AppExperience.UNSUPPORTED -> R.string.login_continue
})
