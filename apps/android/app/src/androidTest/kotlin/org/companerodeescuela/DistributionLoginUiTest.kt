package org.companerodeescuela

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.motion.ProvideCompaneroMotionPreferences
import org.companerodeescuela.core.navigation.AppDistribution
import org.companerodeescuela.core.navigation.AppExperience
import org.companerodeescuela.feature.auth.LoginScreen
import org.companerodeescuela.feature.auth.SessionUiState
import org.junit.Rule
import org.junit.Test

class DistributionLoginUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val distribution: AppDistribution
        get() = AppDistribution.valueOf(BuildConfig.APP_DISTRIBUTION)

    @Test
    fun selectedProductShowsOnlyItsOwnRoleCards() {
        compose.setContent {
            CompaneroTheme {
                ProvideCompaneroMotionPreferences(reducedMotion = true) {
                    LoginScreen(
                        state = SessionUiState(checking = false),
                        onLogin = { _, _, _ -> },
                        distribution = distribution,
                    )
                }
            }
        }

        val expected =
            if (distribution == AppDistribution.ACADEMIC) {
                setOf(AppExperience.STUDENT, AppExperience.TEACHER, AppExperience.TUTOR)
            } else {
                setOf(AppExperience.COORDINATOR, AppExperience.ADMIN, AppExperience.SUPER_ADMIN)
            }
        val known =
            setOf(
                AppExperience.STUDENT,
                AppExperience.TEACHER,
                AppExperience.TUTOR,
                AppExperience.COORDINATOR,
                AppExperience.ADMIN,
                AppExperience.SUPER_ADMIN,
            )
        expected.forEach { compose.onNodeWithTag("role_" + it.name).assertExists() }
        (known - expected).forEach { compose.onNodeWithTag("role_" + it.name).assertDoesNotExist() }
    }

    @Test
    fun registrationActionIsOnlyOfferedByAcademicStudentCard() {
        compose.setContent {
            CompaneroTheme {
                ProvideCompaneroMotionPreferences(reducedMotion = true) {
                    LoginScreen(
                        state = SessionUiState(checking = false),
                        onLogin = { _, _, _ -> },
                        distribution = distribution,
                    )
                }
            }
        }

        if (distribution == AppDistribution.ACADEMIC) {
            compose.onNodeWithTag("login_registration").assertExists()
        } else {
            compose.onNodeWithTag("login_registration").assertDoesNotExist()
        }
    }
}
