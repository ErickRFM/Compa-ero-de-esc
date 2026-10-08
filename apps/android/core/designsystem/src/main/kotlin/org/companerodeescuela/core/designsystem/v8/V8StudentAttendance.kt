package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Visual feedback only. Evidence status must originate in the attendance domain, not UI heuristics. */
@Composable
fun V8AttendanceEvidence(schoolNetworkVerified: Boolean?, locationVerified: Boolean?, ready: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        V8StatePill(text = when(schoolNetworkVerified) { true -> "Red verificada"; false -> "Red no validada"; null -> "Red pendiente" }, active = schoolNetworkVerified == true, modifier = Modifier.weight(1f))
        V8StatePill(text = when(locationVerified) { true -> "Ubicación verificada"; false -> "Ubicación no validada"; null -> "Ubicación pendiente" }, active = locationVerified == true, modifier = Modifier.weight(1f))
        V8StatePill(text = if (ready) "Listo" else "Pendiente", active = ready, modifier = Modifier.weight(1f))
    }
}

@Composable
fun V8AttendanceProgress(present: Int, total: Int, modifier: Modifier = Modifier) {
    val boundedTotal = total.coerceAtLeast(0)
    val boundedPresent = present.coerceIn(0, boundedTotal)
    val ratio = if (boundedTotal == 0) 0f else boundedPresent.toFloat() / boundedTotal
    V8GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Asistencia de la clase", color = V8RedColors.TextPrimary, fontWeight = FontWeight.Bold)
            Text("$boundedPresent / $boundedTotal presentes", color = V8RedColors.TextSecondary, fontSize = 13.sp)
            LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth(), color = V8RedColors.Crimson, trackColor = V8RedColors.Outline)
        }
    }
}
