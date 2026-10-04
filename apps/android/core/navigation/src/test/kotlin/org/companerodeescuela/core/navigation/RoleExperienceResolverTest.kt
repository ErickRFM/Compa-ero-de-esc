package org.companerodeescuela.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.companerodeescuela.shared.contracts.UserRole

class RoleExperienceResolverTest {
    @Test
    fun `student gets student shell including class channel`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.STUDENT))

        assertEquals(AppExperience.STUDENT, config.experience)
        assertEquals(Destination.Home, config.startDestination)
        assertEquals(
            listOf(
                TopLevelDestination.Home,
                TopLevelDestination.Schedule,
                TopLevelDestination.Channel,
                TopLevelDestination.Attendance,
            ),
            config.topLevelDestinations,
        )
    }

    @Test
    fun `teacher gets channel and attendance workspace`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.TEACHER))

        assertEquals(AppExperience.TEACHER, config.experience)
        assertEquals(Destination.Channel, config.startDestination)
        assertEquals(
            listOf(TopLevelDestination.Channel, TopLevelDestination.Attendance),
            config.topLevelDestinations,
        )
    }

    @Test
    fun `student teacher account can resolve either granted experience`() {
        val roles = setOf(UserRole.STUDENT, UserRole.TEACHER)

        assertEquals(AppExperience.STUDENT, RoleExperienceResolver.resolve(roles).experience)
        assertEquals(
            AppExperience.TEACHER,
            RoleExperienceResolver.resolve(
                roles,
                preferredExperience = AppExperience.TEACHER,
            ).experience,
        )
    }

    @Test
    fun `ungranted preferred experience is ignored`() {
        val config = RoleExperienceResolver.resolve(
            roles = setOf(UserRole.STUDENT),
            preferredExperience = AppExperience.ADMIN,
        )

        assertEquals(AppExperience.STUDENT, config.experience)
    }

    @Test
    fun `administrative roles never fall through to student shell`() {
        val coordinator = RoleExperienceResolver.resolve(setOf(UserRole.COORDINATOR))
        val admin = RoleExperienceResolver.resolve(setOf(UserRole.ADMIN))
        val superAdmin = RoleExperienceResolver.resolve(setOf(UserRole.SUPER_ADMIN))

        listOf(coordinator, admin, superAdmin).forEach { config ->
            assertEquals(Destination.RoleUnavailable, config.startDestination)
            assertTrue(config.topLevelDestinations.isEmpty())
        }
    }
}
