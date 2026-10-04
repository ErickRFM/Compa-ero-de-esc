package org.companerodeescuela.core.designsystem.brand

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import org.companerodeescuela.core.designsystem.R

@Composable
fun UptlaxBrand(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painter = painterResource(R.drawable.uptlax_logo),
        contentDescription = "Universidad Politécnica de Tlaxcala",
        modifier = modifier,
        tint = tint,
    )
}
