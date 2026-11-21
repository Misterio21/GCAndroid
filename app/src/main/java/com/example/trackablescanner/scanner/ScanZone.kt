
package com.example.trackablescanner.scanner

import android.graphics.Rect
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ScanZoneOverlay(
    modifier: Modifier = Modifier
) {

    Canvas(modifier = modifier) {

        val leftX = size.width * 0.20f
        val rightX = size.width * 0.80f

        val topY = size.height * 0.20f
        val bottomY = size.height * 0.80f

        val lineWidth = 3.dp.toPx()

        drawLine(
            color = Color.Green,
            start = Offset(leftX, topY),
            end = Offset(leftX, bottomY),
            strokeWidth = lineWidth
        )

        drawLine(
            color = Color.Green,
            start = Offset(rightX, topY),
            end = Offset(rightX, bottomY),
            strokeWidth = lineWidth
        )

        drawLine(
            color = Color.Green,
            start = Offset(leftX, topY),
            end = Offset(rightX, topY),
            strokeWidth = lineWidth
        )

        drawLine(
            color = Color.Green,
            start = Offset(leftX, bottomY),
            end = Offset(rightX, bottomY),
            strokeWidth = lineWidth
        )
    }
}

fun isInsideScanZone(
    box: Rect,
    imageProxy: ImageProxy
): Boolean {

    val rotation = imageProxy.imageInfo.rotationDegrees

    val imageWidth: Float
    val imageHeight: Float

    if (rotation == 90 || rotation == 270) {
        imageWidth = imageProxy.height.toFloat()
        imageHeight = imageProxy.width.toFloat()
    } else {
        imageWidth = imageProxy.width.toFloat()
        imageHeight = imageProxy.height.toFloat()
    }

    val centerX = box.exactCenterX()
    val centerY = box.exactCenterY()

    val normalizedX = centerX / imageWidth
    val normalizedY = centerY / imageHeight

    return normalizedX in 0.20f..0.80f &&
            normalizedY in 0.20f..0.80f
}
