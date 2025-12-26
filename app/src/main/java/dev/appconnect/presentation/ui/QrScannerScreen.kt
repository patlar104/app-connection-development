package dev.appconnect.presentation.ui

import android.Manifest
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dev.appconnect.R
import dev.appconnect.presentation.theme.QRScannerOverlayWhite
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber

// QR Scanner constants
private const val SCANNING_FRAME_SIZE_DP = 250
private const val SCANNING_TEXT_TOP_PADDING_DP = 300
private const val SCANNING_FRAME_STROKE_WIDTH_DP = 4
private const val QR_PREVIEW_LENGTH = 50

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrScannerScreen(
    onScanSuccess: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val hasCameraPermission = cameraPermissionState.status is PermissionStatus.Granted

    if (hasCameraPermission) {
        val scanStateRef = remember { ScanState() }
        
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val barcodeScanner = BarcodeScanning.getClient()

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                    processImageProxy(
                                        imageProxy,
                                        barcodeScanner,
                                        scanStateRef,
                                        onScanSuccess
                                    )
                                }
                            }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            },
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Camera binding failed")
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay (Scanning Frame + Close Button)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            // Close button at top
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp) // Keep 16.dp for padding, not a scanning-specific constant
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.ui_close),
                    tint = QRScannerOverlayWhite
                )
            }
            
            // Scanning frame
            Canvas(modifier = Modifier.size(SCANNING_FRAME_SIZE_DP.dp)) {
                drawRect(
                    color = QRScannerOverlayWhite,
                    style = Stroke(width = SCANNING_FRAME_STROKE_WIDTH_DP.dp.toPx())
                )
            }
            Text(
                text = stringResource(R.string.ui_scan_pc_qr_code),
                color = QRScannerOverlayWhite,
                modifier = Modifier.padding(top = SCANNING_TEXT_TOP_PADDING_DP.dp)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.ui_camera_permission_required))
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text(stringResource(R.string.ui_grant_permission))
            }
        }
    }
}

private class ScanState {
    var hasScanned = false
}

private fun processImageProxy(
    imageProxy: ImageProxy,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    scanState: ScanState,
    onScanSuccess: (String) -> Unit
) {
    // Prevent multiple scans
    if (scanState.hasScanned) {
        imageProxy.close()
        return
    }
    
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { qrData ->
                        if (!scanState.hasScanned) {
                            scanState.hasScanned = true
                            Timber.d("QR code scanned: ${qrData.take(QR_PREVIEW_LENGTH)}...")
                            onScanSuccess(qrData)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Barcode scanning failed")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

