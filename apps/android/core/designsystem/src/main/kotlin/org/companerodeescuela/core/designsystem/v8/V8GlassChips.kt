package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import org.companerodeescuela.core.designsystem.theme.LocalCompaneroHighContrast
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Keeps native chip selection, actions and accessibility with the shared V8 finish. */
@Composable
fun V8GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!LocalV8GlassEnabled.current) {
        FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier)
        return
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.v8GlassSurface(12.dp, emphasized = selected, elevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        border = null,
        leadingIcon = if (selected && LocalCompaneroHighContrast.current) {
            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
fun V8GlassAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    if (!LocalV8GlassEnabled.current) {
        AssistChip(onClick = onClick, label = label, modifier = modifier, enabled = enabled, leadingIcon = leadingIcon)
        return
    }
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier.v8GlassSurface(12.dp, elevation = 0.dp),
        enabled = enabled,
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(12.dp),
        border = null,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = Color.Transparent,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
