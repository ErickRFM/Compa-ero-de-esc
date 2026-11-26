package org.companerodeescuela.core.navigation

import org.companerodeescuela.shared.contracts.UserRole

/**
 * One vocabulary for profiles across Perfil, access-denied screens and tests.
 * Keep these aligned with the bottom-bar labels in [TopLevelDestination].
 */
object ExperiencePresentation {
    fun experienceName(
        experience: AppExperience,
        roles: Set<UserRole>,
    ): String =
        when (experience) {
            AppExperience.STUDENT -> "Estudiante"
            AppExperience.TEACHER -> if (UserRole.TEACHER in roles) "Docente" else "Docente pendiente"
            AppExperience.TUTOR -> "Tutoría"
            AppExperience.PRINCIPAL -> "Dirección"
            AppExperience.COORDINATOR -> "Coordinación"
            AppExperience.ADMIN -> "Administración"
            AppExperience.SUPER_ADMIN -> "Administración general"
            AppExperience.GUEST -> if (UserRole.WORKSHOP_PARTICIPANT in roles) "Participante" else "Invitado"
            AppExperience.UNSUPPORTED -> "No disponible"
        }

    fun roleName(role: UserRole): String =
        when (role) {
            UserRole.STUDENT -> "Estudiante"
            UserRole.WORKSHOP_PARTICIPANT -> "Participante"
            UserRole.GUEST -> "Invitado"
            UserRole.TEACHER_PENDING -> "Docente pendiente"
            UserRole.TEACHER -> "Docente"
            UserRole.TUTOR -> "Tutoría"
            UserRole.PRINCIPAL -> "Dirección"
            UserRole.COORDINATOR -> "Coordinación"
            UserRole.ADMIN -> "Administración"
            UserRole.SUPER_ADMIN -> "Administración general"
        }

    fun roleSummary(roles: Set<UserRole>): String =
        roles
            .sortedBy(UserRole::ordinal)
            .joinToString(" · ", transform = ::roleName)
            .ifBlank { "Rol no disponible" }

    /**
     * What the active profile can do, in one sentence, so Perfil explains the
     * scope instead of only naming the role.
     */
    fun scopeSummary(access: ExperienceAccess): String =
        when {
            access.superAdministration -> "Cuentas, invitaciones, estructura académica e historial auditado."
            access.academicAdministration -> "Directorio, aprobaciones, horarios y clases de tu institución."
            access.supervision -> "Supervisión de pases, revisiones y cobertura horaria."
            access.tutorTools -> "Seguimiento y justificantes de los grupos que tienes asignados."
            access.teacherTools -> "Pase de lista, clases, canal, tareas y calificaciones de tus grupos."
            access.teacherPending ->
                "Control escolar debe validar tu perfil para habilitar pase de lista, clases y calificaciones."
            access.studentTools -> "Tu horario, asistencia, clases, tareas y calificaciones."
            access.experience == AppExperience.GUEST -> "Ajustes de cuenta y accesibilidad; sin datos escolares."
            else -> "Esta aplicación no tiene herramientas para tu cuenta."
        }
}
