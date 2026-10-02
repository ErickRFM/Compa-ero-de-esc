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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.navigation.CompaneroScaffold
import org.companerodeescuela.core.navigation.Destination
import org.companerodeescuela.feature.auth.LoginScreen
import org.companerodeescuela.feature.auth.SessionViewModel
import org.companerodeescuela.feature.home.HomeScreen
import org.companerodeescuela.feature.profile.ProfileScreen
import org.companerodeescuela.feature.schedule.ScheduleScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
                            CompaneroScaffold {
                                composable(Destination.Home.route) { HomeScreen() }
                                composable(Destination.Schedule.route) { ScheduleScreen() }
                                composable(Destination.Profile.route) { ProfileScreen() }
                            }
                        }
                    }
                }
            }
        }
    }
}
