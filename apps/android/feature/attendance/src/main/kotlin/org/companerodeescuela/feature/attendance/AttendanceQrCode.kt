package org.companerodeescuela.feature.attendance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing

@Composable
fun AttendanceQrCode(
    token: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(token) {
        QRCodeWriter().encode(
            token,
            BarcodeFormat.QR_CODE,
            1,
            1,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
            ),
        )
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
            .background(Color.White)
            .padding(CompaneroSpacing.md),
    ) {
        drawRect(Color.White)
        val cellWidth = size.width / matrix.width
        val cellHeight = size.height / matrix.height

        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(
                            x = x * cellWidth,
                            y = y * cellHeight,
                        ),
                        size = Size(
                            width = cellWidth + 0.5f,
                            height = cellHeight + 0.5f,
                        ),
                    )
                }
            }
        }
    }
}
