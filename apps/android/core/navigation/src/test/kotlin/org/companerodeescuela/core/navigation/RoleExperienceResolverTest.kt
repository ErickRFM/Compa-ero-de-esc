package org.companerodeescuela.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.companerodeescuela.shared.contracts.UserRole

class RoleExperienceResolverTest {
    @Test
    fun `student gets student shell`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.STUDENT))

        assertEquals(AppExperience.STUDENT, config.experience)
        assertEquals(Destination.Home, config.startDestination)
        assertEquals(TopLevelDestination.entries, config.topLevelDestinations)
    }

    @Test
    fun `teacher gets attendance without one-item bottom navigation`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.TEACHER))

        assertEquals(AppExperience.TEACHER, config.experience)
        assertEquals(Destination.Attendance, config.startDestination)
        assertTrue(config.topLevelDestinations.isEmpty())
    }

    @Test
    fun `student teacher account can resolve either granted experience`() {
        val roles = setOf(UserRole.STUDENT, UserRole.TEACHER)

        assertEquals(
            AppExperience.STUDENT,
            RoleExperienceResolver.resolve(roles).experience,
        )
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
        assertEquals(AppExperience.COORDINATOR, coordinator.experience)
        assertEquals(AppExperience.ADMIN, admin.experience)
        assertEquals(AppExperience.SUPER_ADMIN, superAdmin.experience)
    }

    @Test
    fun `no role resolves to unsupported experience`() {
        val config = RoleExperienceResolver.resolve(emptySet())

        assertEquals(AppExperience.UNSUPPORTED, config.experience)
        assertEquals(Destination.RoleUnavailable, config.startDestination)
    }
}
