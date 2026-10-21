package org.companerodeescuela

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.compose.composable
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled
import org.companerodeescuela.core.navigation.CompaneroScaffold
import org.companerodeescuela.core.navigation.RoleExperienceResolver
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * V9 palette/glass must not depend on whether the first screen belongs to a student.
 * These tests exercise the actual Compose shell, not a duplicate theme calculator.
 */
class V9RoleShellUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun coordinatorLightIsReadableWhiteGlass() {
        verify(UserRole.COORDINATOR, dark = false, highContrast = false)
    }

    @Test
    fun tutorDarkUsesMatteV9Glass() {
        verify(UserRole.TUTOR, dark = true, highContrast = false)
    }

    @Test
    fun adminDarkHighContrastUsesBlackFoundation() {
        verify(UserRole.ADMIN, dark = true, highContrast = true)
    }

    private fun verify(
        role: UserRole,
        dark: Boolean,
        highContrast: Boolean,
    ) {
        val config = RoleExperienceResolver.resolve(setOf(role))
        var glass: Boolean? = null
        var background: Color? = null
        compose.setContent {
            CompaneroTheme(darkTheme = dark, highContrast = highContrast) {
                CompaneroScaffold(
                    startDestination = config.startDestination,
                    topLevelDestinations = emptyList(),
                    highContrast = highContrast,
                ) {
                    composable(config.startDestination.route) {
                        val currentGlass = LocalV8GlassEnabled.current
                        val currentBackground = MaterialTheme.colorScheme.background
                        SideEffect {
                            glass = currentGlass
                            background = currentBackground
                        }
                        Text("Pantalla de prueba", modifier = Modifier.testTag("v9_role_content"))
                    }
                }
            }
        }
        compose.onNodeWithTag("v9_role_content").assertExists()
        compose.waitForIdle()
        assertEquals(true, glass)
        if (!dark) {
            assertEquals(Color.White, background)
        } else if (highContrast) {
            assertEquals(Color.Black, background)
        } else {
            assertTrue(requireNotNull(background) != Color.White)
        }
    }
}
