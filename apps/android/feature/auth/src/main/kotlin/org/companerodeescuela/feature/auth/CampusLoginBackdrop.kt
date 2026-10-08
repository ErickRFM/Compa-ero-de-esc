package org.companerodeescuela.feature.auth

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

/**
 * The exact campus corridor image selected for Compañero de Clase.
 *
 * Local drawable, not a network call. Crop stays centered to keep the red
 * roof and campus walkway visible on phones with different aspect ratios.
 * Separate gradients improve text and form readability without modifying
 * the source photograph.
 */
@Composable
internal fun CampusLoginBackdrop(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.login_campus_red),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0xBA000000),
                        0.27f to Color(0x72000000),
                        0.54f to Color(0xA2080508),
                        1.0f to Color(0xEF070407),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0x48000000),
                        0.70f to Color.Transparent,
                    ),
                ),
        )
    }
}
