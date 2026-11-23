package com.example.features.floating

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.models.FloatingPanelMode
import com.example.data.models.NovaAssistantStatus
import com.example.data.models.NovaSessionState
import com.example.data.models.NovaSettings
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.IndigoPrism
import com.example.ui.theme.NovaStatusActive
import com.example.ui.theme.NovaStatusCompleted
import com.example.ui.theme.NovaStatusError
import com.example.ui.theme.NovaStatusProcessing
import com.example.ui.theme.NovaStatusScanning
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid

@Composable
fun FloatingBubbleOverlay(
    sessionState: NovaSessionState,
    settings: NovaSettings,
    onTap: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val statusColor = when (sessionState.status) {
        NovaAssistantStatus.ACTIVE -> NovaStatusActive
        NovaAssistantStatus.SCANNING -> NovaStatusScanning
        NovaAssistantStatus.PROCESSING -> NovaStatusProcessing
        NovaAssistantStatus.OCR_REVIEW -> CyanPulse
        NovaAssistantStatus.COMPLETED -> NovaStatusCompleted
        NovaAssistantStatus.ERROR -> NovaStatusError
        NovaAssistantStatus.INACTIVE -> Color.Gray
    }

    val isAnimated = sessionState.status == NovaAssistantStatus.SCANNING ||
        sessionState.status == NovaAssistantStatus.PROCESSING
    val infiniteTransition = rememberInfiniteTransition(label = "nova_bubble_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val ringColor = if (isAnimated) statusColor.copy(alpha = pulseAlpha) else statusColor

    if (sessionState.isBubbleMinimized) {
        // Minimized edge dock pill — keeps underlying app unobstructed
        Row(
            modifier = Modifier
                .alpha(settings.bubbleOpacity)
                .shadow(10.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(ObsidianVoid.copy(alpha = 0.92f))
                .border(1.5.dp, ringColor, RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd
                    ) { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .clickable(onClick = onTap)
                .height(48.dp)
                .padding(horizontal = 12.dp)
                .testTag("floating_bubble_minimized"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(ringColor)
            )
            Text(
                text = "NOVA",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )
        }
    } else {
        val bubbleSize = settings.bubbleSizeDp.dp
        Box(
            modifier = Modifier
                .alpha(settings.bubbleOpacity)
                .size(bubbleSize + 8.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd
                    ) { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .clickable(onClick = onTap)
                .testTag("floating_bubble"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(bubbleSize)
                    .shadow(14.dp, CircleShape, ambientColor = CyanPulse, spotColor = IndigoPrism)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ObsidianVoid, ObsidianSurface)
                        )
                    )
                    .border(2.dp, ringColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_nova_icon_1795430101889),
                    contentDescription = stringResource(R.string.cd_floating_bubble),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(bubbleSize - 6.dp)
                        .clip(CircleShape)
                )
            }

            // Live status indicator dot (Active / Scanning / Processing / Error)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(ObsidianVoid)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ringColor)
                )
            }

            // Minimized Hub Panel indicator badge
            if (sessionState.panelMode == FloatingPanelMode.MINIMIZED) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyanPulse)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (sessionState.latestAnswer != null) "AI" else "HUB",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ObsidianVoid,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    )
                }
            }
        }
    }
}
