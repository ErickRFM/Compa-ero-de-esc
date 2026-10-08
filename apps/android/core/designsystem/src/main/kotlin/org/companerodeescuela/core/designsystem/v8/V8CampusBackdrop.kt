package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import org.companerodeescuela.core.designsystem.R

/**
 * Shared offline, photo-backed V8 campus hero. Login and student screens use the
 * same approved artwork rather than a different decorative approximation.
 *
 * Scrims keep scrolling text and translucent surfaces legible on all screens.
 * Artwork is presentation only: no interactive mockup screenshot is embedded.
 */
@Composable
fun V8CampusBackdrop(modifier: Modifier = Modifier, login: Boolean = false) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.campus_red),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                brush = Brush.verticalGradient(
                    0.0f to Color(0xB5000000),
                    0.20f to Color(0x59000000),
                    (if (login) 0.52f else 0.35f) to Color(0xBB09090C),
                    0.74f to Color(0xF908090C),
                    1.0f to V8RedColors.Background,
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                brush = Brush.horizontalGradient(
                    0.0f to Color(0xC608090C),
                    0.53f to Color(0x8808090C),
                    1.0f to Color(0x2008090C),
                ),
            ),
        )
    }
}
