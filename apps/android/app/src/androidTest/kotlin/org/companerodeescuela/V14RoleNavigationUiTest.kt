package org.companerodeescuela

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.navigation.CompaneroScaffold
import org.companerodeescuela.core.navigation.Destination
import org.companerodeescuela.core.navigation.RoleExperienceResolver
import org.companerodeescuela.core.navigation.navigateToTopLevel
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/** Real Compose navigation-graph fixtures for every authorized main experience. */
class V14RoleNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val routes = listOf(
        Destination.Home, Destination.TeacherHome, Destination.TutorHome,
        Destination.TutorGroups, Destination.TutorRequests,
        Destination.CoordinatorHome, Destination.AdminHome,
        Destination.Schedule, Destination.Attendance, Destination.Classrooms,
        Destination.Channel, Destination.Grading, Destination.Profile,
        Destination.AppearanceSettings,
    )

    @Test fun studentBackToHome() = verify(UserRole.STUDENT)
    @Test fun teacherBackToHome() = verify(UserRole.TEACHER)
    @Test fun tutorBackToHome() = verify(UserRole.TUTOR)
    @Test fun coordinatorBackToHome() = verify(UserRole.COORDINATOR)
    @Test fun adminBackToHome() = verify(UserRole.ADMIN)
    @Test fun superAdminBackToHome() = verify(UserRole.SUPER_ADMIN)

    private fun verify(role: UserRole) {
        val config = RoleExperienceResolver.resolve(setOf(role))
        var nav: NavHostController? = null
        compose.setContent {
            CompaneroTheme {
                val controller = rememberNavController()
                SideEffect { nav = controller }
                CompaneroScaffold(
                    navController = controller,
                    startDestination = config.startDestination,
                    topLevelDestinations = config.topLevelDestinations,
                ) {
                    routes.forEach { target ->
                        composable(target.route) {
                            Text(target.route, Modifier.testTag("route_${target.route}"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertNotNull(nav)
        val controller = requireNotNull(nav)
        val start = config.startDestination
        compose.onNodeWithTag("route_${start.route}").assertExists()
        val second = config.topLevelDestinations.getOrNull(1)?.destination ?: return
        compose.runOnIdle { controller.navigateToTopLevel(second) }
        compose.waitForIdle()
        compose.onNodeWithTag("route_${second.route}").assertExists()
        assertEquals(second.route, controller.currentDestination?.route)
        compose.runOnIdle { controller.navigate(Destination.AppearanceSettings.route) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("route_${second.route}").assertExists()
        compose.runOnIdle { controller.navigateToTopLevel(start) }
        compose.waitForIdle()
        compose.onNodeWithTag("route_${start.route}").assertExists()
        assertEquals(start.route, controller.currentDestination?.route)
    }
}
