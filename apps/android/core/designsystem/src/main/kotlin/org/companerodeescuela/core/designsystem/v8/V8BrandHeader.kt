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
fun V8BrandHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = Icons.Filled.School,
                contentDescription = null,
                tint = V8RedColors.Crimson,
                modifier = Modifier.size(47.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text("Compañero", color = V8RedColors.TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
                Text("de Clase", color = V8RedColors.TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
            }
        }
        Text(
            "E S T U D I A  ·  C O N E C T A  ·  A V A N Z A",
            color = V8RedColors.TextSecondary,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
        )
    }
}
