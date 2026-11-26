package org.companerodeescuela.core.navigation

import org.companerodeescuela.shared.contracts.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExperienceAccessTest {
    private val singleRoleAccounts =
        UserRole.entries.map { setOf(it) } +
            listOf(
                setOf(UserRole.STUDENT, UserRole.TEACHER),
                setOf(UserRole.TEACHER, UserRole.TUTOR),
                setOf(UserRole.ADMIN, UserRole.SUPER_ADMIN),
                setOf(UserRole.COORDINATOR, UserRole.PRINCIPAL),
            )

    @Test
    fun `every resolved shell can open its own start and tab destinations`() {
        for (distribution in listOf(AppDistribution.ACADEMIC, AppDistribution.MANAGEMENT)) {
            for (roles in singleRoleAccounts) {
                for (experience in RoleExperienceResolver.available(roles, distribution)) {
                    val config = RoleExperienceResolver.resolve(roles, experience, distribution)
                    val access = ExperienceAccess.of(config.experience, roles, distribution)
                    val owned = listOf(config.startDestination) + config.topLevelDestinations.map { it.destination }
                    owned.forEach { destination ->
                        assertTrue(
                            access.canOpen(destination),
                            "$roles as $experience in $distribution cannot open ${destination.route}",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `pending teacher sees the waiting home but no teacher tools`() {
        val roles = setOf(UserRole.TEACHER_PENDING)
        val access = ExperienceAccess.of(AppExperience.TEACHER, roles, AppDistribution.ACADEMIC)

        assertTrue(access.teacherPending)
        assertFalse(access.teacherTools)
        assertTrue(access.canOpen(Destination.TeacherHome))
        listOf(
            Destination.Classrooms,
            Destination.Attendance,
            Destination.Grading,
            Destination.Channel,
            Destination.Schedule,
        ).forEach { assertFalse(access.canOpen(it), it.route) }
        assertFalse(access.canRequestTutorRole(roles))
    }

    @Test
    fun `an experience the roles do not grant opens nothing privileged`() {
        val access = ExperienceAccess.of(AppExperience.SUPER_ADMIN, setOf(UserRole.ADMIN), AppDistribution.MANAGEMENT)

        assertFalse(access.academicAdministration)
        assertFalse(access.superAdministration)
        assertFalse(access.canOpen(Destination.AdminHome))
        assertFalse(access.canOpen(Destination.InvitationIssue))
    }

    @Test
    fun `academic APK never exposes management consoles`() {
        val roles = setOf(UserRole.STUDENT, UserRole.SUPER_ADMIN)
        val access = ExperienceAccess.of(AppExperience.SUPER_ADMIN, roles, AppDistribution.ACADEMIC)

        assertFalse(access.superAdministration)
        assertFalse(access.canOpen(Destination.AdminAccounts))
        assertFalse(access.canOpen(Destination.AdminHome))
    }

    @Test
    fun `admin and super admin split on invitation and audit consoles`() {
        val admin = ExperienceAccess.of(AppExperience.ADMIN, setOf(UserRole.ADMIN), AppDistribution.MANAGEMENT)
        val owner =
            ExperienceAccess.of(AppExperience.SUPER_ADMIN, setOf(UserRole.SUPER_ADMIN), AppDistribution.MANAGEMENT)

        assertTrue(admin.canOpen(Destination.AdminHome))
        assertTrue(admin.administrativeProfileShortcuts)
        assertFalse(admin.canOpen(Destination.InvitationIssue))
        assertFalse(admin.canOpen(Destination.AdminHistory))
        listOf(Destination.InvitationIssue, Destination.AdminAccounts, Destination.AdminAcademic, Destination.AdminHistory)
            .forEach { assertTrue(owner.canOpen(it), it.route) }
    }

    @Test
    fun `supervision is read-only and cannot reach the academic editor`() {
        val access = ExperienceAccess.of(AppExperience.PRINCIPAL, setOf(UserRole.PRINCIPAL), AppDistribution.MANAGEMENT)

        assertTrue(access.supervision)
        assertTrue(access.administrativeChannelSession)
        assertFalse(access.academicAdministration)
        assertFalse(access.canOpen(Destination.AdminHome))
        assertFalse(access.canOpen(Destination.Classrooms))
    }

    @Test
    fun `tutor reaches only tutoring and shared settings`() {
        val access = ExperienceAccess.of(AppExperience.TUTOR, setOf(UserRole.TUTOR), AppDistribution.ACADEMIC)

        assertTrue(access.canOpen(Destination.TutorGroups))
        assertFalse(access.canOpen(Destination.Attendance))
        assertFalse(access.canOpen(Destination.Channel))
        assertFalse(access.canOpen(Destination.AdminHome))
    }

    @Test
    fun `guest keeps account settings only`() {
        val access = ExperienceAccess.of(AppExperience.GUEST, setOf(UserRole.GUEST), AppDistribution.ACADEMIC)

        assertTrue(access.canOpen(Destination.Profile))
        assertTrue(access.canOpen(Destination.AppearanceSettings))
        assertFalse(access.canOpen(Destination.Schedule))
        assertFalse(access.canOpen(Destination.Home))
        assertFalse(access.canOpen(Destination.InvitationAcceptance))
        assertTrue(
            ExperienceAccess
                .of(AppExperience.GUEST, setOf(UserRole.GUEST), AppDistribution.ACADEMIC, emailVerified = true)
                .canOpen(Destination.InvitationAcceptance),
        )
    }

    @Test
    fun `teacher may request tutoring only once and only in the academic app`() {
        val teacher = setOf(UserRole.TEACHER)
        val tutorTeacher = setOf(UserRole.TEACHER, UserRole.TUTOR)

        assertTrue(ExperienceAccess.of(AppExperience.TEACHER, teacher, AppDistribution.ACADEMIC).canRequestTutorRole(teacher))
        assertFalse(
            ExperienceAccess.of(AppExperience.TEACHER, tutorTeacher, AppDistribution.ACADEMIC).canRequestTutorRole(tutorTeacher),
        )
    }

    @Test
    fun `profile labels share one vocabulary`() {
        assertEquals("Docente pendiente", ExperiencePresentation.experienceName(AppExperience.TEACHER, setOf(UserRole.TEACHER_PENDING)))
        assertEquals("Tutoría", ExperiencePresentation.experienceName(AppExperience.TUTOR, setOf(UserRole.TUTOR)))
        assertEquals("Estudiante · Docente", ExperiencePresentation.roleSummary(setOf(UserRole.TEACHER, UserRole.STUDENT)))
        assertEquals("Rol no disponible", ExperiencePresentation.roleSummary(emptySet()))
    }

    @Test
    fun `every profile has a scope explanation`() {
        AppExperience.entries.forEach { experience ->
            val roles = UserRole.entries.toSet()
            val summary = ExperiencePresentation.scopeSummary(ExperienceAccess.of(experience, roles, AppDistribution.LEGACY))
            assertTrue(summary.isNotBlank(), experience.name)
        }
    }
}
