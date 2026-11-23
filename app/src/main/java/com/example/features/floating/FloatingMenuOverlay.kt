package com.example.features.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.models.FloatingHubTab
import com.example.data.models.NovaAssistantStatus
import com.example.data.models.NovaSessionState
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.IndigoPrism
import com.example.ui.theme.NovaStatusActive
import com.example.ui.theme.NovaStatusError
import com.example.ui.theme.NovaStatusProcessing
import com.example.ui.theme.NovaStatusScanning
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid

@Composable
fun FloatingMenuOverlay(
    sessionState: NovaSessionState,
    isScreenPinned: Boolean,
    historyCount: Int,
    onDismiss: () -> Unit,
    onScanArea: () -> Unit,
    onGallery: () -> Unit,
    onOpenHubTab: (FloatingHubTab) -> Unit,
    onMinimizeBubble: () -> Unit,
    onStopAssistant: () -> Unit
) {
    // Full-screen translucent tap-outside backdrop (closes menu cleanly on outside tap)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 342.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("floating_menu_card"),
            shape = RoundedCornerShape(24.dp),
            color = ObsidianSurface.copy(alpha = 0.97f),
            tonalElevation = 12.dp,
            shadowElevation = 20.dp
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusDot(sessionState.status)
                        Column {
                            Text(
                                text = "NOVA AI",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = statusLabel(sessionState.status),
                                style = MaterialTheme.typography.labelMedium.copy(color = CyanPulse)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("floating_menu_close")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Tutup menu",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                if (isScreenPinned) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2B220B))
                            .border(1.dp, NovaStatusScanning.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Screen Pinning aktif — Scan Area & Tanya Cepat tetap siap di floating.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFDE68A))
                        )
                    }
                }

                // Primary Visual Actions
                FloatingMenuActionRow(
                    icon = Icons.Outlined.CropFree,
                    accent = CyanPulse,
                    title = "Scan Area",
                    subtitle = if (sessionState.isCaptureSessionReady) {
                        "Sesi capture siap • Pilih kotak layar"
                    } else {
                        "Minta izin capture lalu pilih area layar"
                    },
                    badge = if (sessionState.isCaptureSessionReady) "SIAP" else "P0",
                    onClick = onScanArea,
                    testTag = "menu_scan_area"
                )

                FloatingMenuActionRow(
                    icon = Icons.Outlined.PhotoLibrary,
                    accent = NovaStatusActive,
                    title = "Galeri",
                    subtitle = "Pilih screenshot/foto via Android Photo Picker",
                    badge = "OCR",
                    onClick = onGallery,
                    testTag = "menu_gallery"
                )

                HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.65f))

                // Direct 4-Tab Floating Hub Shortcuts
                Text(
                    text = "PANEL HUB MENGAMBANG",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HubShortcutTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.AutoAwesome,
                        label = "Jawaban",
                        accent = CyanPulse,
                        onClick = { onOpenHubTab(FloatingHubTab.ANSWER) },
                        testTag = "menu_hub_answer"
                    )
                    HubShortcutTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Search,
                        label = "Tanya",
                        accent = Color(0xFFF59E0B),
                        onClick = { onOpenHubTab(FloatingHubTab.ASK) },
                        testTag = "menu_hub_ask"
                    )
                    HubShortcutTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.History,
                        label = if (historyCount > 0) "Riwayat ($historyCount)" else "Riwayat",
                        accent = IndigoPrism,
                        onClick = { onOpenHubTab(FloatingHubTab.HISTORY) },
                        testTag = "menu_hub_history"
                    )
                    HubShortcutTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Tune,
                        label = "Atur",
                        accent = NovaStatusActive,
                        onClick = { onOpenHubTab(FloatingHubTab.SETTINGS) },
                        testTag = "menu_hub_settings"
                    )
                }

                HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.65f))

                // Utility footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniUtilityButton(
                        icon = Icons.Outlined.Remove,
                        label = "Minimalkan Bubble",
                        tint = Color(0xFFCBD5E1),
                        onClick = onMinimizeBubble,
                        testTag = "menu_minimize_bubble"
                    )
                    MiniUtilityButton(
                        icon = Icons.Outlined.PowerSettingsNew,
                        label = "Hentikan NOVA",
                        tint = NovaStatusError,
                        onClick = onStopAssistant,
                        testTag = "menu_stop_nova"
                    )
                }
            }
        }
    }
}

@Composable
private fun HubShortcutTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun FloatingMenuActionRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(21.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                maxLines = 2
            )
        }
    }
}

@Composable
private fun MiniUtilityButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(color = tint)
        )
    }
}

@Composable
private fun StatusDot(status: NovaAssistantStatus) {
    val color = when (status) {
        NovaAssistantStatus.ACTIVE -> NovaStatusActive
        NovaAssistantStatus.SCANNING -> NovaStatusScanning
        NovaAssistantStatus.PROCESSING -> NovaStatusProcessing
        NovaAssistantStatus.OCR_REVIEW -> CyanPulse
        NovaAssistantStatus.COMPLETED -> Color(0xFF34D399)
        NovaAssistantStatus.ERROR -> NovaStatusError
        NovaAssistantStatus.INACTIVE -> Color.Gray
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(ObsidianVoid)
            .border(2.dp, color, CircleShape)
    )
}

private fun statusLabel(status: NovaAssistantStatus): String = when (status) {
    NovaAssistantStatus.ACTIVE -> "ACTIVE • SIAP MEMINDAI"
    NovaAssistantStatus.SCANNING -> "SCANNING • MEMILIH AREA"
    NovaAssistantStatus.PROCESSING -> "PROCESSING • OCR & GROQ AI"
    NovaAssistantStatus.OCR_REVIEW -> "KONFIRMASI TEKS OCR"
    NovaAssistantStatus.COMPLETED -> "COMPLETED • JAWABAN SIAP"
    NovaAssistantStatus.ERROR -> "ERROR • PERLU TINDAKAN"
    NovaAssistantStatus.INACTIVE -> "NONAKTIF"
}
