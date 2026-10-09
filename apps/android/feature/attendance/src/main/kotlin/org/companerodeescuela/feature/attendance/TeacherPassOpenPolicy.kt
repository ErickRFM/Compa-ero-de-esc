package org.companerodeescuela.feature.attendance

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.companerodeescuela.core.common.result.AppError
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import org.companerodeescuela.shared.contracts.ClassOccurrenceStatusContract

/**
 * Defensive client guidance only. The Ktor API remains authoritative for
 * opening a session, teacher ownership and concurrency.
 */
internal enum class TeacherPassOpenStatus {
    AVAILABLE,
    ENDED,
    CANCELLED,
    INVALID_SCHEDULE,
}

internal fun teacherPassOpenStatus(
    occurrence: ClassOccurrenceContract,
    now: LocalDateTime,
): TeacherPassOpenStatus {
    if (occurrence.status == ClassOccurrenceStatusContract.CANCELLED) {
        return TeacherPassOpenStatus.CANCELLED
    }

    return runCatching {
        val date = LocalDate.parse(occurrence.date)
        val starts = LocalTime.parse(occurrence.startsAt)
        val ends = LocalTime.parse(occurrence.endsAt)
        if (starts == ends) return TeacherPassOpenStatus.INVALID_SCHEDULE
        val endDateTime = LocalDateTime.of(date, ends).let {
            if (ends < starts) it.plusDays(1) else it
        }
        if (now >= endDateTime) TeacherPassOpenStatus.ENDED
        else TeacherPassOpenStatus.AVAILABLE
    }.getOrDefault(TeacherPassOpenStatus.INVALID_SCHEDULE)
}

internal fun teacherPassUnavailableMessage(status: TeacherPassOpenStatus): String = when (status) {
    TeacherPassOpenStatus.AVAILABLE -> ""
    TeacherPassOpenStatus.ENDED ->
        "La clase ya terminó. No se puede iniciar un pase nuevo desde esta vista."
    TeacherPassOpenStatus.CANCELLED ->
        "Esta clase está cancelada. No se puede abrir su pase."
    TeacherPassOpenStatus.INVALID_SCHEDULE ->
        "El horario de esta clase necesita revisión antes de abrir asistencia."
}

/**
 * Opening a class pass is not the same operation as refreshing a roster.
 * Avoid displaying the generic HTTP 409 text as a failed refresh.
 */
internal fun teacherPassOpenError(error: AppError): String =
    if (error is AppError.Http && error.status == 409) {
        "No se pudo abrir el pase. Puede existir una sesión cerrada, " +
            "una sesión de otro docente o una clase cancelada. " +
            "Actualiza asistencia y verifica el estado de la clase."
    } else {
        error.userMessage
    }
