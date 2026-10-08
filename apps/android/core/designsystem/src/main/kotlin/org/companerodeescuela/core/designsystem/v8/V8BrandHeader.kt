package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Student-facing V8 brand identity shared by all five approved surfaces. */
@Composable
fun V8BrandHeader(modifier: Modifier = Modifier, stacked: Boolean = false) {
    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            V8BrandMark(Modifier.size(54.dp))
            Text("Compañero\nde Clase", color = V8RedColors.TextPrimary, fontSize = 22.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
            Text("ESTUDIA · CONECTA · AVANZA", color = V8RedColors.TextSecondary, fontSize = 9.sp, letterSpacing = 2.sp)
        }
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            V8BrandMark(Modifier.size(36.dp))
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text("Compañero", color = V8RedColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
                Text("de Clase", color = V8RedColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
            }
        }
        Text(
            "ESTUDIA · CONECTA · AVANZA",
            color = V8RedColors.TextSecondary,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun V8BrandMark(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * .05f, h * .35f); lineTo(w * .5f, h * .12f)
            lineTo(w * .95f, h * .35f); lineTo(w * .5f, h * .58f); close()
            moveTo(w * .22f, h * .44f); lineTo(w * .22f, h * .72f)
            quadraticBezierTo(w * .5f, h * .92f, w * .78f, h * .72f)
            lineTo(w * .78f, h * .44f)
            moveTo(w * .93f, h * .37f); lineTo(w * .93f, h * .64f)
        }
        drawPath(path, V8RedColors.Crimson, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}
