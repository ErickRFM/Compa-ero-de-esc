package org.companerodeescuela

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.composable
import dagger.hilt.android.AndroidEntryPoint
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.navigation.CompaneroScaffold
import org.companerodeescuela.core.navigation.Destination
import org.companerodeescuela.feature.home.HomeScreen
import org.companerodeescuela.feature.profile.ProfileScreen

/**
 * Single activity host.
 *
 * Destinations are registered here, in the app module, so `core:navigation`
 * stays free of feature dependencies and each feature owns only its own
 * screen.
 */
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
                    CompaneroScaffold {
                        composable(Destination.Home.route) { HomeScreen() }
                        composable(Destination.Profile.route) { ProfileScreen() }
                    }
                }
            }
        }
    }
}
