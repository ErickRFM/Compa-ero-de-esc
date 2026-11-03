package org.companerodeescuela

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.companerodeescuela.core.designsystem.theme.CompaneroTheme
import org.companerodeescuela.core.navigation.AppExperience
import org.companerodeescuela.feature.profile.ProfileScreen
import org.companerodeescuela.shared.contracts.UserRole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Screens are presentation-only; a server role/capability check remains mandatory. */
class ManagementProfileNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun mixedStudentAccountDoesNotOfferTeacherRequestInStudentMode() {
        compose.setContent {
            AuthTestLocale {
                CompaneroTheme {
                    ProfileScreen(
                        displayName = "QA cuenta mixta",
                        roles = setOf(UserRole.STUDENT, UserRole.TEACHER),
                        activeExperience = AppExperience.STUDENT,
                        onTutorRequest = {},
                        onAppearance = {},
                        onLogout = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Solicitar acceso de tutor").assertDoesNotExist()
    }

    @Test
    fun coordinatorModeDoesNotExposeEmptyAdministrationGroupForAnotherRole() {
        compose.setContent {
            CompaneroTheme {
                ProfileScreen(
                    displayName = "QA coordinación",
                    roles = setOf(UserRole.COORDINATOR, UserRole.ADMIN),
                    activeExperience = AppExperience.COORDINATOR,
                    managementExperience = true,
                    onAdminDashboard = {},
                    onAdminSchedule = {},
                    onAdminClassrooms = {},
                    onAppearance = {},
                    onLogout = {},
                )
            }
        }
        compose.onNodeWithText("Administración").assertDoesNotExist()
        compose.onNodeWithText("Panel administrativo").assertDoesNotExist()
    }

    @Test
    fun explicitTeacherModeRetainsTutorRequestForMixedAccount() {
        var requests = 0
        compose.setContent {
            AuthTestLocale {
                CompaneroTheme {
                    ProfileScreen(
                        displayName = "QA docente",
                        roles = setOf(UserRole.STUDENT, UserRole.TEACHER),
                        activeExperience = AppExperience.TEACHER,
                        onTutorRequest = { requests++ },
                        onAppearance = {},
                        onLogout = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Perfil activo · Docente").assertExists()
        compose.onNodeWithText("Solicitar acceso de tutor").performScrollTo().performClick()
        assertEquals(1, requests)
    }

    @Test
    fun revokedActiveAdminCannotRetainManagementShortcuts() {
        compose.setContent {
            CompaneroTheme {
                ProfileScreen(
                    displayName = "QA rol retirado",
                    roles = setOf(UserRole.COORDINATOR),
                    activeExperience = AppExperience.ADMIN,
                    managementExperience = true,
                    onAdminDashboard = {},
                    onAdminSchedule = {},
                    onAdminClassrooms = {},
                    onAppearance = {},
                    onLogout = {},
                )
            }
        }
        compose.onNodeWithText("Perfil activo · No disponible").assertExists()
        compose.onNodeWithText("Panel administrativo").assertDoesNotExist()
        compose.onNodeWithText("Horarios y grupos").assertDoesNotExist()
        compose.onNodeWithText("Clases y canales").assertDoesNotExist()
    }

    @Test
    fun managementAdminOpensExistingScopedDestinations() {
        var dashboard = 0
        var schedule = 0
        var classrooms = 0
        compose.setContent {
            CompaneroTheme {
                ProfileScreen(
                    displayName = "QA Management",
                    roles = setOf(UserRole.ADMIN),
                    managementExperience = true,
                    onAdminDashboard = { dashboard++ },
                    onAdminSchedule = { schedule++ },
                    onAdminClassrooms = { classrooms++ },
                    onAppearance = {},
                    onLogout = {},
                )
            }
        }
        compose.onNodeWithText("Perfil de Gestión").assertExists()
        compose.onNodeWithText("Panel administrativo").performScrollTo().performClick()
        compose.onNodeWithText("Horarios y grupos").performScrollTo().performClick()
        compose.onNodeWithText("Clases y canales").performScrollTo().performClick()
        assertEquals(1, dashboard)
        assertEquals(1, schedule)
        assertEquals(1, classrooms)
    }

    @Test
    fun studentModeCannotExposeManagementShortcutsEvenWithCallbacks() {
        compose.setContent {
            CompaneroTheme {
                ProfileScreen(
                    displayName = "QA Student",
                    roles = setOf(UserRole.STUDENT),
                    managementExperience = true,
                    onAdminDashboard = {},
                    onAdminSchedule = {},
                    onAdminClassrooms = {},
                    onAppearance = {},
                    onLogout = {},
                )
            }
        }
        compose.onNodeWithText("Panel administrativo").assertDoesNotExist()
        compose.onNodeWithText("Horarios y grupos").assertDoesNotExist()
        compose.onNodeWithText("Clases y canales").assertDoesNotExist()
    }

    @Test
    fun adminRoleAloneDoesNotGrantManagementUIInAcademicApp() {
        compose.setContent {
            CompaneroTheme {
                ProfileScreen(
                    displayName = "QA Admin",
                    roles = setOf(UserRole.SUPER_ADMIN),
                    managementExperience = false,
                    onAdminDashboard = {},
                    onAdminSchedule = {},
                    onAdminClassrooms = {},
                    onAppearance = {},
                    onLogout = {},
                )
            }
        }
        compose.onNodeWithText("Panel administrativo").assertDoesNotExist()
        compose.onNodeWithText("Horarios y grupos").assertDoesNotExist()
    }
}
