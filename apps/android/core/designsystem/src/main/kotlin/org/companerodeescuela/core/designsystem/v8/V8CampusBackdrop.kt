package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Lightweight, offline university-themed V8 backdrop.
 *
 * Independent artwork: never paste an entire mockup behind interactive content.
 * Maintains high contrast with a left/bottom dark scrim. A licensed photo can
 * replace the procedural campus drawing without changing feature screens.
 */
@Composable
fun V8CampusBackdrop(modifier: Modifier = Modifier, login: Boolean = false) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF17080E), Color(0xFF07090C), Color(0xFF08090B)),
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x887A0B18), Color.Transparent),
                center = Offset(w * .82f, h * .23f),
                radius = w * .75f,
            ),
        )
        val roofY = h * (if (login) .115f else .13f)
        val bottomY = h * (if (login) .60f else .47f)
        val left = w * .55f
        drawRect(Color(0xFF151216), topLeft = Offset(left, roofY), size = Size(w * .45f, bottomY - roofY))
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color(0xFF130F12), Color(0xFF492025), Color(0xFF171113))),
            topLeft = Offset(left, roofY),
            size = Size(w * .45f, bottomY - roofY),
        )
        // Angular glass facade and crimson interior lighting.
        val facade = Path().apply {
            moveTo(left, roofY)
            lineTo(w * .72f, roofY - h * .025f)
            lineTo(w, roofY + h * .055f)
            lineTo(w, bottomY)
            lineTo(left, bottomY - h * .025f)
            close()
        }
        drawPath(facade, color = Color(0xCC120F14))
        for (row in 0..3) {
            for (col in 0..3) {
                val x = left + (col * w * .104f)
                val y = roofY + h * .066f + row * (bottomY - roofY) * .20f
                val windowSize = Size(w * .073f, (bottomY - roofY) * .14f)
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF861723), Color(0xFF1F1117))),
                    topLeft = Offset(x, y),
                    size = windowSize,
                )
                drawLine(Color(0xFF0A090D), Offset(x + windowSize.width / 2, y), Offset(x + windowSize.width / 2, y + windowSize.height), strokeWidth = w * .009f)
            }
        }
        // Campus sidewalk and dark silhouettes for depth.
        drawRect(Brush.verticalGradient(listOf(Color(0x003B0810), Color(0xFF0B070A))), topLeft = Offset(0f, bottomY), size = Size(w, h - bottomY))
        for (index in 0..6) {
            val x = w * (.06f + index * .145f)
            val y = bottomY - h * (.015f + (index % 3) * .018f)
            drawLine(Color(0xFF0B0A0D), Offset(x, y), Offset(x - w * .022f, y - h * .14f), strokeWidth = w * .015f)
            for (branch in 0..4) {
                val by = y - h * (.10f + branch * .017f)
                drawLine(Color(0xFF10080D), Offset(x - w * .018f, by), Offset(x + (branch - 2) * w * .03f, by - h * .05f), strokeWidth = w * .008f)
            }
        }
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color(0xF908090C), Color(0xE808090C), Color(0x5008090C)),
            ),
            size = Size(w, bottomY + h * .08f),
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xE508090C), Color(0xFF08090C)),
                startY = bottomY * .56f,
                endY = h * (if (login) .82f else .70f),
            ),
        )
    }
}
