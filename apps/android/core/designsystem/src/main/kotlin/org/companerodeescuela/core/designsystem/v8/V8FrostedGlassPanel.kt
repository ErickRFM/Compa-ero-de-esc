@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast

/**
 * One shared V9 surface with three deliberately different visual treatments:
 * normal dark is opaque matte, dark high contrast uses an opaque black
 * foundation and a solid outline, and light is clean
 * white with ink text. High-contrast light remains opaque and sharply outlined.
 */
internal enum class V8SurfaceFinish {
    DARK_MATTE,
    DARK_HIGH_CONTRAST,
    LIGHT_CLEAN,
    LIGHT_HIGH_CONTRAST,
}

internal fun v8SurfaceFinish(
    light: Boolean,
    highContrast: Boolean,
): V8SurfaceFinish =
    when {
        light && highContrast -> V8SurfaceFinish.LIGHT_HIGH_CONTRAST
        light -> V8SurfaceFinish.LIGHT_CLEAN
        highContrast -> V8SurfaceFinish.DARK_HIGH_CONTRAST
        else -> V8SurfaceFinish.DARK_MATTE
    }

@Composable
fun Modifier.v8GlassSurface(
    cornerRadius: Dp = V8RedDimensions.CardCorner,
    emphasized: Boolean = false,
    elevation: Dp = 6.dp,
    themeAware: Boolean = true,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val light = themeAware && MaterialTheme.colorScheme.background.luminance() > 0.5f
    val finish = v8SurfaceFinish(light, LocalCompaneroHighContrast.current)
    val scheme = MaterialTheme.colorScheme
    val fill =
        when (finish) {
            V8SurfaceFinish.LIGHT_CLEAN -> if (emphasized) Color(0xFFF2F4F8) else Color.White
            V8SurfaceFinish.LIGHT_HIGH_CONTRAST -> Color.White
            V8SurfaceFinish.DARK_MATTE -> if (emphasized) Color(0xFF20232B) else Color(0xFF17191F)
            V8SurfaceFinish.DARK_HIGH_CONTRAST -> Color(0xFF090A0D)
        }
    val edge =
        when (finish) {
            V8SurfaceFinish.LIGHT_CLEAN -> if (emphasized) Color(0xFFBFC5CF) else Color(0xFFDEE2E9)
            V8SurfaceFinish.LIGHT_HIGH_CONTRAST -> scheme.onSurface
            V8SurfaceFinish.DARK_MATTE -> if (emphasized) Color(0xFF414650) else Color(0xFF30343D)
            V8SurfaceFinish.DARK_HIGH_CONTRAST -> scheme.onSurface
        }
    val highContrast = finish == V8SurfaceFinish.DARK_HIGH_CONTRAST || finish == V8SurfaceFinish.LIGHT_HIGH_CONTRAST
    return shadow(
        elevation = if (highContrast) 0.dp else elevation.coerceIn(0.dp, if (light) 2.dp else 1.dp),
        shape = shape,
        ambientColor = Color.Black,
        spotColor = Color.Black,
    ).clip(shape).background(fill).then(
        when {
            highContrast -> Modifier.border(2.dp, edge, shape)
            emphasized -> Modifier.border(1.dp, scheme.primary.copy(alpha = 0.28f), shape)
            else -> Modifier
        },
    )
}

@Composable
fun V8FrostedGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = V8RedDimensions.CardCorner,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    emphasized: Boolean = false,
    themeAware: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(
        LocalContentColor provides if (themeAware) MaterialTheme.colorScheme.onSurface else V8RedColors.TextPrimary,
    ) {
        Box(
            modifier = modifier.v8GlassSurface(cornerRadius, emphasized, themeAware = themeAware).padding(contentPadding),
            content = content,
        )
    }
}
