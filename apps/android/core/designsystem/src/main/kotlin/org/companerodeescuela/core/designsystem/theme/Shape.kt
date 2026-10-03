package org.companerodeescuela.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Expressive V4 shape scale.
 *
 * Large surfaces deliberately carry stronger rounding so the product no
 * longer reads like default Material cards. Compact controls stay tighter to
 * preserve information density on small phones.
 */
internal val CompanionShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
