package org.companerodeescuela.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * V5.3 shape scale.
 *
 * Radius is hierarchical rather than uniformly pill-shaped: rows stay compact,
 * cards are clearly rounded, and only hero/floating surfaces use the largest
 * radius.
 */
internal val CompanionShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
