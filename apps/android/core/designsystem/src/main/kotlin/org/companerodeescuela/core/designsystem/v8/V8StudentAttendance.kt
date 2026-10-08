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
        EvidenceCard("Jornada escolar", if (schoolNetworkVerified == true) "Verificada" else "Pendiente", schoolNetworkVerified == true, Modifier.weight(1f))
        EvidenceCard("Ubicación", if (locationVerified == true) "Verificada" else "No utilizada", locationVerified == true, Modifier.weight(1f))
        EvidenceCard("Sesión de clase", if (ready) "Lista para QR" else "Pendiente", ready, Modifier.weight(1f))
    }
}

@Composable
private fun EvidenceCard(title: String, status: String, verified: Boolean, modifier: Modifier) {
    V8GlassCard(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp), cornerRadius = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = V8RedColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(status, color = if (verified) V8RedColors.Success else V8RedColors.TextSecondary, fontSize = 10.sp)
        }
    }
}
