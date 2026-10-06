package org.companerodeescuela.core.navigation

/**
 * Every destination in the app, as data.
 */
sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object TeacherHome : Destination("teacher-home")
    data object CoordinatorHome : Destination("coordinator-home")
    data object AdminHome : Destination("admin-home")
    data object Schedule : Destination("schedule")
    data object Attendance : Destination("attendance")
    data object Channel : Destination("channel")
    data object Classrooms : Destination("classrooms")
    data object RoleUnavailable : Destination("role-unavailable")
    data object Profile : Destination("profile")
    data object AppearanceSettings : Destination("appearance-settings")
    data object DesignSystemCatalog : Destination("design-system-catalog")

    fun createRoute(vararg arguments: Any): String =
        if (arguments.isEmpty()) route
        else route + arguments.joinToString(separator = "/", prefix = "/")
}

/**
 * High-frequency destinations are selected per role experience by RoleExperienceResolver.
 */
enum class TopLevelDestination(
    val destination: Destination,
    val label: String,
) {
    Home(Destination.Home, "Hoy"),
    TeacherHome(Destination.TeacherHome, "Hoy"),
    CoordinatorHome(Destination.CoordinatorHome, "Gestión"),
    AdminHome(Destination.AdminHome, "Administración"),
    Schedule(Destination.Schedule, "Agenda"),
    Classrooms(Destination.Classrooms, "Clases"),
    Channel(Destination.Channel, "Canal"),
    Attendance(Destination.Attendance, "Asistencia"),
}
