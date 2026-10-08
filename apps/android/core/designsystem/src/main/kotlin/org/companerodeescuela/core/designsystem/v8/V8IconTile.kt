package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Decorative icon shared by teacher shortcuts and assigned class cards. */
@Composable
fun V8IconTile(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = V8RedColors.Crimson) {
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.size(48.dp).background(Brush.linearGradient(listOf(tint.copy(alpha = .24f), tint.copy(alpha = .07f))), shape)
        .border(1.dp, tint.copy(alpha = .55f), shape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
    }
}
