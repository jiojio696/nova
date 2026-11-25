package com.example.features.floating

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.models.AdminLicenseItem
import com.example.data.models.LicenseKeyDoc
import com.example.ui.theme.JetBrainsMonoFontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.models.AnswerLanguage
import com.example.data.models.NovaAnswerResult
import com.example.data.models.NovaSettings
import com.example.data.repository.ApiConfigStatus
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.IndigoPrism
import com.example.ui.theme.NovaStatusActive
import com.example.ui.theme.NovaStatusCompleted
import com.example.ui.theme.NovaStatusError
import com.example.ui.theme.NovaStatusScanning
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianVoid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ==================== TAB 2: TANYA CEPAT (DIRECT FLOATING ASK) ====================

@Composable
fun FloatingAskTab(
    onSubmitQuestion: (String) -> Unit,
    onScanArea: () -> Unit,
    onOpenGallery: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val quickSamples = listOf(
        "Apa ibu kota negara Jepang?",
        "Jelaskan hukum Newton kedua secara singkat"
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "TANYA LANGSUNG (SEARCH + GROQ AI)",
            style = MaterialTheme.typography.labelMedium.copy(color = CyanPulse)
        )

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("floating_ask_input"),
            placeholder = { Text("Ketik pertanyaan atau tempel teks...", color = Color(0xFF64748B)) },
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = CyanPulse,
                unfocusedBorderColor = ObsidianBorder,
                focusedContainerColor = ObsidianCard,
                unfocusedContainerColor = ObsidianCard
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Button(
            onClick = {
                if (draft.isNotBlank()) {
                    onSubmitQuestion(draft)
                }
            },
            enabled = draft.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("floating_ask_submit"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPulse,
                contentColor = ObsidianVoid
            )
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cari & Jawab", fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "CONTOH CEPAT",
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
            )
            quickSamples.forEach { sample ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianCard)
                        .border(1.dp, ObsidianBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .clickable { draft = sample }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sample,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactVisualTrigger(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.CropFree,
                label = "Scan Area",
                tint = CyanPulse,
                onClick = onScanArea
            )
            CompactVisualTrigger(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PhotoLibrary,
                label = "Galeri",
                tint = NovaStatusActive,
                onClick = onOpenGallery
            )
        }
    }
}

@Composable
private fun CompactVisualTrigger(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianElevated)
            .border(1.dp, ObsidianBorder.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(4.dp))
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

// ==================== TAB 3: RIWAYAT SCAN LOKAL (ROOM) ====================

@Composable
fun FloatingHistoryTab(
    items: List<NovaAnswerResult>,
    onSearchFocusChanged: (Boolean) -> Unit,
    onLoadInAnswerTab: (NovaAnswerResult) -> Unit,
    onCopyAnswer: (String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearAll: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenGallery: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(items, query) {
        if (query.isBlank()) items
        else items.filter {
            it.question.contains(query, ignoreCase = true) ||
                it.shortAnswer.contains(query, ignoreCase = true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RIWAYAT LOKAL (${items.size})",
                    style = MaterialTheme.typography.labelMedium.copy(color = CyanPulse)
                )
                Text(
                    text = "Teks Q&A saja • Tanpa screenshot",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                )
            }
            if (items.isNotEmpty()) {
                IconButton(
                    onClick = { confirmClear = !confirmClear },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("floating_history_clear_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = "Hapus semua riwayat",
                        tint = NovaStatusError
                    )
                }
            }
        }

        AnimatedVisibility(visible = confirmClear) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2D121D))
                    .border(1.dp, NovaStatusError.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Hapus semua riwayat?",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFECDD3))
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { confirmClear = false }) {
                        Text("Batal", color = Color(0xFFCBD5E1))
                    }
                    TextButton(
                        onClick = {
                            confirmClear = false
                            onClearAll()
                        }
                    ) {
                        Text("Hapus", color = NovaStatusError, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (items.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onSearchFocusChanged(it.isFocused) }
                    .testTag("floating_history_search"),
                placeholder = { Text("Cari riwayat...", color = Color(0xFF64748B)) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = Color(0xFF94A3B8))
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = CyanPulse,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedContainerColor = ObsidianCard,
                    unfocusedContainerColor = ObsidianCard
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_nova_empty_history_1795430098765),
                    contentDescription = stringResource(R.string.cd_empty_history),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 148.dp, height = 110.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Text(
                    text = if (items.isEmpty()) "Belum ada riwayat pemindaian" else "Tidak ada hasil yang cocok",
                    style = MaterialTheme.typography.titleSmall.copy(color = Color.White),
                    textAlign = TextAlign.Center
                )
                if (items.isEmpty()) {
                    OutlinedButton(
                        onClick = onOpenGallery,
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan dari Galeri", color = CyanPulse)
                    }
                }
            }
        } else {
            filtered.take(25).forEach { item ->
                FloatingHistoryCard(
                    item = item,
                    onLoad = { onLoadInAnswerTab(item) },
                    onCopy = {
                        onCopyAnswer("Pertanyaan: ${item.question}\n\nJawaban: ${item.shortAnswer}\n\n${item.fullExplanation}")
                    },
                    onDelete = { onDeleteItem(item.id) },
                    onOpenUrl = onOpenUrl
                )
            }
        }
    }
}

