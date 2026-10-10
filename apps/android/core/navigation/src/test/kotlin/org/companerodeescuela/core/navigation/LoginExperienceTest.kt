package org.companerodeescuela.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.companerodeescuela.shared.contracts.UserRole

class LoginExperienceTest {
    @Test fun requestedAccessNeverGrantsAnUnauthorizedRole() {
        assertNull(RoleExperienceResolver.preferredAfterLogin(setOf(UserRole.STUDENT), AppExperience.ADMIN, AppExperience.STUDENT))
    }
    @Test fun requestedExperienceWinsOnlyWhenGranted() {
        assertEquals(AppExperience.TUTOR, RoleExperienceResolver.preferredAfterLogin(setOf(UserRole.TEACHER, UserRole.TUTOR), AppExperience.TUTOR, AppExperience.TEACHER))
    }
    @Test fun savedExperienceMustStillBeAuthorized() {
        assertEquals(AppExperience.TEACHER, RoleExperienceResolver.preferredAfterLogin(setOf(UserRole.TEACHER), null, AppExperience.ADMIN))
    }
    @Test fun coordinationRemainsAnExplicitAuthorizedChoice() {
        assertNull(RoleExperienceResolver.preferredAfterLogin(setOf(UserRole.COORDINATOR), AppExperience.ADMIN, null))
        assertEquals(AppExperience.COORDINATOR, RoleExperienceResolver.preferredAfterLogin(setOf(UserRole.COORDINATOR), null, null))
    }
    @Test fun multiroleRestorationWithoutPreferenceRequiresChoice() {
        assertNull(RoleExperienceResolver.preferredAfterLogin(setOf(UserRole.TEACHER, UserRole.TUTOR), null, null))
    }
    @Test fun pendingTeacherDoesNotGainTeacherDestinations() {
        val preferred = RoleExperienceResolver.preferredAfterLogin(setOf(UserRole.TEACHER_PENDING), AppExperience.TEACHER, null)
        assertEquals(listOf(TopLevelDestination.TeacherHome), RoleExperienceResolver.resolve(setOf(UserRole.TEACHER_PENDING), preferred).topLevelDestinations)
    }
}
