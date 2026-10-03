package org.companerodeescuela.core.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class CompaneroMotionPreferences(
    val reducedMotion: Boolean = false,
)

val LocalCompaneroMotionPreferences = staticCompositionLocalOf {
    CompaneroMotionPreferences()
}

/**
 * Centralizes the explicit product-level reduced-motion switch.
 *
 * Platform animator duration scale is still honored by Compose. This value is
 * for product choices such as replacing shared morphs/particles with simpler
 * transitions when the user asks for reduced motion.
 */
@Composable
fun ProvideCompaneroMotionPreferences(
    reducedMotion: Boolean,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalCompaneroMotionPreferences provides CompaneroMotionPreferences(
            reducedMotion = reducedMotion,
        ),
        content = content,
    )
}
