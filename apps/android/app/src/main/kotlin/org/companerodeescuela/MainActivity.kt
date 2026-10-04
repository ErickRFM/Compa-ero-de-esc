package org.companerodeescuela

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import org.companerodeescuela.core.navigation.CompaneroScaffold
import org.companerodeescuela.core.navigation.Destination
import org.companerodeescuela.feature.attendance.AttendanceScreen
import org.companerodeescuela.feature.attendance.TeacherHomeScreen
import org.companerodeescuela.feature.auth.LoginScreen
import org.companerodeescuela.feature.auth.RegistrationScreen
import org.companerodeescuela.feature.auth.SessionViewModel
import org.companerodeescuela.feature.designsystem.DesignSystemCatalogScreen
import org.companerodeescuela.feature.home.HomeScreen
import org.companerodeescuela.feature.profile.ProfileScreen
import org.companerodeescuela.feature.schedule.ScheduleScreen
import org.companerodeescuela.feature.settings.AppThemeMode
import org.companerodeescuela.feature.settings.AppearancePreferences
import org.companerodeescuela.feature.settings.AppearanceSettingsScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var attendanceSyncScheduler: AttendanceSyncScheduler

    @Inject
    lateinit var appearancePreferences: AppearancePreferences

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
                                        onActivate = sessionViewModel::login,
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
                                val navigation = RoleNavigationPolicy.resolve(session.roles)
                                val startDestination = navigation.startDestination
                                val topLevelDestinations = navigation.destinations
                                val navController = rememberNavController()

                                CompaneroScaffold(
                                    navController = navController,
                                    startDestination = startDestination,
                                    topLevelDestinations = topLevelDestinations,
                                ) {
                                    composable(Destination.Home.route) {
                                        HomeScreen(
                                            onOpenSchedule = {
                                                navController.navigate(Destination.Schedule.route) {
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
                                        )
                                    }
                                    composable(Destination.Schedule.route) { ScheduleScreen() }
                                    composable(Destination.Attendance.route) { AttendanceScreen() }
                                    composable(Destination.Profile.route) {
                                        ProfileScreen(
                                            displayName = session.displayName,
                                            roles = session.roles,
                                            onAppearance = {
                                                navController.navigate(
                                                    Destination.AppearanceSettings.route,
                                                )
                                            },
                                            onLogout = sessionViewModel::logout,
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
