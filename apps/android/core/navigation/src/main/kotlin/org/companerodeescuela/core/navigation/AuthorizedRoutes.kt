package org.companerodeescuela.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Registers [destination] behind [ExperienceAccess.canOpen]. A route reached by a
 * stale back stack, deep link or notification renders [denied] instead of a
 * screen the active profile does not own.
 */
fun NavGraphBuilder.authorizedComposable(
    destination: Destination,
    access: ExperienceAccess,
    denied: @Composable () -> Unit,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(destination.route) { entry ->
        if (access.canOpen(destination)) content(entry) else denied()
    }
}
