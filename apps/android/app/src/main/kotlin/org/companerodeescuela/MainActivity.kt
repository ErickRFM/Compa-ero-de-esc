@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import org.companerodeescuela.core.attendance.AttendanceSyncScheduler
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.designsystem.v8.V8LoadingState
import org.companerodeescuela.core.motion.ProvideCompaneroMotionPreferences
import org.companerodeescuela.core.navigation.AcademicRoutePolicy
import org.companerodeescuela.core.navigation.AppDistribution
import org.companerodeescuela.core.navigation.AppExperience
import org.companerodeescuela.core.navigation.CompaneroScaffold
import org.companerodeescuela.core.navigation.Destination
import org.companerodeescuela.core.navigation.RoleExperienceResolver
import org.companerodeescuela.feature.admin.adminHomeScreen
import org.companerodeescuela.feature.attendance.AttendanceMode
import org.companerodeescuela.feature.attendance.TeacherHomeScreen
import org.companerodeescuela.feature.attendance.attendanceScreen
import org.companerodeescuela.feature.auth.AccountOnboardingScreen
import org.companerodeescuela.feature.auth.AuthDraftViewModel
import org.companerodeescuela.feature.auth.InvitationAcceptanceScreen
import org.companerodeescuela.feature.auth.InvitationIssueScreen
import org.companerodeescuela.feature.auth.LoginScreen
import org.companerodeescuela.feature.auth.MfaScreen
import org.companerodeescuela.feature.auth.RegistrationScreen
import org.companerodeescuela.feature.auth.SessionViewModel
import org.companerodeescuela.feature.auth.StaffApplicationScreen
import org.companerodeescuela.feature.channel.ChannelScreen
import org.companerodeescuela.feature.classroom.ClassroomAdministrationEditor
import org.companerodeescuela.feature.classroom.ClassroomScreen
import org.companerodeescuela.feature.coordinator.coordinatorHomeScreen
import org.companerodeescuela.feature.designsystem.DesignSystemCatalogScreen
import org.companerodeescuela.feature.grading.GradebookScreen
import org.companerodeescuela.feature.grading.StudentGradesScreen
import org.companerodeescuela.feature.home.HomeScreen
import org.companerodeescuela.feature.profile.ActiveExperiencePreferences
import org.companerodeescuela.feature.profile.ProfileScreen
import org.companerodeescuela.feature.schedule.ScheduleScreen
import org.companerodeescuela.feature.settings.AppThemeMode
import org.companerodeescuela.feature.settings.AppearancePreferences
import org.companerodeescuela.feature.settings.AppearanceSettingsScreen
import org.companerodeescuela.feature.settings.IntegrationSettingsScreen
import org.companerodeescuela.feature.tutoring.StudentRepresentativesScreen
import org.companerodeescuela.feature.tutoring.TutorGroupsScreen
import org.companerodeescuela.feature.tutoring.TutorHomeScreen
import org.companerodeescuela.feature.tutoring.TutorRequestsScreen
import org.companerodeescuela.notifications.PushRegistrationDiagnostics
import org.companerodeescuela.notifications.PushRegistrationSchedule
import org.companerodeescuela.notifications.channelNotificationRoute
import org.companerodeescuela.notifications.parseAttendanceSessionId
import org.companerodeescuela.shared.contracts.AttendancePushContract
import org.companerodeescuela.shared.contracts.TeacherClassContext
import org.companerodeescuela.shared.contracts.UserRole
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var pendingAttendanceSessionId by mutableStateOf<String?>(null)
    private var pendingChannelNavigation by mutableStateOf(false)
    private var pendingChannelTasksTab by mutableStateOf(false)
    private var pendingChannelId by mutableStateOf<String?>(null)

    @Inject
    lateinit var attendanceSyncScheduler: AttendanceSyncScheduler

    @Inject
    lateinit var appearancePreferences: AppearancePreferences

    @Inject
    lateinit var activeExperiencePreferences: ActiveExperiencePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingAttendanceSessionId = attendanceSessionId(intent)
        readChannelNotification(intent)
        appearancePreferences.applySavedLanguage()
        val appDistribution = AppDistribution.valueOf(BuildConfig.APP_DISTRIBUTION)

        setContent {
            val notificationsPermissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) {
                    PushRegistrationSchedule.enqueue(this@MainActivity)
                }
            val pushRegistration by PushRegistrationDiagnostics.state.collectAsStateWithLifecycle()
            val appearance by appearancePreferences.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme =
                when (appearance.themeMode) {
                    AppThemeMode.SYSTEM -> systemDark
                    AppThemeMode.LIGHT -> false
                    AppThemeMode.DARK -> true
                }

            CompaneroTheme(
                darkTheme = darkTheme,
                dynamicColor = appearance.dynamicColor,
                accentPalette = appearance.accentPalette,
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
                        val authDraft: AuthDraftViewModel = viewModel()
                        val session by sessionViewModel.state.collectAsStateWithLifecycle()
                        SideEffect {
                            // Follow the selected theme, not the student's role:
                            // light-mode screens must use dark system-bar icons.
                            val style =
                                if (darkTheme) {
                                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                                } else {
                                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                                }
                            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                        }
                        var activatingAccess by rememberSaveable { mutableStateOf(false) }
                        var loginExperienceName by rememberSaveable { mutableStateOf<String?>(null) }

                        LaunchedEffect(session.checking, session.authenticated, session.userId, session.accountStatus, session.roles) {
                            if (!session.checking && !session.authenticated) authDraft.clearAll()
                            if (session.authenticated) authDraft.clearRegistration()
                            if (session.authenticated) {
                                activatingAccess = false
                                if (session.accountStatus == org.companerodeescuela.shared.contracts.AccountStatus.ACTIVE &&
                                    appDistribution == AppDistribution.ACADEMIC &&
                                    org.companerodeescuela.shared.contracts.UserRole.STUDENT in session.roles
                                ) {
                                    attendanceSyncScheduler.schedule()
                                }
                                if (session.accountStatus == org.companerodeescuela.shared.contracts.AccountStatus.ACTIVE &&
                                    BuildConfig.FIREBASE_CONFIGURED &&
                                    Build.VERSION.SDK_INT >= 33
                                ) {
                                    val consent = getSharedPreferences("notification_consent", MODE_PRIVATE)
                                    if (!consent.getBoolean("requested", false) &&
                                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        consent.edit().putBoolean("requested", true).apply()
                                        notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }
                        }

                        when {
                            session.checking -> {
                                V8LoadingState(
                                    modifier = Modifier.safeDrawingPadding(),
                                    title = stringResource(org.companerodeescuela.core.designsystem.R.string.ux_loading_session),
                                    branded = true,
                                )
                            }
                            !session.authenticated -> {
                                if (activatingAccess) {
                                    RegistrationScreen(
                                        state = session,
                                        onRegister = { sessionViewModel.register(it) },
                                        onReloadInstitutions = sessionViewModel::loadRegistrationInstitutions,
                                        onBackToLogin = { activatingAccess = false },
                                    )
                                } else {
                                    LoginScreen(
                                        state = session,
                                        distribution = appDistribution,
                                        onLogin = { username, password, experience ->
                                            loginExperienceName = experience.name
                                            sessionViewModel.login(username, password)
                                        },
                                        onAutomaticLogin = { username, password ->
                                            loginExperienceName = null
                                            sessionViewModel.login(username, password)
                                        },
                                        onCreateAccount = {
                                            loginExperienceName = null
                                            activatingAccess = true
                                            sessionViewModel.loadRegistrationInstitutions()
                                        },
                                    )
                                }
                            }
                            session.accountStatus != org.companerodeescuela.shared.contracts.AccountStatus.ACTIVE -> {
                                AccountOnboardingScreen(
                                    session,
                                    sessionViewModel::verifyEmail,
                                    sessionViewModel::resendVerification,
                                    sessionViewModel::refreshIdentity,
                                    sessionViewModel::logout,
                                )
                            }
                            else -> {
                                val availableExperiences =
                                    remember(session.roles) {
                                        RoleExperienceResolver.available(session.roles, appDistribution)
                                    }
                                val userId = session.userId
                                var preferredExperience by remember(userId, session.roles) {
                                    mutableStateOf(
                                        RoleExperienceResolver.preferredAfterLogin(
                                            roles = session.roles,
                                            requested = AppExperience.entries.firstOrNull { it.name == loginExperienceName },
                                            saved = userId?.let(activeExperiencePreferences::read),
                                            distribution = appDistribution,
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
                                        selectionUnavailable =
                                            loginExperienceName != null &&
                                                AppExperience.entries.firstOrNull { it.name == loginExperienceName } !in
                                                availableExperiences,
                                        onSelect = { selected ->
                                            activeExperiencePreferences.write(userId, selected)
                                            loginExperienceName = null
                                            preferredExperience = selected
                                        },
                                        onLogout = {
                                            loginExperienceName = null
                                            sessionViewModel.logout()
                                        },
                                    )
                                } else {
                                    val roleConfig =
                                        remember(session.roles, preferredExperience) {
                                            RoleExperienceResolver.resolve(
                                                roles = session.roles,
                                                preferredExperience = preferredExperience,
                                                distribution = appDistribution,
                                            )
                                        }
                                    val navController = key(userId, roleConfig.experience, session.roles) { rememberNavController() }
                                    var channelTabRequest by rememberSaveable(userId, roleConfig.experience) { mutableIntStateOf(-1) }
                                    var channelIdRequest by rememberSaveable(
                                        userId,
                                        roleConfig.experience,
                                    ) { mutableStateOf<String?>(null) }
                                    LaunchedEffect(pendingAttendanceSessionId, roleConfig.experience, session.roles) {
                                        val requestedSession = pendingAttendanceSessionId ?: return@LaunchedEffect
                                        if (UserRole.STUDENT !in session.roles || appDistribution != AppDistribution.ACADEMIC) {
                                            pendingAttendanceSessionId = null
                                        } else if (roleConfig.experience != AppExperience.STUDENT) {
                                            preferredExperience = AppExperience.STUDENT
                                        } else {
                                            navController.navigate(Destination.Attendance.route) { launchSingleTop = true }
                                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                                AttendancePushContract.SESSION_ID,
                                                requestedSession,
                                            )
                                            pendingAttendanceSessionId = null
                                        }
                                    }
                                    LaunchedEffect(
                                        pendingChannelNavigation,
                                        pendingChannelId,
                                        pendingChannelTasksTab,
                                        roleConfig.experience,
                                        session.roles,
                                    ) {
                                        if (pendingChannelNavigation && userId != null) {
                                            val destinationExperience =
                                                AcademicRoutePolicy.channelExperience(
                                                    current = roleConfig.experience,
                                                    roles = session.roles,
                                                    distribution = appDistribution,
                                                )
                                            when {
                                                destinationExperience == null -> {
                                                    pendingChannelNavigation = false
                                                    pendingChannelId = null
                                                    pendingChannelTasksTab = false
                                                }
                                                destinationExperience != roleConfig.experience -> {
                                                    preferredExperience = destinationExperience
                                                }
                                                else -> {
                                                    pendingChannelNavigation = false
                                                    channelTabRequest = if (pendingChannelTasksTab) 2 else 0
                                                    channelIdRequest = pendingChannelId
                                                    pendingChannelId = null
                                                    pendingChannelTasksTab = false
                                                    navController.navigate(Destination.Channel.route) {
                                                        launchSingleTop = true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    CompaneroScaffold(
                                        navController = navController,
                                        startDestination = roleConfig.startDestination,
                                        topLevelDestinations = roleConfig.topLevelDestinations,
                                        highContrast = appearance.highContrast,
                                        campusBackdropEnabled = appearance.campusBackdropEnabled,
                                    ) {
                                        composable(Destination.Home.route) {
                                            HomeScreen(
                                                displayName = session.displayName,
                                                onOpenAttendance = {
                                                    navController.navigate(Destination.Attendance.route) { launchSingleTop = true }
                                                },
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
                                                    channelTabRequest = 0
                                                    navController.navigate(Destination.Channel.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onOpenTasks = {
                                                    channelTabRequest = 2
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
                                                    channelTabRequest = 0
                                                    navController.navigate(Destination.Channel.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                onOpenTasks = {
                                                    channelTabRequest = 2
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
                                        composable(Destination.RepresentativeInvitations.route) {
                                            if (AcademicRoutePolicy.studentActions(roleConfig.experience, session.roles)) {
                                                StudentRepresentativesScreen()
                                            } else {
                                                RoleUnavailableScreen(
                                                    AppExperience.UNSUPPORTED,
                                                    onLogout = sessionViewModel::logout,
                                                    onBack = { navController.popBackStack() },
                                                )
                                            }
                                        }
                                        composable(Destination.CoordinatorHome.route) {
                                            coordinatorHomeScreen(
                                                directionExperience = roleConfig.experience == AppExperience.PRINCIPAL,
                                                onOpenSchedule = {
                                                    navController.navigate(Destination.Schedule.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                            )
                                        }
                                        composable(Destination.AdminHome.route) {
                                            adminHomeScreen(
                                                onOpenSecurity = { navController.navigate(Destination.Mfa.route) },
                                                onOpenSchedule = {
                                                    // D1 fix: admin schedule must navigate to Schedule, not Classrooms
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
                                        composable(Destination.Schedule.route) {
                                            if (
                                                roleConfig.experience == AppExperience.ADMIN ||
                                                roleConfig.experience == AppExperience.SUPER_ADMIN
                                            ) {
                                                ClassroomAdministrationEditor(
                                                    onOpenChannel = {
                                                        navController.navigate(Destination.Channel.route) { launchSingleTop = true }
                                                    },
                                                    startOnSchedule = true,
                                                )
                                            } else {
                                                ScheduleScreen()
                                            }
                                        }
                                        composable(Destination.Classrooms.route) {
                                            ClassroomScreen(
                                                roles = session.roles,
                                                studentExperience =
                                                    AcademicRoutePolicy.studentActions(
                                                        roleConfig.experience,
                                                        session.roles,
                                                    ),
                                                teacherExperience = roleConfig.experience == AppExperience.TEACHER,
                                                administrativeExperience =
                                                    roleConfig.experience == AppExperience.ADMIN ||
                                                        roleConfig.experience == AppExperience.SUPER_ADMIN,
                                                teacherUserId = session.userId,
                                                onOpenAttendance = { classroom ->
                                                    navController.navigate(Destination.Attendance.route) { launchSingleTop = true }
                                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                                        TeacherClassContext.STATE_KEY,
                                                        TeacherClassContext.from(
                                                            classroom,
                                                        ),
                                                    )
                                                },
                                                onOpenChannel = { classroom ->
                                                    navController.navigate(Destination.Channel.route) { launchSingleTop = true }
                                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                                        TeacherClassContext.STATE_KEY,
                                                        classroom?.let(TeacherClassContext::from),
                                                    )
                                                },
                                                onOpenGrading = { classroom ->
                                                    navController.navigate(Destination.Grading.route) { launchSingleTop = true }
                                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                                        TeacherClassContext.STATE_KEY,
                                                        TeacherClassContext.from(classroom),
                                                    )
                                                },
                                                onOpenStudentGrades = {
                                                    navController.navigate(Destination.Grading.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                            )
                                        }
                                        composable(Destination.Grading.route) { entry ->
                                            val selection by
                                                entry.savedStateHandle
                                                    .getStateFlow<TeacherClassContext?>(
                                                        TeacherClassContext.STATE_KEY,
                                                        null,
                                                    ).collectAsStateWithLifecycle()
                                            when {
                                                AcademicRoutePolicy.studentActions(roleConfig.experience, session.roles) ->
                                                    StudentGradesScreen()
                                                AcademicRoutePolicy.teacherActions(roleConfig.experience, session.roles) ->
                                                    GradebookScreen(requestedClassroom = selection)
                                                else ->
                                                    RoleUnavailableScreen(
                                                        roleConfig.experience,
                                                        onLogout = sessionViewModel::logout,
                                                        onBack = { navController.popBackStack() },
                                                    )
                                            }
                                        }
                                        composable(Destination.Channel.route) { entry ->
                                            val selection by
                                                entry.savedStateHandle
                                                    .getStateFlow<TeacherClassContext?>(
                                                        TeacherClassContext.STATE_KEY,
                                                        null,
                                                    ).collectAsStateWithLifecycle()
                                            val isAdministrative =
                                                roleConfig.experience == AppExperience.ADMIN ||
                                                    roleConfig.experience == AppExperience.SUPER_ADMIN ||
                                                    roleConfig.experience == AppExperience.COORDINATOR ||
                                                    roleConfig.experience == AppExperience.PRINCIPAL
                                            ChannelScreen(
                                                requestedClassroom = selection,
                                                isAdministrativeSession = isAdministrative,
                                                requestedTab = channelTabRequest,
                                                requestedChannelId = channelIdRequest,
                                                onConsumeRequestedTab = {
                                                    channelTabRequest = -1
                                                    channelIdRequest = null
                                                },
                                            )
                                        }
                                        composable(Destination.Attendance.route) { entry ->
                                            val selection by
                                                entry.savedStateHandle
                                                    .getStateFlow<TeacherClassContext?>(
                                                        TeacherClassContext.STATE_KEY,
                                                        null,
                                                    ).collectAsStateWithLifecycle()
                                            val requestedSessionId by entry.savedStateHandle
                                                .getStateFlow<String?>(AttendancePushContract.SESSION_ID, null)
                                                .collectAsStateWithLifecycle()
                                            if (AcademicRoutePolicy.studentActions(roleConfig.experience, session.roles) ||
                                                AcademicRoutePolicy.teacherActions(roleConfig.experience, session.roles)
                                            ) {
                                                attendanceScreen(
                                                    requestedClassroom = selection,
                                                    requestedSessionId = requestedSessionId,
                                                    onConsumeRequestedSession = {
                                                        entry.savedStateHandle.remove<String>(AttendancePushContract.SESSION_ID)
                                                    },
                                                    requestedMode =
                                                        when (roleConfig.experience) {
                                                            AppExperience.STUDENT -> AttendanceMode.STUDENT
                                                            AppExperience.TEACHER -> AttendanceMode.TEACHER
                                                            else -> null
                                                        },
                                                )
                                            } else {
                                                RoleUnavailableScreen(
                                                    roleConfig.experience,
                                                    onLogout = sessionViewModel::logout,
                                                    onBack = { navController.popBackStack() },
                                                )
                                            }
                                        }
                                        composable(Destination.RoleUnavailable.route) {
                                            RoleUnavailableScreen(
                                                roleConfig.experience,
                                                onLogout = sessionViewModel::logout,
                                                onBack = { navController.popBackStack() },
                                            )
                                        }
                                        composable(Destination.Profile.route) {
                                            val adminExperience =
                                                roleConfig.experience == AppExperience.ADMIN ||
                                                    roleConfig.experience == AppExperience.SUPER_ADMIN
                                            val adminProfile =
                                                appDistribution == AppDistribution.MANAGEMENT && adminExperience
                                            ProfileScreen(
                                                displayName = session.displayName,
                                                appVersion = BuildConfig.VERSION_NAME,
                                                roles = session.roles,
                                                canSwitchExperience = availableExperiences.size > 1,
                                                managementExperience = appDistribution == AppDistribution.MANAGEMENT,
                                                activeExperience = roleConfig.experience,
                                                onAdminDashboard =
                                                    if (adminProfile) {
                                                        {
                                                            navController.navigate(Destination.AdminHome.route) {
                                                                launchSingleTop = true
                                                            }
                                                        }
                                                    } else {
                                                        null
                                                    },
                                                onAdminSchedule =
                                                    if (adminProfile) {
                                                        {
                                                            navController.navigate(Destination.Schedule.route) {
                                                                launchSingleTop = true
                                                            }
                                                        }
                                                    } else {
                                                        null
                                                    },
                                                onAdminClassrooms =
                                                    if (adminProfile) {
                                                        {
                                                            navController.navigate(Destination.Classrooms.route) {
                                                                launchSingleTop = true
                                                            }
                                                        }
                                                    } else {
                                                        null
                                                    },
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
                                                onSecurity = { navController.navigate(Destination.Mfa.route) },
                                                onInvitation = { navController.navigate(Destination.InvitationAcceptance.route) },
                                                onInvitationIssue =
                                                    if (appDistribution == AppDistribution.MANAGEMENT &&
                                                        roleConfig.experience == AppExperience.SUPER_ADMIN &&
                                                        org.companerodeescuela.shared.contracts.UserRole.SUPER_ADMIN in session.roles
                                                    ) {
                                                        { navController.navigate(Destination.InvitationIssue.route) }
                                                    } else {
                                                        null
                                                    },
                                                notificationsStatus = pushRegistration.userMessage,
                                                onNotifications =
                                                    if (BuildConfig.FIREBASE_CONFIGURED) {
                                                        {
                                                            if (Build.VERSION.SDK_INT >= 33) {
                                                                val consent = getSharedPreferences("notification_consent", MODE_PRIVATE)
                                                                val granted =
                                                                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                                                                        PackageManager.PERMISSION_GRANTED
                                                                val permanentlyDenied =
                                                                    consent.getBoolean("requested", false) &&
                                                                        !ActivityCompat.shouldShowRequestPermissionRationale(
                                                                            this@MainActivity,
                                                                            Manifest.permission.POST_NOTIFICATIONS,
                                                                        )
                                                                if (granted || permanentlyDenied) {
                                                                    openNotificationSettings()
                                                                } else {
                                                                    consent.edit().putBoolean("requested", true).apply()
                                                                    notificationsPermissionLauncher.launch(
                                                                        Manifest.permission.POST_NOTIFICATIONS,
                                                                    )
                                                                }
                                                            } else {
                                                                openNotificationSettings()
                                                            }
                                                        }
                                                    } else {
                                                        null
                                                    },
                                                onTutorRequest =
                                                    if (appDistribution == AppDistribution.ACADEMIC) {
                                                        { navController.navigate(Destination.StaffApplication.route) }
                                                    } else {
                                                        null
                                                    },
                                                onLogout = {
                                                    loginExperienceName = null
                                                    sessionViewModel.logout()
                                                },
                                            )
                                        }
                                        composable(Destination.InvitationIssue.route) {
                                            if (appDistribution == AppDistribution.MANAGEMENT &&
                                                roleConfig.experience == AppExperience.SUPER_ADMIN &&
                                                org.companerodeescuela.shared.contracts.UserRole.SUPER_ADMIN in session.roles
                                            ) {
                                                InvitationIssueScreen(onBack = { navController.popBackStack() })
                                            } else {
                                                RoleUnavailableScreen(
                                                    roleConfig.experience,
                                                    onLogout = sessionViewModel::logout,
                                                    onBack = { navController.popBackStack() },
                                                )
                                            }
                                        }
                                        composable(Destination.InvitationAcceptance.route) {
                                            InvitationAcceptanceScreen(
                                                state = session,
                                                onAccept = sessionViewModel::acceptRegistrationInvitation,
                                                onBack = { navController.popBackStack() },
                                            )
                                        }
                                        composable(Destination.Mfa.route) {
                                            MfaScreen(onBack = { navController.popBackStack() })
                                        }
                                        composable(Destination.StaffApplication.route) {
                                            StaffApplicationScreen(onBack = { navController.popBackStack() })
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
                                                onAccentPalette = appearancePreferences::setAccentPalette,
                                                onDynamicColor = appearancePreferences::setDynamicColor,
                                                onCampusBackdrop = appearancePreferences::setCampusBackdropEnabled,
                                                onLanguage = appearancePreferences::setLanguage,
                                                onTextScale = appearancePreferences::setTextScale,
                                                onReducedMotion = appearancePreferences::setReducedMotion,
                                                onHighContrast = appearancePreferences::setHighContrast,
                                                onOpenDebugCatalog =
                                                    if (BuildConfig.DEBUG) {
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAttendanceSessionId = attendanceSessionId(intent)
        readChannelNotification(intent)
    }

    private fun readChannelNotification(intent: Intent?) {
        val route =
            channelNotificationRoute(
                intent?.action,
                intent?.getStringExtra("destination"),
                intent?.getStringExtra("type"),
                intent?.getStringExtra("channelId"),
            )
        pendingChannelId = route?.channelId
        pendingChannelTasksTab = route?.openTasks == true
        pendingChannelNavigation = route != null
    }

    override fun onResume() {
        super.onResume()
        // Recheck permission/channel changes after returning from Android settings.
        if (BuildConfig.FIREBASE_CONFIGURED) PushRegistrationSchedule.enqueue(this)
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun attendanceSessionId(intent: android.content.Intent?): String? =
        parseAttendanceSessionId(
            intent?.action,
            intent?.getStringExtra(AttendancePushContract.SESSION_ID),
        )
}

@androidx.compose.runtime.Composable
private fun RoleUnavailableScreen(
    experience: AppExperience,
    onLogout: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val title =
        when (experience) {
            AppExperience.PRINCIPAL -> "Dirección"
            AppExperience.COORDINATOR -> "Coordinación"
            AppExperience.ADMIN -> "Administración"
            AppExperience.SUPER_ADMIN -> "Administración general"
            AppExperience.GUEST -> "Invitado"
            AppExperience.UNSUPPORTED -> "Acceso no disponible"
            AppExperience.STUDENT -> "Estudiante"
            AppExperience.TEACHER -> "Docente"
            AppExperience.TUTOR -> "Tutor académico"
        }
    Column(
        modifier =
            Modifier
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
        org.companerodeescuela.core.designsystem.v8.V8GlassCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = org.companerodeescuela.core.designsystem.theme.CompaneroSpacing.sm),
        ) {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        org.companerodeescuela.core.designsystem.theme.CompaneroSpacing.sm,
                    ),
            ) {
                Text(
                    text =
                        if (experience == AppExperience.UNSUPPORTED) {
                            "Tu cuenta no tiene un perfil autorizado en esta aplicación. Usa la aplicación correspondiente o cierra sesión."
                        } else {
                            "Esta sección no forma parte de las herramientas asignadas a tu perfil activo."
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (experience == AppExperience.UNSUPPORTED) {
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.role_unavailable_logout))
                    }
                } else {
                    if (onBack != null) {
                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Volver")
                        }
                    }
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.role_unavailable_logout))
                    }
                }
            }
        }
    }
}
