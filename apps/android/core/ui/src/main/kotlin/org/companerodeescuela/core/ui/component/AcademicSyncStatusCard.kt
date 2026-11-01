@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.companerodeescuela.core.designsystem.v8.V8GlassCard

/** Informational state, shared by Inicio and Horario; never grants academic access. */
@Composable
fun AcademicSyncStatusCard(
    title: String,
    message: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    V8GlassCard(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = LocalDensity.current.fontScale >= 1.5f || maxWidth < 260.dp
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.align(Alignment.End)) {
                        Text(if (refreshing) "Actualizando…" else "Actualizar")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.labelLarge)
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onRefresh, enabled = !refreshing) {
                        Text(if (refreshing) "Actualizando…" else "Actualizar")
                    }
                }
            }
        }
    }
}
