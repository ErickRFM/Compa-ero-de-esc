package org.companerodeescuela.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import org.companerodeescuela.shared.contracts.UserRole

class RoleExperienceResolverTest {
    @Test
    fun `student gets student shell`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.STUDENT))

        assertEquals(AppExperience.STUDENT, config.experience)
        assertEquals(Destination.Home, config.startDestination)
        assertEquals(
            listOf(
                TopLevelDestination.Home,
                TopLevelDestination.Schedule,
                TopLevelDestination.Classrooms,
            ),
            config.topLevelDestinations,
        )
    }

    @Test
    fun `teacher gets full teacher workspace`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.TEACHER))

        assertEquals(AppExperience.TEACHER, config.experience)
        assertEquals(Destination.TeacherHome, config.startDestination)
        assertEquals(
            listOf(
                TopLevelDestination.TeacherHome,
                TopLevelDestination.Classrooms,
                TopLevelDestination.Schedule,
            ),
            config.topLevelDestinations,
        )
    }

    @Test
    fun `coordinator gets coordinator workspace`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.COORDINATOR))

        assertEquals(AppExperience.COORDINATOR, config.experience)
        assertEquals(Destination.CoordinatorHome, config.startDestination)
        assertEquals(
            listOf(
                TopLevelDestination.CoordinatorHome,
                TopLevelDestination.Schedule,
                TopLevelDestination.Attendance,
                TopLevelDestination.Channel,
            ),
            config.topLevelDestinations,
        )
    }

    @Test
    fun `admin and superAdmin get admin workspace`() {
        val admin = RoleExperienceResolver.resolve(setOf(UserRole.ADMIN))
        val superAdmin = RoleExperienceResolver.resolve(setOf(UserRole.SUPER_ADMIN))

        assertEquals(AppExperience.ADMIN, admin.experience)
        assertEquals(Destination.AdminHome, admin.startDestination)

        assertEquals(AppExperience.SUPER_ADMIN, superAdmin.experience)
        assertEquals(Destination.AdminHome, superAdmin.startDestination)
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
    fun `no role resolves to unsupported experience`() {
        val config = RoleExperienceResolver.resolve(emptySet())

        assertEquals(AppExperience.UNSUPPORTED, config.experience)
        assertEquals(Destination.RoleUnavailable, config.startDestination)
    }
}
