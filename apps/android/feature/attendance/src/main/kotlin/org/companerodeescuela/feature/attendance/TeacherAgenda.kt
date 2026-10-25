package org.companerodeescuela.feature.attendance

import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import java.time.LocalDateTime

/** End time is exclusive; cancelled and malformed occurrences cannot become the next class. */
internal fun upcomingTeacherClasses(
    occurrences: List<ClassOccurrenceContract>,
    now: LocalDateTime,
): List<ClassOccurrenceContract> =
    occurrences
        .filter { teacherPassOpenStatus(it, now) == TeacherPassOpenStatus.AVAILABLE }
        .sortedWith(compareBy({ it.date }, { it.startsAt }))
