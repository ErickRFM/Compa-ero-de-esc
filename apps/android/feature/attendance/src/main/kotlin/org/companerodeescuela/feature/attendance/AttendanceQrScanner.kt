package org.companerodeescuela.feature.attendance

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import org.companerodeescuela.core.designsystem.theme.CompaneroSize
import org.companerodeescuela.core.designsystem.theme.CompaneroSpacing

@Composable
fun AttendanceQrScanner(
    onToken: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black,
    ) {
        if (!permissionGranted) {
            CameraPermissionRequired(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onClose = onClose,
            )
            return@Surface
        }

        val options = remember {
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        }
        val scanner = remember { BarcodeScanning.getClient(options) }
        val accepted = remember { AtomicBoolean(false) }
        var boundCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

        DisposableEffect(scanner) {
            onDispose {
                boundCameraProvider?.unbindAll()
                scanner.close()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { previewContext ->
                    PreviewView(previewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                        val cameraProviderFuture =
                            ProcessCameraProvider.getInstance(previewContext)
                        cameraProviderFuture.addListener(
                            {
                                val cameraProvider = cameraProviderFuture.get()
                                boundCameraProvider = cameraProvider
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = surfaceProvider
                                }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(
                                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST,
                                    )
                                    .build()
                                analysis.setAnalyzer(
                                    ContextCompat.getMainExecutor(previewContext),
                                    QrAnalyzer(
                                        scanner = scanner,
                                        accepted = accepted,
                                        onToken = onToken,
                                    ),
                                )

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis,
                                )
                            },
                            ContextCompat.getMainExecutor(previewContext),
                        )
                    }
                },
            )

            val frameColor = MaterialTheme.colorScheme.primary
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(CompaneroSize.scannerFrame),
            ) {
                val length = size.minDimension * 0.22f
                val strokeWidth = CompaneroSize.scannerFrameStroke.toPx()
                val right = size.width
                val bottom = size.height

                drawLine(frameColor, Offset.Zero, Offset(length, 0f), strokeWidth, cap = StrokeCap.Round)
                drawLine(frameColor, Offset.Zero, Offset(0f, length), strokeWidth, cap = StrokeCap.Round)
                drawLine(frameColor, Offset(right - length, 0f), Offset(right, 0f), strokeWidth, cap = StrokeCap.Round)
                drawLine(frameColor, Offset(right, 0f), Offset(right, length), strokeWidth, cap = StrokeCap.Round)
                drawLine(frameColor, Offset(0f, bottom - length), Offset(0f, bottom), strokeWidth, cap = StrokeCap.Round)
                drawLine(frameColor, Offset(0f, bottom), Offset(length, bottom), strokeWidth, cap = StrokeCap.Round)
                drawLine(frameColor, Offset(right - length, bottom), Offset(right, bottom), strokeWidth, cap = StrokeCap.Round)
                drawLine(frameColor, Offset(right, bottom - length), Offset(right, bottom), strokeWidth, cap = StrokeCap.Round)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.58f))
                    .statusBarsPadding()
                    .padding(horizontal = CompaneroSpacing.lg, vertical = CompaneroSpacing.md),
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
                    ) {
                        Text(
                            text = "Escanear QR",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            text = "Apunta al pase de tu docente",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.82f),
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cerrar escáner",
                            tint = Color.White,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .navigationBarsPadding()
                    .padding(horizontal = CompaneroSpacing.lg, vertical = CompaneroSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CompaneroSpacing.xxs),
            ) {
                Text(
                    text = "Lectura segura · sin conexión",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                Text(
                    text = "El intento se guarda primero en este dispositivo; el servidor confirma el estado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun CameraPermissionRequired(
    onRequest: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(CompaneroSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Permiso de cámara",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        Text(
            text = "La cámara se usa únicamente para leer el QR de asistencia. Puedes volver sin concederla.",
            modifier = Modifier.padding(
                top = CompaneroSpacing.sm,
                bottom = CompaneroSpacing.lg,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
        )
        Button(onClick = onRequest) {
            Text("Permitir cámara")
        }
        Button(
            onClick = onClose,
            modifier = Modifier.padding(top = CompaneroSpacing.xs),
        ) {
            Text("Volver")
        }
    }
}

private class QrAnalyzer(
    private val scanner: BarcodeScanner,
    private val accepted: AtomicBoolean,
    private val onToken: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    @ExperimentalGetImage
    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || accepted.get()) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes
                    .firstOrNull()
                    ?.rawValue
                    ?.trim()
                    ?.takeIf { it.startsWith("v1.") }
                if (value != null && accepted.compareAndSet(false, true)) {
                    onToken(value)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
