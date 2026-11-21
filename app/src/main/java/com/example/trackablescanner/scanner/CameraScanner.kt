
package com.example.trackablescanner.scanner

import android.graphics.Rect
import android.util.Size
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val SCAN_WIDTH_RATIO = 0.55f
private const val SCAN_HEIGHT_RATIO = 0.30f
private const val SCAN_INTERVAL_MS = 150L

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScanner(
    modifier: Modifier = Modifier,
    onCodeDetected: (String) -> Unit
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val lastScanTime = remember {
        AtomicLong(0L)
    }

    val lastEmittedCode = remember {
        AtomicReference<String?>(null)
    }

    val lastEmptyScanTime = remember {
        AtomicLong(0L)
    }

    val cameraExecutor = remember {
        Executors.newSingleThreadExecutor()
    }

    val recognizer = remember {
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )
    }

    val trackableRecognizer = remember {
        TrackableRecognizer()
    }

    val scanConfirmation = remember {
        ScanConfirmation(requiredConfirmations = 2)
    }

    Box(modifier = modifier) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->

                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode =
                        PreviewView.ImplementationMode.COMPATIBLE
                }

                val cameraProviderFuture =
                    ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({

                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(
                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build()

                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->

                        processImage(
                            imageProxy = imageProxy,
                            lastScanTime = lastScanTime,
                            recognizer = recognizer,
                            trackableRecognizer = trackableRecognizer,
                            scanConfirmation = scanConfirmation,
                            lastEmittedCode = lastEmittedCode,
                            lastEmptyScanTime = lastEmptyScanTime,
                            onCodeDetected = onCodeDetected
                        )
                    }

                    try {

                        cameraProvider.unbindAll()

                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Rámeček odpovídající skutečné oblasti OCR
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val frameWidth = size.width * SCAN_WIDTH_RATIO
            val frameHeight = size.height * SCAN_HEIGHT_RATIO

            val left = (size.width - frameWidth) / 2f
            val top = (size.height - frameHeight) / 2f

            drawRoundRect(
                color = Color.Green,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(
                    frameWidth,
                    frameHeight
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    14.dp.toPx()
                ),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer.close()
            cameraExecutor.shutdown()
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processImage(
    imageProxy: ImageProxy,
    lastScanTime: AtomicLong,
    recognizer: TextRecognizer,
    trackableRecognizer: TrackableRecognizer,
    scanConfirmation: ScanConfirmation,
    lastEmittedCode: AtomicReference<String?>,
    lastEmptyScanTime: AtomicLong,
    onCodeDetected: (String) -> Unit
) {

    val currentTime = System.currentTimeMillis()
    val previousTime = lastScanTime.get()

    if (currentTime - previousTime < SCAN_INTERVAL_MS) {
        imageProxy.close()
        return
    }

    lastScanTime.set(currentTime)

    try {

        val mediaImage = imageProxy.image

        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // Skutečný výřez obrazu před OCR
        val croppedImage = cropImageToScanZone(imageProxy)

        val inputImage = InputImage.fromByteArray(
            croppedImage.data,
            croppedImage.width,
            croppedImage.height,
            imageProxy.imageInfo.rotationDegrees,
            InputImage.IMAGE_FORMAT_NV21
        )

        recognizer.process(inputImage)
            .addOnSuccessListener { result ->

                val detectedCodes = trackableRecognizer.findCodes(result)

                val now = System.currentTimeMillis()

                if (detectedCodes.isEmpty()) {

                    if (lastEmptyScanTime.get() == 0L) {
                        lastEmptyScanTime.set(now)
                    }

                    // Kód musí zmizet z obrazu alespoň na 2 sekundy,
                    // než dovolíme jeho opětovné naskenování.
                    if (now - lastEmptyScanTime.get() >= 2000L) {
                        lastEmittedCode.set(null)
                        scanConfirmation.observe(emptySet())
                    }

                    return@addOnSuccessListener
                }

                lastEmptyScanTime.set(0L)

                val confirmedCode = scanConfirmation.observe(detectedCodes)

                if (confirmedCode != null) {

                    // Stejný kód nesmí vyvolat opakovaný požadavek.
                    if (lastEmittedCode.compareAndSet(null, confirmedCode)) {
                        onCodeDetected(confirmedCode)
                    }
                }
            }
            .addOnFailureListener { exception ->

                exception.printStackTrace()
                scanConfirmation.observe(emptySet())
            }
            .addOnCompleteListener {
                imageProxy.close()
            }

    } catch (e: Exception) {

        e.printStackTrace()
        scanConfirmation.observe(emptySet())
        imageProxy.close()
    }
}

private data class CroppedImage(
    val data: ByteArray,
    val width: Int,
    val height: Int
)

/**
 * Ořízne YUV_420_888 obraz na oblast odpovídající
 * zelenému rámečku a převede jej na NV21.
 */
private fun cropImageToScanZone(
    imageProxy: ImageProxy
): CroppedImage {

    val imageWidth = imageProxy.width
    val imageHeight = imageProxy.height
    val rotation = imageProxy.imageInfo.rotationDegrees

    val sourceRect = imageProxy.cropRect

    // Při otočení obrazu o 90/270 stupňů se
    // prohodí poměry šířky a výšky.
    val rotated = rotation == 90 || rotation == 270

    val widthRatio = if (rotated) {
        SCAN_HEIGHT_RATIO
    } else {
        SCAN_WIDTH_RATIO
    }

    val heightRatio = if (rotated) {
        SCAN_WIDTH_RATIO
    } else {
        SCAN_HEIGHT_RATIO
    }

    var cropWidth = (sourceRect.width() * widthRatio).toInt()
    var cropHeight = (sourceRect.height() * heightRatio).toInt()

    // YUV chroma rovina vyžaduje sudé rozměry.
    cropWidth = cropWidth and -2
    cropHeight = cropHeight and -2

    cropWidth = cropWidth.coerceAtLeast(2)
    cropHeight = cropHeight.coerceAtLeast(2)

    var left = sourceRect.centerX() - cropWidth / 2
    var top = sourceRect.centerY() - cropHeight / 2

    left = left.coerceIn(sourceRect.left, sourceRect.right - cropWidth)
    top = top.coerceIn(sourceRect.top, sourceRect.bottom - cropHeight)

    left = left and -2
    top = top and -2

    val cropRect = Rect(
        left,
        top,
        left + cropWidth,
        top + cropHeight
    )

    val planes = imageProxy.planes

    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBuffer = yPlane.buffer.duplicate()
    val uBuffer = uPlane.buffer.duplicate()
    val vBuffer = vPlane.buffer.duplicate()

    val nv21 = ByteArray(cropWidth * cropHeight * 3 / 2)

    var outputIndex = 0

    // Y složka
    for (y in 0 until cropHeight) {

        val sourceY = cropRect.top + y

        for (x in 0 until cropWidth) {

            val sourceX = cropRect.left + x

            val sourceIndex =
                sourceY * yPlane.rowStride +
                        sourceX * yPlane.pixelStride

            nv21[outputIndex++] = yBuffer.get(sourceIndex)
        }
    }

    // UV složka – NV21 používá pořadí VU
    for (y in 0 until cropHeight step 2) {

        for (x in 0 until cropWidth step 2) {

            val sourceX = (cropRect.left + x) / 2
            val sourceY = (cropRect.top + y) / 2

            val vIndex =
                sourceY * vPlane.rowStride +
                        sourceX * vPlane.pixelStride

            val uIndex =
                sourceY * uPlane.rowStride +
                        sourceX * uPlane.pixelStride

            nv21[outputIndex++] = vBuffer.get(vIndex)
            nv21[outputIndex++] = uBuffer.get(uIndex)
        }
    }

    return CroppedImage(
        data = nv21,
        width = cropWidth,
        height = cropHeight
    )
}
