package org.companerodeescuela.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.CompaneroElevation
import org.companerodeescuela.core.designsystem.v8.LocalV8GlassEnabled
import org.companerodeescuela.core.designsystem.v8.V8FrostedGlassPanel

enum class CompaneroSurfaceRole {
    BASE,
    INSET,
    CARD,
    RAISED,
    FLOATING,
}

@Composable
fun CompaneroSurface(
    modifier: Modifier = Modifier,
    role: CompaneroSurfaceRole = CompaneroSurfaceRole.CARD,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalV8GlassEnabled.current && role != CompaneroSurfaceRole.BASE) {
        V8FrostedGlassPanel(modifier = modifier) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
        return
    }
    val color = when (role) {
        CompaneroSurfaceRole.BASE -> MaterialTheme.colorScheme.surface
        CompaneroSurfaceRole.INSET -> MaterialTheme.colorScheme.surfaceContainerLow
        CompaneroSurfaceRole.CARD -> MaterialTheme.colorScheme.surfaceContainer
        CompaneroSurfaceRole.RAISED -> MaterialTheme.colorScheme.surfaceContainerHigh
        CompaneroSurfaceRole.FLOATING -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val elevation: Dp = when (role) {
        CompaneroSurfaceRole.BASE -> 0.dp
        CompaneroSurfaceRole.INSET -> CompaneroElevation.subtle
        CompaneroSurfaceRole.CARD -> CompaneroElevation.card
        CompaneroSurfaceRole.RAISED -> CompaneroElevation.raised
        CompaneroSurfaceRole.FLOATING -> CompaneroElevation.immersive
    }
    val shape = when (role) {
        CompaneroSurfaceRole.BASE -> MaterialTheme.shapes.small
        CompaneroSurfaceRole.INSET -> MaterialTheme.shapes.medium
        CompaneroSurfaceRole.CARD -> MaterialTheme.shapes.large
        CompaneroSurfaceRole.RAISED,
        CompaneroSurfaceRole.FLOATING,
        -> MaterialTheme.shapes.extraLarge
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        tonalElevation = elevation,
        shadowElevation = elevation,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
fun CompaneroHeroSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalV8GlassEnabled.current) {
        V8FrostedGlassPanel(modifier = modifier, cornerRadius = 26.dp) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
        return
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        tonalElevation = CompaneroElevation.raised,
        shadowElevation = CompaneroElevation.raised,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
fun CompaneroGroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompaneroSurface(
        modifier = modifier.fillMaxWidth(),
        role = CompaneroSurfaceRole.CARD,
        content = content,
    )
}
