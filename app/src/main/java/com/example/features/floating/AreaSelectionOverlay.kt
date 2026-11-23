package com.example.features.floating

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.NovaStatusScanning
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragHandle {
    NONE, MOVE,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    LEFT, RIGHT, TOP, BOTTOM
}

@Composable
fun AreaSelectionOverlay(
    onCancel: () -> Unit,
    onConfirmScan: (selectionRect: RectF, overlayWidth: Int, overlayHeight: Int) -> Unit
) {
    val density = LocalDensity.current
    val handleHitRadiusPx = with(density) { 30.dp.toPx() }
    val minWidthPx = with(density) { 100.dp.toPx() }
    val minHeightPx = with(density) { 64.dp.toPx() }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var rect by remember { mutableStateOf<RectF?>(null) }
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { newSize ->
                containerSize = newSize
                if (rect == null && newSize.width > 0 && newSize.height > 0) {
                    val w = newSize.width.toFloat()
                    val h = newSize.height.toFloat()
                    rect = RectF(
                        w * 0.12f,
                        h * 0.28f,
                        w * 0.88f,
                        h * 0.52f
                    )
                }
            }
            .pointerInput(containerSize) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val current = rect ?: return@detectDragGestures
                        activeHandle = detectHandle(startOffset, current, handleHitRadiusPx)
                    },
                    onDragEnd = { activeHandle = DragHandle.NONE },
                    onDragCancel = { activeHandle = DragHandle.NONE }
                ) { change, dragAmount ->
                    val current = rect ?: return@detectDragGestures
                    val boundsW = containerSize.width.toFloat()
                    val boundsH = containerSize.height.toFloat()
                    if (boundsW <= 0f || boundsH <= 0f) return@detectDragGestures

                    change.consume()
                    val updated = RectF(current)
                    when (activeHandle) {
                        DragHandle.MOVE -> {
                            val w = updated.width()
                            val h = updated.height()
                            val newLeft = (updated.left + dragAmount.x).coerceIn(0f, boundsW - w)
                            val newTop = (updated.top + dragAmount.y).coerceIn(0f, boundsH - h)
                            updated.set(newLeft, newTop, newLeft + w, newTop + h)
                        }
                        DragHandle.TOP_LEFT -> {
                            updated.left = (updated.left + dragAmount.x)
                                .coerceIn(0f, updated.right - minWidthPx)
                            updated.top = (updated.top + dragAmount.y)
                                .coerceIn(0f, updated.bottom - minHeightPx)
                        }
                        DragHandle.TOP_RIGHT -> {
                            updated.right = (updated.right + dragAmount.x)
                                .coerceIn(updated.left + minWidthPx, boundsW)
                            updated.top = (updated.top + dragAmount.y)
                                .coerceIn(0f, updated.bottom - minHeightPx)
                        }
                        DragHandle.BOTTOM_LEFT -> {
                            updated.left = (updated.left + dragAmount.x)
                                .coerceIn(0f, updated.right - minWidthPx)
                            updated.bottom = (updated.bottom + dragAmount.y)
                                .coerceIn(updated.top + minHeightPx, boundsH)
                        }
                        DragHandle.BOTTOM_RIGHT -> {
                            updated.right = (updated.right + dragAmount.x)
                                .coerceIn(updated.left + minWidthPx, boundsW)
                            updated.bottom = (updated.bottom + dragAmount.y)
                                .coerceIn(updated.top + minHeightPx, boundsH)
                        }
                        DragHandle.LEFT -> {
                            updated.left = (updated.left + dragAmount.x)
                                .coerceIn(0f, updated.right - minWidthPx)
                        }
                        DragHandle.RIGHT -> {
                            updated.right = (updated.right + dragAmount.x)
                                .coerceIn(updated.left + minWidthPx, boundsW)
                        }
                        DragHandle.TOP -> {
                            updated.top = (updated.top + dragAmount.y)
                                .coerceIn(0f, updated.bottom - minHeightPx)
                        }
                        DragHandle.BOTTOM -> {
                            updated.bottom = (updated.bottom + dragAmount.y)
                                .coerceIn(updated.top + minHeightPx, boundsH)
                        }
                        DragHandle.NONE -> Unit
                    }
                    rect = updated
                }
            }
            .testTag("area_selection_overlay")
    ) {
        val currentRect = rect

        // Offscreen compositing canvas for crystal-clear transparent rectangle cutout
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(color = Color(0x9E04070E))

            if (currentRect != null) {
                // Transparent cutout hole
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(currentRect.left, currentRect.top),
                    size = Size(currentRect.width(), currentRect.height()),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    blendMode = BlendMode.Clear
                )

                // Subtle precision border
                drawRoundRect(
                    color = CyanPulse.copy(alpha = 0.85f),
                    topLeft = Offset(currentRect.left, currentRect.top),
                    size = Size(currentRect.width(), currentRect.height()),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Rule-of-thirds subtle reticle hairlines
                val thirdW = currentRect.width() / 3f
                val thirdH = currentRect.height() / 3f
                val gridColor = CyanPulse.copy(alpha = 0.22f)
                drawLine(
                    gridColor,
                    Offset(currentRect.left + thirdW, currentRect.top),
                    Offset(currentRect.left + thirdW, currentRect.bottom),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    gridColor,
                    Offset(currentRect.left + thirdW * 2, currentRect.top),
                    Offset(currentRect.left + thirdW * 2, currentRect.bottom),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    gridColor,
                    Offset(currentRect.left, currentRect.top + thirdH),
                    Offset(currentRect.right, currentRect.top + thirdH),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    gridColor,
                    Offset(currentRect.left, currentRect.top + thirdH * 2),
                    Offset(currentRect.right, currentRect.top + thirdH * 2),
                    strokeWidth = 1.dp.toPx()
                )

                // Bold glowing optical corner brackets
                val bracketLen = 22.dp.toPx()
                val bracketStroke = 4.dp.toPx()
                val l = currentRect.left
                val t = currentRect.top
                val r = currentRect.right
                val b = currentRect.bottom

                // Top-left
                drawLine(CyanPulse, Offset(l, t), Offset(l + bracketLen, t), bracketStroke, StrokeCap.Round)
                drawLine(CyanPulse, Offset(l, t), Offset(l, t + bracketLen), bracketStroke, StrokeCap.Round)
                // Top-right
                drawLine(CyanPulse, Offset(r, t), Offset(r - bracketLen, t), bracketStroke, StrokeCap.Round)
                drawLine(CyanPulse, Offset(r, t), Offset(r, t + bracketLen), bracketStroke, StrokeCap.Round)
                // Bottom-left
                drawLine(CyanPulse, Offset(l, b), Offset(l + bracketLen, b), bracketStroke, StrokeCap.Round)
                drawLine(CyanPulse, Offset(l, b), Offset(l, b - bracketLen), bracketStroke, StrokeCap.Round)
                // Bottom-right
                drawLine(CyanPulse, Offset(r, b), Offset(r - bracketLen, b), bracketStroke, StrokeCap.Round)
                drawLine(CyanPulse, Offset(r, b), Offset(r, b - bracketLen), bracketStroke, StrokeCap.Round)
            }
        }

        // Visual capture transparency indicator (PRD 11: visual indicator during screen capture)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(ObsidianSurface.copy(alpha = 0.94f))
                .border(1.dp, ObsidianBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(NovaStatusScanning)
            )
            val dims = if (currentRect != null) {
                "${currentRect.width().roundToInt()} × ${currentRect.height().roundToInt()} px"
            } else {
                "Siap"
            }
            Text(
                text = "SCAN AREA · $dims",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }

        // Floating Cancel + Scan action bar anchored near selection rectangle
        if (currentRect != null && containerSize.height > 0) {
            val actionBarHeightPx = with(density) { 64.dp.toPx() }
            val marginPx = with(density) { 16.dp.toPx() }
            val placeBelow = currentRect.bottom + actionBarHeightPx + marginPx * 2 < containerSize.height
            val targetY = if (placeBelow) {
                (currentRect.bottom + marginPx).roundToInt()
            } else {
                (currentRect.top - actionBarHeightPx - marginPx)
                    .coerceAtLeast(with(density) { 72.dp.toPx() })
                    .roundToInt()
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, targetY) }
                    .clip(RoundedCornerShape(24.dp))
                    .background(ObsidianVoid.copy(alpha = 0.95f))
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("area_selection_cancel"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Batal",
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFFCBD5E1)
                    )
                    Text(
                        text = "Batal",
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                Button(
                    onClick = {
                        onConfirmScan(
                            RectF(currentRect),
                            containerSize.width,
                            containerSize.height
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("area_selection_scan"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPulse,
                        contentColor = ObsidianVoid
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DocumentScanner,
                        contentDescription = "Pindai area",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Pindai",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

private fun detectHandle(point: Offset, rect: RectF, radius: Float): DragHandle {
    fun near(x: Float, y: Float): Boolean =
        abs(point.x - x) <= radius && abs(point.y - y) <= radius

    return when {
        near(rect.left, rect.top) -> DragHandle.TOP_LEFT
        near(rect.right, rect.top) -> DragHandle.TOP_RIGHT
        near(rect.left, rect.bottom) -> DragHandle.BOTTOM_LEFT
        near(rect.right, rect.bottom) -> DragHandle.BOTTOM_RIGHT
        abs(point.x - rect.left) <= radius && point.y in rect.top..rect.bottom -> DragHandle.LEFT
        abs(point.x - rect.right) <= radius && point.y in rect.top..rect.bottom -> DragHandle.RIGHT
        abs(point.y - rect.top) <= radius && point.x in rect.left..rect.right -> DragHandle.TOP
        abs(point.y - rect.bottom) <= radius && point.x in rect.left..rect.right -> DragHandle.BOTTOM
        rect.contains(point.x, point.y) -> DragHandle.MOVE
        else -> DragHandle.NONE
    }
}
