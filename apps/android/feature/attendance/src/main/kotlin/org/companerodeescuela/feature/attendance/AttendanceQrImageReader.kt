package org.companerodeescuela.feature.attendance

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class AttendanceQrImageReader(
    private val context: Context,
) {
    suspend fun read(uri: Uri): String? {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        return try {
            val image = InputImage.fromFilePath(context, uri)
            suspendCancellableCoroutine { continuation ->
                scanner.process(image)
                    .addOnSuccessListener { values ->
                        val token = values.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotBlank) }
                        if (continuation.isActive) continuation.resume(token)
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
            }
        } finally {
            scanner.close()
        }
    }
}
