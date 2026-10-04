package org.companerodeescuela.feature.schedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class ScheduleImportProcessor(
    private val context: Context,
) {
    suspend fun extractText(uri: Uri): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        return if (mime == "application/pdf") {
            extractPdf(uri)
        } else {
            recognize(InputImage.fromFilePath(context, uri))
        }
    }

    private suspend fun extractPdf(uri: Uri): String {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("No se pudo abrir el PDF")
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val pages = minOf(renderer.pageCount, MAX_PAGES)
                return buildList {
                    repeat(pages) { index ->
                        renderer.openPage(index).use { page ->
                            val bitmap = Bitmap.createBitmap(
                                page.width.coerceAtLeast(1),
                                page.height.coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            bitmap.eraseColor(Color.WHITE)
                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                            )
                            try {
                                add(recognize(InputImage.fromBitmap(bitmap, 0)))
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }.joinToString("\n")
            }
        }
    }

    private suspend fun recognize(image: InputImage): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text)
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
            }
        } finally {
            recognizer.close()
        }
    }

    private companion object {
        const val MAX_PAGES = 4
    }
}
