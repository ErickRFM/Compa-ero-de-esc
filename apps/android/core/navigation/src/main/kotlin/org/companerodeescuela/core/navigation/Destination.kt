package org.companerodeescuela.core.navigation

/**
 * Every destination in the app, as data.
 */
sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Schedule : Destination("schedule")
    data object Profile : Destination("profile")

    fun createRoute(vararg arguments: Any): String =
        if (arguments.isEmpty()) route
        else route + arguments.joinToString(separator = "/", prefix = "/")
}

/**
 * Only high-frequency student destinations belong in the persistent bottom
 * navigation. Profile is deliberately secondary and is opened from the app bar.
 */
enum class TopLevelDestination(
    val destination: Destination,
    val label: String,
) {
    Home(Destination.Home, "Hoy"),
    Schedule(Destination.Schedule, "Agenda"),
}
