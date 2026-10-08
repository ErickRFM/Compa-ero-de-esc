package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Navigation supplies a profile shortcut only for roles without a Profile tab. */
val LocalV8HeaderAction = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/** Keeps page titles and the profile touch target in separate layout space. */
@Composable
fun V8ScreenHeader(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val action = LocalV8HeaderAction.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) { content() }
        action?.invoke()
    }
}
