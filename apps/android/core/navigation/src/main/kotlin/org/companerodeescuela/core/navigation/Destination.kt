package org.companerodeescuela.core.navigation

/**
 * Every destination in the app, as data.
 *
 * Routes are declared here rather than as string literals inside each
 * feature, so a typo is a compile error and a rename is a single edit.
 * Feature modules contribute their own destinations and register them; the
 * foundation only owns the ones it actually renders.
 */
sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Profile : Destination("profile")

    /** Builds the route for a destination that takes an argument. */
    fun createRoute(vararg arguments: Any): String =
        if (arguments.isEmpty()) route
        else route + arguments.joinToString(separator = "/", prefix = "/")
}

/**
 * Graph destinations, used to build the top-level navigation bar.
 *
 * Kept separate from [Destination] because "where can I go" and "what does
 * this screen look like" change for different reasons.
 */
enum class TopLevelDestination(
    val destination: Destination,
    val label: String,
) {
    Home(Destination.Home, "Inicio"),
    Profile(Destination.Profile, "Perfil"),
}
