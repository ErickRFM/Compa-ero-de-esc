package org.companerodeescuela.core.navigation

import org.companerodeescuela.shared.contracts.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals

/** The APK selector must not leak an experience from the other distribution. */
class AppDistributionRoleResolverTest {
    @Test
    fun `academic distribution allows restricted guest but not admin`() {
        assertEquals(true, AppDistribution.ACADEMIC.allows(AppExperience.GUEST))
        assertEquals(false, AppDistribution.ACADEMIC.allows(AppExperience.ADMIN))
    }

    @Test
    fun `direction is available only in management and never grants another experience`() {
        val roles = setOf(UserRole.PRINCIPAL)
        assertEquals(emptyList(), RoleExperienceResolver.available(roles, AppDistribution.ACADEMIC))
        assertEquals(emptyList(), RoleExperienceResolver.available(roles, AppDistribution.LEGACY))
        assertEquals(listOf(AppExperience.PRINCIPAL), RoleExperienceResolver.available(roles, AppDistribution.MANAGEMENT))
        val config = RoleExperienceResolver.resolve(roles, distribution = AppDistribution.MANAGEMENT)
        assertEquals(AppExperience.PRINCIPAL, config.experience)
        assertEquals(Destination.CoordinatorHome, config.startDestination)
        assertEquals(listOf(TopLevelDestination.CoordinatorHome, TopLevelDestination.Schedule), config.topLevelDestinations)
    }

    @Test
    fun `academic includes only student teacher and tutor among mixed roles`() {
        val roles =
            setOf(
                UserRole.STUDENT,
                UserRole.TEACHER,
                UserRole.TUTOR,
                UserRole.COORDINATOR,
                UserRole.ADMIN,
                UserRole.SUPER_ADMIN,
            )
        assertEquals(
            listOf(AppExperience.STUDENT, AppExperience.TEACHER, AppExperience.TUTOR),
            RoleExperienceResolver.available(roles, AppDistribution.ACADEMIC),
        )
    }

    @Test
    fun `management includes only coordinator admin and super admin`() {
        val roles =
            setOf(
                UserRole.STUDENT,
                UserRole.TEACHER,
                UserRole.TUTOR,
                UserRole.COORDINATOR,
                UserRole.ADMIN,
                UserRole.SUPER_ADMIN,
            )
        assertEquals(
            listOf(AppExperience.COORDINATOR, AppExperience.ADMIN, AppExperience.SUPER_ADMIN),
            RoleExperienceResolver.available(roles, AppDistribution.MANAGEMENT),
        )
    }

    @Test
    fun `management account cannot enter academic app as an admin`() {
        val config =
            RoleExperienceResolver.resolve(
                roles = setOf(UserRole.ADMIN),
                preferredExperience = AppExperience.ADMIN,
                distribution = AppDistribution.ACADEMIC,
            )
        assertEquals(AppExperience.UNSUPPORTED, config.experience)
        assertEquals(Destination.RoleUnavailable, config.startDestination)
    }

    @Test
    fun `student account cannot enter management app`() {
        val config =
            RoleExperienceResolver.resolve(
                roles = setOf(UserRole.STUDENT),
                distribution = AppDistribution.MANAGEMENT,
            )
        assertEquals(AppExperience.UNSUPPORTED, config.experience)
    }

    @Test
    fun `saved management choice cannot cross into academic product`() {
        val roles = setOf(UserRole.STUDENT, UserRole.ADMIN)
        assertEquals(
            AppExperience.STUDENT,
            RoleExperienceResolver.preferredAfterLogin(
                roles = roles,
                requested = null,
                saved = AppExperience.ADMIN,
                distribution = AppDistribution.ACADEMIC,
            ),
        )
        assertEquals(
            AppExperience.STUDENT,
            RoleExperienceResolver
                .resolve(
                    roles = roles,
                    preferredExperience = AppExperience.ADMIN,
                    distribution = AppDistribution.ACADEMIC,
                ).experience,
        )
    }

    @Test
    fun `pending teacher is still restricted within academic product`() {
        val config =
            RoleExperienceResolver.resolve(
                roles = setOf(UserRole.TEACHER_PENDING),
                distribution = AppDistribution.ACADEMIC,
            )
        assertEquals(AppExperience.TEACHER, config.experience)
        assertEquals(listOf(TopLevelDestination.TeacherHome), config.topLevelDestinations)
    }

    @Test
    fun `guest is available only as restricted academic experience`() {
        val roles = setOf(UserRole.GUEST, UserRole.WORKSHOP_PARTICIPANT)
        assertEquals(listOf(AppExperience.GUEST), RoleExperienceResolver.available(roles, AppDistribution.ACADEMIC))
        assertEquals(emptyList(), RoleExperienceResolver.available(roles, AppDistribution.MANAGEMENT))
        assertEquals(listOf(AppExperience.GUEST), RoleExperienceResolver.available(roles, AppDistribution.LEGACY))
    }

    @Test
    fun `existing single APK behavior is preserved until product migration`() {
        val roles = setOf(UserRole.STUDENT, UserRole.ADMIN)
        assertEquals(
            listOf(AppExperience.STUDENT, AppExperience.ADMIN),
            RoleExperienceResolver.available(roles),
        )
        assertEquals(
            AppExperience.ADMIN,
            RoleExperienceResolver.resolve(roles, AppExperience.ADMIN).experience,
        )
    }
}
