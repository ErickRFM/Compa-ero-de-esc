package org.companerodeescuela.core.navigation

import org.companerodeescuela.shared.contracts.UserRole

/**
 * What the active experience may *show* in this APK.
 *
 * This is the single client-side source for route and action gating. It is a
 * presentation boundary, never an authorization grant: the API re-verifies live
 * identity, capability, institution and resource scope on every request.
 * Screens and the navigation graph read these flags instead of comparing
 * experiences and roles ad hoc.
 */
data class ExperienceAccess(
    val experience: AppExperience,
    val distribution: AppDistribution,
    /** Student self-service: own attendance QR, classes, grades and representatives. */
    val studentTools: Boolean,
    /** Approved teacher tools: roll call, classrooms, channel publishing and gradebook. */
    val teacherTools: Boolean,
    /** Teacher experience shown while the account still awaits staff approval. */
    val teacherPending: Boolean,
    /** Tutoring of groups assigned by administration. */
    val tutorTools: Boolean,
    /** Coordination and direction supervision console. */
    val supervision: Boolean,
    /** Account directory, schedule/classroom editor (ADMIN and SUPER_ADMIN experiences). */
    val academicAdministration: Boolean,
    /** Super Admin consoles and one-time invitation issuing (Management APK only). */
    val superAdministration: Boolean,
    /** Administrative accounts see institutional copy instead of class channels. */
    val administrativeChannelSession: Boolean,
    /** Accepting an invitation requires a verified e-mail. */
    val emailVerified: Boolean,
) {
    /** Admin shortcuts on Perfil belong to the Management APK only. */
    val administrativeProfileShortcuts: Boolean
        get() = academicAdministration && distribution == AppDistribution.MANAGEMENT

    /** Teacher may request a tutor assignment only while not already a tutor. */
    fun canRequestTutorRole(roles: Set<UserRole>): Boolean =
        teacherTools && distribution != AppDistribution.MANAGEMENT && UserRole.TUTOR !in roles

    fun canOpen(destination: Destination): Boolean =
        when (destination) {
            Destination.Home,
            Destination.RepresentativeInvitations,
            -> studentTools
            Destination.TeacherHome -> teacherTools || teacherPending
            Destination.TutorHome,
            Destination.TutorGroups,
            Destination.TutorRequests,
            -> tutorTools
            Destination.CoordinatorHome -> supervision
            Destination.AdminHome -> academicAdministration
            Destination.AdminAccounts,
            Destination.AdminAcademic,
            Destination.AdminHistory,
            Destination.InvitationIssue,
            -> superAdministration
            Destination.Schedule ->
                studentTools || teacherTools || tutorTools || supervision || academicAdministration
            Destination.Classrooms -> studentTools || teacherTools || academicAdministration
            Destination.Grading,
            Destination.Attendance,
            -> studentTools || teacherTools
            Destination.Channel -> studentTools || teacherTools || administrativeChannelSession
            Destination.InvitationAcceptance -> emailVerified
            Destination.RoleUnavailable,
            Destination.Profile,
            Destination.Mfa,
            Destination.StaffApplication,
            Destination.AppearanceSettings,
            Destination.IntegrationSettings,
            Destination.DesignSystemCatalog,
            -> true
        }

    companion object {
        fun of(
            experience: AppExperience,
            roles: Set<UserRole>,
            distribution: AppDistribution,
            emailVerified: Boolean = false,
        ): ExperienceAccess {
            val granted = experience in RoleExperienceResolver.available(roles, distribution)

            fun active(
                target: AppExperience,
                role: UserRole,
            ) = granted && experience == target && role in roles
            val admin = active(AppExperience.ADMIN, UserRole.ADMIN)
            val superAdmin = active(AppExperience.SUPER_ADMIN, UserRole.SUPER_ADMIN)
            val supervision =
                active(AppExperience.COORDINATOR, UserRole.COORDINATOR) ||
                    active(AppExperience.PRINCIPAL, UserRole.PRINCIPAL)
            val teacher = active(AppExperience.TEACHER, UserRole.TEACHER)
            return ExperienceAccess(
                experience = experience,
                distribution = distribution,
                studentTools = active(AppExperience.STUDENT, UserRole.STUDENT),
                teacherTools = teacher,
                teacherPending = !teacher && active(AppExperience.TEACHER, UserRole.TEACHER_PENDING),
                tutorTools = active(AppExperience.TUTOR, UserRole.TUTOR),
                supervision = supervision,
                academicAdministration = admin || superAdmin,
                superAdministration = superAdmin && distribution == AppDistribution.MANAGEMENT,
                administrativeChannelSession = admin || superAdmin || supervision,
                emailVerified = emailVerified,
            )
        }
    }
}
