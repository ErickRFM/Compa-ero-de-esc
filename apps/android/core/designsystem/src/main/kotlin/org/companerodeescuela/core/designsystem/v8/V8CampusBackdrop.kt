package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.R

/**
 * V8 backdrop: one campus hero at the top, an opaque ink surface below.
 * It must never tile or bleed through the content cards / attendance QR area.
 * The image is an asset, not a flattened screenshot of an interactive screen.
 */
@Composable
fun V8CampusBackdrop(modifier: Modifier = Modifier, login: Boolean = false) {
    val heroHeight = if (login) 680.dp else 355.dp
    val endY = with(LocalDensity.current) { heroHeight.toPx() }
    Box(modifier = modifier.fillMaxSize().background(V8RedColors.Background)) {
        Image(
            painter = painterResource(R.drawable.campus_red),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(heroHeight),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xB208090C),
                        0.20f to Color(0x6908090C),
                        0.50f to Color(0xA708090C),
                        0.78f to Color(0xF208090C),
                        1.00f to V8RedColors.Background,
                    ),
                    startY = 0f,
                    endY = endY,
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.00f to Color(0xC008090C),
                    0.60f to Color(0x7508090C),
                    1.00f to Color(0x2808090C),
                ),
            ),
        )
    }
}
