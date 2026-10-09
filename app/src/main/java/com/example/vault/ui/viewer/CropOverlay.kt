package com.example.vault.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.vault.ui.theme.SupabaseGreen

private enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
}

/**
 * Interactive touch-draggable crop overlay with rule-of-thirds grid, corner brackets,
 * and semi-transparent dimming outside the crop box.
 * Coordinates returned in onCropAreaChanged are normalized [0f..1f].
 */
@Composable
fun InteractiveCropOverlay(
    bitmapWidth: Int,
    bitmapHeight: Int,
    normRect: Rect,
    onNormRectChange: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleRadiusPx = with(density) { 32.dp.toPx() }
    val cornerLengthPx = with(density) { 22.dp.toPx() }
    val cornerStrokeWidthPx = with(density) { 4.dp.toPx() }
    val borderStrokeWidthPx = with(density) { 2.dp.toPx() }

    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()

        if (containerW <= 0f || containerH <= 0f || bitmapWidth <= 0 || bitmapHeight <= 0) {
            return@BoxWithConstraints
        }

        // Calculate exact image placement with ContentScale.Fit
        val scale = minOf(containerW / bitmapWidth, containerH / bitmapHeight)
        val imageW = bitmapWidth * scale
        val imageH = bitmapHeight * scale
        val imageLeft = (containerW - imageW) / 2f
        val imageTop = (containerH - imageH) / 2f

        // Convert normalized rect [0..1] to screen pixel coordinates
        val cropLeft = imageLeft + normRect.left * imageW
        val cropTop = imageTop + normRect.top * imageH
        val cropRight = imageLeft + normRect.right * imageW
        val cropBottom = imageTop + normRect.bottom * imageH

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imageW, imageH) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val distTL = (offset - Offset(cropLeft, cropTop)).getDistance()
                            val distTR = (offset - Offset(cropRight, cropTop)).getDistance()
                            val distBL = (offset - Offset(cropLeft, cropBottom)).getDistance()
                            val distBR = (offset - Offset(cropRight, cropBottom)).getDistance()

                            activeHandle = when {
                                distTL < handleRadiusPx -> DragHandle.TOP_LEFT
                                distTR < handleRadiusPx -> DragHandle.TOP_RIGHT
                                distBL < handleRadiusPx -> DragHandle.BOTTOM_LEFT
                                distBR < handleRadiusPx -> DragHandle.BOTTOM_RIGHT
                                offset.x in cropLeft..cropRight && offset.y in cropTop..cropBottom -> DragHandle.CENTER
                                else -> DragHandle.NONE
                            }
                        },
                        onDragEnd = { activeHandle = DragHandle.NONE },
                        onDragCancel = { activeHandle = DragHandle.NONE },
                        onDrag = { _, dragAmount ->
                            if (activeHandle == DragHandle.NONE || imageW <= 0f || imageH <= 0f) return@detectDragGestures

                            val dNormX = dragAmount.x / imageW
                            val dNormY = dragAmount.y / imageH
                            val minSize = 0.12f

                            var newLeft = normRect.left
                            var newTop = normRect.top
                            var newRight = normRect.right
                            var newBottom = normRect.bottom

                            when (activeHandle) {
                                DragHandle.TOP_LEFT -> {
                                    newLeft = (normRect.left + dNormX).coerceIn(0f, normRect.right - minSize)
                                    newTop = (normRect.top + dNormY).coerceIn(0f, normRect.bottom - minSize)
                                }
                                DragHandle.TOP_RIGHT -> {
                                    newRight = (normRect.right + dNormX).coerceIn(normRect.left + minSize, 1f)
                                    newTop = (normRect.top + dNormY).coerceIn(0f, normRect.bottom - minSize)
                                }
                                DragHandle.BOTTOM_LEFT -> {
                                    newLeft = (normRect.left + dNormX).coerceIn(0f, normRect.right - minSize)
                                    newBottom = (normRect.bottom + dNormY).coerceIn(normRect.top + minSize, 1f)
                                }
                                DragHandle.BOTTOM_RIGHT -> {
                                    newRight = (normRect.right + dNormX).coerceIn(normRect.left + minSize, 1f)
                                    newBottom = (normRect.bottom + dNormY).coerceIn(normRect.top + minSize, 1f)
                                }
                                DragHandle.CENTER -> {
                                    val width = normRect.right - normRect.left
                                    val height = normRect.bottom - normRect.top
                                    newLeft = (normRect.left + dNormX).coerceIn(0f, 1f - width)
                                    newTop = (normRect.top + dNormY).coerceIn(0f, 1f - height)
                                    newRight = newLeft + width
                                    newBottom = newTop + height
                                }
                                DragHandle.NONE -> {}
                            }

                            onNormRectChange(Rect(newLeft, newTop, newRight, newBottom))
                        }
                    )
                }
        ) {
            val dimColor = Color(0x99000000)

            // 1. Dim outside rectangles
            // Top dim
            if (cropTop > 0f) {
                drawRect(dimColor, topLeft = Offset(0f, 0f), size = Size(containerW, cropTop))
            }
            // Bottom dim
            if (cropBottom < containerH) {
                drawRect(dimColor, topLeft = Offset(0f, cropBottom), size = Size(containerW, containerH - cropBottom))
            }
            // Left dim
            if (cropLeft > 0f) {
                drawRect(dimColor, topLeft = Offset(0f, cropTop), size = Size(cropLeft, cropBottom - cropTop))
            }
            // Right dim
            if (cropRight < containerW) {
                drawRect(dimColor, topLeft = Offset(cropRight, cropTop), size = Size(containerW - cropRight, cropBottom - cropTop))
            }

            // 2. Crop border
            val cropRectSize = Size(cropRight - cropLeft, cropBottom - cropTop)
            drawRect(
                color = SupabaseGreen.copy(alpha = 0.9f),
                topLeft = Offset(cropLeft, cropTop),
                size = cropRectSize,
                style = Stroke(width = borderStrokeWidthPx)
            )

            // 3. Rule-of-thirds grid lines
            val thirdW = (cropRight - cropLeft) / 3f
            val thirdH = (cropBottom - cropTop) / 3f
            val gridColor = Color.White.copy(alpha = 0.35f)
            val gridStroke = Stroke(width = 1.dp.toPx())

            // Vertical grid lines
            drawLine(gridColor, Offset(cropLeft + thirdW, cropTop), Offset(cropLeft + thirdW, cropBottom), strokeWidth = gridStroke.width)
            drawLine(gridColor, Offset(cropLeft + thirdW * 2f, cropTop), Offset(cropLeft + thirdW * 2f, cropBottom), strokeWidth = gridStroke.width)
            // Horizontal grid lines
            drawLine(gridColor, Offset(cropLeft, cropTop + thirdH), Offset(cropRight, cropTop + thirdH), strokeWidth = gridStroke.width)
            drawLine(gridColor, Offset(cropLeft, cropTop + thirdH * 2f), Offset(cropRight, cropTop + thirdH * 2f), strokeWidth = gridStroke.width)

            // 4. Corner L-brackets
            val bracketColor = Color.White
            val bracketStroke = Stroke(width = cornerStrokeWidthPx)

            // Top-Left corner
            val pathTL = Path().apply {
                moveTo(cropLeft, cropTop + cornerLengthPx)
                lineTo(cropLeft, cropTop)
                lineTo(cropLeft + cornerLengthPx, cropTop)
            }
            drawPath(pathTL, bracketColor, style = bracketStroke)

            // Top-Right corner
            val pathTR = Path().apply {
                moveTo(cropRight - cornerLengthPx, cropTop)
                lineTo(cropRight, cropTop)
                lineTo(cropRight, cropTop + cornerLengthPx)
            }
            drawPath(pathTR, bracketColor, style = bracketStroke)

            // Bottom-Left corner
            val pathBL = Path().apply {
                moveTo(cropLeft, cropBottom - cornerLengthPx)
                lineTo(cropLeft, cropBottom)
                lineTo(cropLeft + cornerLengthPx, cropBottom)
            }
            drawPath(pathBL, bracketColor, style = bracketStroke)

            // Bottom-Right corner
            val pathBR = Path().apply {
                moveTo(cropRight - cornerLengthPx, cropBottom)
                lineTo(cropRight, cropBottom)
                lineTo(cropRight, cropBottom - cornerLengthPx)
            }
            drawPath(pathBR, bracketColor, style = bracketStroke)
        }
    }
}
