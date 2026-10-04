package org.companerodeescuela

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.attendance.AttendanceSyncScheduler
import org.companerodeescuela.core.navigation.CompaneroScaffold
import org.companerodeescuela.core.navigation.Destination
import org.companerodeescuela.core.navigation.TopLevelDestination
import org.companerodeescuela.feature.auth.LoginScreen
import org.companerodeescuela.feature.attendance.AttendanceScreen
import org.companerodeescuela.feature.auth.SessionViewModel
import org.companerodeescuela.feature.home.HomeScreen
import org.companerodeescuela.feature.profile.ProfileScreen
import org.companerodeescuela.feature.schedule.ScheduleScreen
import org.companerodeescuela.shared.contracts.UserRole

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var attendanceSyncScheduler: AttendanceSyncScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            CompaneroTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val sessionViewModel: SessionViewModel = hiltViewModel()
                    val session by sessionViewModel.state.collectAsStateWithLifecycle()

                    LaunchedEffect(session.authenticated) {
                        if (session.authenticated) {
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
                            LoginScreen(
                                state = session,
                                onLogin = sessionViewModel::login,
                            )
                        }
                        else -> {
                            val teacherOnly =
                                UserRole.TEACHER in session.roles &&
                                    UserRole.STUDENT !in session.roles
                            val startDestination =
                                if (teacherOnly) Destination.Attendance else Destination.Home
                            val topLevelDestinations =
                                if (teacherOnly) {
                                    listOf(TopLevelDestination.Attendance)
                                } else {
                                    TopLevelDestination.entries
                                }

                            CompaneroScaffold(
                                startDestination = startDestination,
                                topLevelDestinations = topLevelDestinations,
                            ) {
                                composable(Destination.Home.route) { HomeScreen() }
                                composable(Destination.Schedule.route) { ScheduleScreen() }
                                composable(Destination.Attendance.route) { AttendanceScreen() }
                                composable(Destination.Profile.route) {
                                    ProfileScreen(
                                        displayName = session.displayName,
                                        onLogout = sessionViewModel::logout,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
