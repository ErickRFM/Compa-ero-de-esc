package org.companerodeescuela.core.navigation

/**
 * Every destination in the app, as data.
 */
sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Schedule : Destination("schedule")
    data object Attendance : Destination("attendance")
    data object Channel : Destination("channel")
    data object RoleUnavailable : Destination("role-unavailable")
    data object Profile : Destination("profile")
    data object AppearanceSettings : Destination("appearance-settings")
    data object DesignSystemCatalog : Destination("design-system-catalog")

    fun createRoute(vararg arguments: Any): String =
        if (arguments.isEmpty()) route
        else route + arguments.joinToString(separator = "/", prefix = "/")
}

/**
 * Only high-frequency student destinations belong in persistent navigation.
 */
enum class TopLevelDestination(
    val destination: Destination,
    val label: String,
) {
    Home(Destination.Home, "Hoy"),
    Schedule(Destination.Schedule, "Agenda"),
    Channel(Destination.Channel, "Canal"),
    Attendance(Destination.Attendance, "Asistencia"),
}