@Composable
private fun FloatingHistoryCard(
    item: NovaAnswerResult,
    onLoad: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val dateLabel = remember(item.timestamp) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
            .clickable(onClick = onLoad)
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyanPulse.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.sourceType.labelId,
                        style = MaterialTheme.typography.labelSmall.copy(color = CyanPulse)
                    )
                }
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Salin",
                        tint = CyanPulse,
                        modifier = Modifier.size(15.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Hapus",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Text(
            text = item.question,
            style = MaterialTheme.typography.titleSmall.copy(color = Color.White),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.shortAnswer,
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (item.sources.isNotEmpty()) {
            val first = item.sources.first()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenUrl(first.url) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(12.dp))
                Text(
                    text = "${first.displayDomain} (+${(item.sources.size - 1).coerceAtLeast(0)} sumber)",
                    style = MaterialTheme.typography.labelSmall.copy(color = CyanPulse),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ==================== TAB 4: PENGATURAN & STATUS IZIN ====================

@Composable
fun FloatingSettingsTab(
    settings: NovaSettings,
    isCaptureSessionReady: Boolean,
    isScreenPinned: Boolean,
    apiConfig: ApiConfigStatus,
    activeLicense: LicenseKeyDoc?,
    onBubbleOpacityChange: (Float) -> Unit,
    onBubbleSizeChange: (Int) -> Unit,
    onConfirmOcrChange: (Boolean) -> Unit,
    onDefaultExpandedChange: (Boolean) -> Unit,
    onLanguageChange: (AnswerLanguage) -> Unit,
    onSaveHistoryChange: (Boolean) -> Unit,
    onActivateCaptureSession: () -> Unit,
    onStopCaptureSession: () -> Unit,
    onChangeLicenseKey: () -> Unit,
    onOpenHiddenAdminGate: () -> Unit
) {
    var showPinningGuide by remember { mutableStateOf(false) }
    var secretTapCount by remember { mutableIntStateOf(0) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 0. Active License Status Card (with hidden 5-tap trigger on header for Admin Panel)
        if (activeLicense != null) {
            val remainingText = remember(activeLicense) {
                AdminLicenseItem(activeLicense).formatRemainingLabel()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianCard)
                    .border(1.dp, NovaStatusCompleted.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val now = System.currentTimeMillis()
                            secretTapCount = if (now - lastTapTimestamp > 2_000L) 1 else secretTapCount + 1
                            lastTapTimestamp = now
                            if (secretTapCount >= 5) {
                                secretTapCount = 0
                                onOpenHiddenAdminGate()
                            }
                        }
                        .testTag("floating_license_header"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        tint = NovaStatusCompleted,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LISENSI PERANGKAT TUNGGAL",
                            style = MaterialTheme.typography.labelSmall.copy(color = NovaStatusCompleted)
                        )
                        Text(
                            text = activeLicense.keyCode,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = Color.White
                            )
                        )
                    }
                }
                Text(
                    text = remainingText,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                )
                OutlinedButton(
                    onClick = onChangeLicenseKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("floating_settings_change_key"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Keluar / Ganti License Key", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 1. Capture Session & Screen Pinning Control
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianCard)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ScreenshotMonitor,
                    contentDescription = null,
                    tint = if (isCaptureSessionReady) NovaStatusCompleted else CyanPulse,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sesi Screen Capture (MediaProjection)",
                        style = MaterialTheme.typography.titleSmall.copy(color = Color.White)
                    )
                    Text(
                        text = if (isCaptureSessionReady) {
                            "Aktif • Siap untuk Scan Area & Screen Pinning"
                        } else {
                            "Aktifkan sebelum Screen Pinning agar Scan Area berjalan internal"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )
                }
            }

            if (isCaptureSessionReady) {
                OutlinedButton(
                    onClick = onStopCaptureSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("floating_settings_stop_capture"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hentikan Sesi Capture", color = NovaStatusError)
                }
            } else {
                Button(
                    onClick = onActivateCaptureSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("floating_settings_start_capture"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPulse,
                        contentColor = ObsidianVoid
                    )
                ) {
                    Text("Aktifkan Sesi Screen Capture", fontWeight = FontWeight.Bold)
                }
            }

            // Expandable Screen Pinning guide
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianElevated)
                    .clickable { showPinningGuide = !showPinningGuide }
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.PushPin, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(15.dp))
                        Text(
                            text = if (isScreenPinned) "Screen Pinning: AKTIF" else "Panduan Screen Pinning",
                            style = MaterialTheme.typography.labelLarge.copy(color = Color.White)
                        )
                    }
                    Icon(
                        imageVector = if (showPinningGuide) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
                AnimatedVisibility(visible = showPinningGuide) {
                    Text(
                        text = "1. Aktifkan sesi Screen Capture di atas sebelum menyematkan aplikasi.\n" +
                            "2. Sematkan aplikasi target via Recents.\n" +
                            "3. Ketuk bubble NOVA → Scan Area internal tanpa keluar dari layar.\n" +
                            "NOVA tidak memakai Accessibility Service dan menghormati FLAG_SECURE.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )
                }
            }
        }

        // 2. Bubble Display Settings (Live preview on the floating bubble!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianCard)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "TAMPILAN BUBBLE & PANEL",
                style = MaterialTheme.typography.labelSmall.copy(color = CyanPulse)
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Opasitas Bubble", style = MaterialTheme.typography.bodySmall.copy(color = Color.White))
                    Text(
                        text = "${(settings.bubbleOpacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(color = CyanPulse)
                    )
                }
                Slider(
                    value = settings.bubbleOpacity,
                    onValueChange = onBubbleOpacityChange,
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanPulse,
                        activeTrackColor = CyanPulse
                    ),
                    modifier = Modifier.testTag("floating_settings_opacity")
                )
                if (settings.bubbleOpacity <= 0.05f) {
                    Text(
                        text = "Mode transparan penuh: bubble tetap bisa diketuk/digeser di posisi terakhirnya, atau buka Hub lewat notifikasi.",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Ukuran Bubble", style = MaterialTheme.typography.bodySmall.copy(color = Color.White))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(50 to "Ringkas", 58 to "Standar", 66 to "Besar").forEach { (dpVal, label) ->
                        FilterChip(
                            selected = settings.bubbleSizeDp == dpVal,
                            onClick = { onBubbleSizeChange(dpVal) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPulse.copy(alpha = 0.22f),
                                selectedLabelColor = CyanPulse,
                                labelColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }
            }

            FloatingToggleRow(
                title = "Konfirmasi Teks OCR",
                subtitle = "Periksa/edit teks ML Kit sebelum cari",
                checked = settings.confirmOcrBeforeSearch,
                onCheckedChange = onConfirmOcrChange
            )

            FloatingToggleRow(
                title = "Panel Default Penuh",
                subtitle = "Langsung perbesar panel saat jawaban siap",
                checked = settings.defaultExpandedPanel,
                onCheckedChange = onDefaultExpandedChange
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bahasa Jawaban AI", style = MaterialTheme.typography.bodySmall.copy(color = Color.White))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnswerLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.answerLanguage == lang,
                            onClick = { onLanguageChange(lang) },
                            label = { Text(lang.displayLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPulse.copy(alpha = 0.22f),
                                selectedLabelColor = CyanPulse,
                                labelColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }
            }

            FloatingToggleRow(
                title = "Simpan Riwayat Lokal",
                subtitle = "Teks Q&A di Room (tanpa screenshot)",
                checked = settings.saveHistoryLocally,
                onCheckedChange = onSaveHistoryChange
            )
        }

        // 3. Engine & API Status (Read-Only)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianCard)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "STATUS ENGINE & API",
                style = MaterialTheme.typography.labelSmall.copy(color = CyanPulse)
            )
            FloatingStatusBadgeRow(
                title = "Groq AI (openai/gpt-oss-120b)",
                detail = if (apiConfig.openAiConfigured) "OPENAI_API_KEY aktif via Secrets" else "Set OPENAI_API_KEY di panel Secrets",
                isReady = apiConfig.openAiConfigured
            )
            FloatingStatusBadgeRow(
                title = "Search API Provider",
                detail = if (apiConfig.googleCustomSearchConfigured) "Google Custom Search aktif" else "Wikimedia Search API + Google fallback",
                isReady = true
            )
            FloatingStatusBadgeRow(
                title = "Google ML Kit OCR (On-Device)",
                detail = "Pengenalan teks Bahasa Indonesia & Inggris lokal",
                isReady = true
            )
        }

        // 4. Privacy Note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianElevated)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = NovaStatusActive, modifier = Modifier.size(16.dp))
            Text(
                text = "Screenshot diproses di memori lalu dibuang setelah OCR. Galeri memakai Photo Picker tanpa izin penyimpanan luas.",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
            )
        }
    }
}

@Composable
private fun FloatingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8)))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FloatingStatusBadgeRow(title: String, detail: String, isReady: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isReady) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
            contentDescription = null,
            tint = if (isReady) NovaStatusCompleted else NovaStatusScanning,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
            Text(text = detail, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8)))
        }
    }
}
