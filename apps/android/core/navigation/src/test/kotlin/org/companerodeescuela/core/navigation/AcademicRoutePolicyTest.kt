package org.companerodeescuela.core.navigation

import org.companerodeescuela.shared.contracts.UserRole
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AcademicRoutePolicyTest {
    @Test
    fun `mixed roles cannot expose student actions in another active experience`() {
        val roles = setOf(UserRole.STUDENT, UserRole.TEACHER, UserRole.TUTOR, UserRole.ADMIN)
        assertTrue(AcademicRoutePolicy.studentActions(AppExperience.STUDENT, roles))
        for (experience in AppExperience.entries.filter { it != AppExperience.STUDENT }) {
            assertFalse(AcademicRoutePolicy.studentActions(experience, roles))
        }
    }

    @Test
    fun `channel notification ignores guests and teachers awaiting approval`() {
        assertTrue(
            AcademicRoutePolicy.channelExperience(
                AppExperience.GUEST,
                setOf(UserRole.GUEST),
                AppDistribution.ACADEMIC,
            ) == null,
        )
        assertTrue(
            AcademicRoutePolicy.channelExperience(
                AppExperience.TEACHER,
                setOf(UserRole.TEACHER_PENDING),
                AppDistribution.ACADEMIC,
            ) == null,
        )
    }

    @Test
    fun `channel notification respects management distribution and switches only to granted role`() {
        val roles = setOf(UserRole.TEACHER, UserRole.ADMIN, UserRole.TUTOR)
        assertTrue(
            AcademicRoutePolicy.channelExperience(
                AppExperience.ADMIN,
                roles,
                AppDistribution.MANAGEMENT,
            ) == AppExperience.ADMIN,
        )
        assertTrue(
            AcademicRoutePolicy.channelExperience(
                AppExperience.TUTOR,
                roles,
                AppDistribution.ACADEMIC,
            ) == AppExperience.TEACHER,
        )
        assertTrue(
            AcademicRoutePolicy.channelExperience(
                AppExperience.GUEST,
                setOf(UserRole.TEACHER),
                AppDistribution.MANAGEMENT,
            ) == null,
        )
    }

    @Test
    fun `academic teaching routes require both active teacher experience and approved teacher role`() {
        assertTrue(AcademicRoutePolicy.teacherActions(AppExperience.TEACHER, setOf(UserRole.TEACHER)))
        assertFalse(AcademicRoutePolicy.teacherActions(AppExperience.TEACHER, setOf(UserRole.TEACHER_PENDING)))
        assertFalse(AcademicRoutePolicy.teacherActions(AppExperience.ADMIN, setOf(UserRole.TEACHER, UserRole.ADMIN)))
        assertFalse(AcademicRoutePolicy.studentActions(AppExperience.STUDENT, setOf(UserRole.TUTOR)))
    }
}
