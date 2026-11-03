package org.companerodeescuela

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.navigation.AppDistribution
import org.companerodeescuela.core.navigation.AppExperience
import org.companerodeescuela.core.navigation.CompaneroScaffold
import org.companerodeescuela.core.navigation.Destination
import org.companerodeescuela.core.navigation.RoleExperienceResolver
import org.companerodeescuela.feature.profile.ProfileScreen
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic identities only; verifies actual selection callbacks and compact layout. */
class ProfileConsistencyUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactLargeTextPickerCanSelectLastRoleAndLogout() {
        var selected: AppExperience? = null
        var logout = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AuthTestLocale {
                    CompaneroTheme {
                        BoxWithConstraints {
                            Box(Modifier.requiredSize(minOf(maxWidth, 360.dp), minOf(maxHeight, 400.dp))) {
                                ExperiencePickerScreen(
                                    experiences =
                                        listOf(
                                            AppExperience.PRINCIPAL,
                                            AppExperience.COORDINATOR,
                                            AppExperience.ADMIN,
                                            AppExperience.SUPER_ADMIN,
                                        ),
                                    onSelect = { selected = it },
                                    onLogout = { logout++ },
                                )
                            }
                        }
                    }
                }
            }
        }
        capture("picker-before-scroll")
        compose
            .onNodeWithText("Continuar como administración general")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(AppExperience.SUPER_ADMIN, selected)
        compose
            .onNodeWithText("Cerrar sesión")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, logout)
        capture("picker-bottom-200")
    }

    @Test
    fun darkStudentProfileKeepsActiveIdentityAndPreferenceActions() {
        val actions = profile(360.dp, false, AppExperience.STUDENT, setOf(UserRole.STUDENT, UserRole.TEACHER))
        compose.onNodeWithText("Perfil activo · Estudiante").assertIsDisplayed()
        capture("profile-student-dark")
        compose
            .onNodeWithText("Tema y accesibilidad")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose
            .onNodeWithText("Cambiar perfil activo")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, actions.appearance)
        assertEquals(1, actions.switch)
        compose.onNodeWithText("Solicitar acceso de tutor").assertDoesNotExist()
    }

    @Test
    fun lightTeacherProfileAtLargeTextKeepsAccountActionsReachable() {
        val actions = profile(390.dp, true, AppExperience.TEACHER, setOf(UserRole.STUDENT, UserRole.TEACHER), scale = 2f)
        compose.onNodeWithText("Perfil activo · Docente").performScrollTo().assertIsDisplayed()
        capture("profile-teacher-light-200")
        compose
            .onNodeWithText("Solicitar acceso de tutor")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose
            .onNodeWithText("Cerrar sesión")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, actions.request)
        assertEquals(1, actions.logout)
    }

    @Test
    fun managementCoordinatorAtLargeTextCannotUseOtherRolesTools() {
        val actions = profile(430.dp, false, AppExperience.COORDINATOR, setOf(UserRole.COORDINATOR, UserRole.ADMIN), scale = 2f)
        compose.onNodeWithText("Perfil activo · Coordinación").performScrollTo().assertIsDisplayed()
        capture("profile-coordinator-dark-200")
        compose.onNodeWithText("Panel administrativo").assertDoesNotExist()
        compose.onNodeWithText("Horarios y grupos").assertDoesNotExist()
        compose.onNodeWithText("Cambiar perfil activo").performScrollTo().performClick()
        compose
            .onNodeWithText("Cerrar sesión")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, actions.switch)
        assertEquals(1, actions.logout)
        assertEquals(0, actions.admin)
    }

    private class Actions {
        var appearance = 0
        var switch = 0
        var request = 0
        var logout = 0
        var admin = 0
    }

    private fun profile(
        width: Dp,
        light: Boolean,
        active: AppExperience,
        roles: Set<UserRole>,
        scale: Float = 1f,
    ): Actions {
        val actions = Actions()
        var profileNav: NavHostController? = null
        val management = active == AppExperience.COORDINATOR
        val config =
            RoleExperienceResolver.resolve(
                roles,
                active,
                if (management) AppDistribution.MANAGEMENT else AppDistribution.ACADEMIC,
            )
        compose.setContent {
            AuthTestLocale {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    CompaneroTheme(darkTheme = !light) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.widthIn(max = width).fillMaxSize()) {
                                val nav = rememberNavController()
                                SideEffect { profileNav = nav }
                                CompaneroScaffold(nav, config.startDestination, config.topLevelDestinations) {
                                    composable(config.startDestination.route) { androidx.compose.material3.Text("Inicio sintético QA") }
                                    composable(Destination.Profile.route) {
                                        ProfileScreen(
                                            displayName = "Cuenta sintética de prueba con nombre largo",
                                            roles = roles,
                                            activeExperience = active,
                                            managementExperience = management,
                                            canSwitchExperience = true,
                                            onSwitchExperience = { actions.switch++ },
                                            onAppearance = { actions.appearance++ },
                                            onTutorRequest = { actions.request++ },
                                            onLogout = { actions.logout++ },
                                            onAdminDashboard = { actions.admin++ },
                                            onAdminSchedule = { actions.admin++ },
                                            onAdminClassrooms = { actions.admin++ },
                                            appVersion = "QA",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { requireNotNull(profileNav).navigate(Destination.Profile.route) }
        compose.waitUntil(8_000) {
            profileNav?.currentBackStackEntry?.let {
                it.destination.route == Destination.Profile.route && it.lifecycle.currentState == Lifecycle.State.RESUMED
            } == true
        }
        compose.waitForIdle()
        return actions
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            val directory = File(instrumentation.targetContext.filesDir, "audit-profiles").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            image.recycle()
        }
    }
}
