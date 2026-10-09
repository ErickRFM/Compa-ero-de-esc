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
                TopLevelDestination.Attendance,
                TopLevelDestination.Classrooms,
                TopLevelDestination.Profile,
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
                TopLevelDestination.Schedule,
                TopLevelDestination.Attendance,
                TopLevelDestination.Classrooms,
                TopLevelDestination.Channel,
            ),
            config.topLevelDestinations,
        )
    }

    @Test
    fun `pending teacher does not get QR or channel actions`() {
        val config = RoleExperienceResolver.resolve(setOf(UserRole.TEACHER_PENDING))

        assertEquals(AppExperience.TEACHER, config.experience)
        assertEquals(Destination.TeacherHome, config.startDestination)
        assertEquals(listOf(TopLevelDestination.TeacherHome), config.topLevelDestinations)
    }

    @Test
    fun `tutor-only account gets tutoring workspace`() {
        val result = RoleExperienceResolver.resolve(setOf(UserRole.TUTOR))
        assertEquals(AppExperience.TUTOR, result.experience)
        assertEquals(Destination.TutorHome, result.startDestination)
        assertEquals(
            listOf(TopLevelDestination.TutorHome, TopLevelDestination.TutorGroups, TopLevelDestination.TutorRequests),
            result.topLevelDestinations,
        )
    }

    @Test
    fun `teacher tutor account can switch experiences`() {
        val roles = setOf(UserRole.TEACHER, UserRole.TUTOR)
        assertEquals(listOf(AppExperience.TEACHER, AppExperience.TUTOR), RoleExperienceResolver.available(roles))
        assertEquals(AppExperience.TUTOR, RoleExperienceResolver.resolve(roles, AppExperience.TUTOR).experience)
    }

    @Test
    fun `pending teacher is not automatically tutor`() {
        assertEquals(listOf(AppExperience.TEACHER), RoleExperienceResolver.available(setOf(UserRole.TEACHER_PENDING)))
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
        val expected = listOf(
            TopLevelDestination.AdminHome,
            TopLevelDestination.Schedule,
            TopLevelDestination.Classrooms,
            TopLevelDestination.Channel,
        )
        assertEquals(expected, admin.topLevelDestinations)
        assertEquals(expected, superAdmin.topLevelDestinations)
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
