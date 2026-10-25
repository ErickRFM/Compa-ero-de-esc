package org.companerodeescuela.feature.schedule

/** Keep the user's preferred day across refreshes; choose a visible fallback only for rendering. */
internal fun resolveScheduleDay(
    preferredDay: String,
    availableDays: List<String>,
): String =
    preferredDay.takeIf(availableDays::contains)
        ?: availableDays.firstOrNull()
        ?: "MONDAY"
