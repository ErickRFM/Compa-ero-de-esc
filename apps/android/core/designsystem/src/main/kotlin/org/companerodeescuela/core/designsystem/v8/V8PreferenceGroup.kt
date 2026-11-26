@file:Suppress("ktlint:standard:function-naming")

package org.companerodeescuela.core.designsystem.v8

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing

@Composable
fun V8PreferenceGroup(
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    titleWeight: FontWeight? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xs)) {
        Text(
            text = title,
            color = titleColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = titleWeight,
            modifier = Modifier.semantics { heading() },
        )
        V8GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = CompaneroSpacing.md, vertical = CompaneroSpacing.xxs),
        ) {
            Column(content = content)
        }
    }
}
