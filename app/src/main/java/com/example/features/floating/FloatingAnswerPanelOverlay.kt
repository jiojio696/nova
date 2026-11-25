package com.example.features.floating

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Minimize
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.models.AnswerLanguage
import com.example.data.models.FloatingHubTab
import com.example.data.models.FloatingPanelMode
import com.example.data.models.NovaAnswerResult
import com.example.data.models.NovaAssistantStatus
import com.example.data.models.NovaSessionState
import com.example.data.models.NovaSettings
import com.example.data.models.SearchResultItem
import com.example.data.repository.ApiConfigStatus
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.IndigoPrism
import com.example.ui.theme.NovaStatusCompleted
import com.example.ui.theme.NovaStatusError
import com.example.ui.theme.NovaStatusProcessing
import com.example.ui.theme.NovaStatusScanning
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.data.models.LicenseKeyDoc
import com.example.ui.theme.ObsidianVoid
import kotlinx.coroutines.delay

@Composable
fun FloatingAnswerPanelOverlay(
    sessionState: NovaSessionState,
    settings: NovaSettings,
    history: List<NovaAnswerResult>,
    apiConfig: ApiConfigStatus,
    isScreenPinned: Boolean,
    activeLicense: LicenseKeyDoc?,
    onSelectTab: (FloatingHubTab) -> Unit,
    onHistorySearchFocusChanged: (Boolean) -> Unit,
    onDragPanel: (dx: Float, dy: Float) -> Unit,
    onSubmitDirectQuestion: (String) -> Unit,
    onConfirmOcr: (String) -> Unit,
    onRescan: () -> Unit,
    onOpenGallery: () -> Unit,
    onCopyAnswer: (String) -> Unit,
    onRetry: () -> Unit,
    onToggleExpand: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenGoogleSearch: (String) -> Unit,
    onLoadHistoryItem: (NovaAnswerResult) -> Unit,
    onDeleteHistoryItem: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onBubbleOpacityChange: (Float) -> Unit,
    onBubbleSizeChange: (Int) -> Unit,
    onConfirmOcrSettingChange: (Boolean) -> Unit,
    onDefaultExpandedChange: (Boolean) -> Unit,
    onLanguageChange: (AnswerLanguage) -> Unit,
    onSaveHistoryChange: (Boolean) -> Unit,
    onActivateCaptureSession: () -> Unit,
    onStopCaptureSession: () -> Unit,
    onChangeLicenseKey: () -> Unit,
    onOpenHiddenAdminGate: () -> Unit
) {
    val isExpanded = sessionState.panelMode == FloatingPanelMode.EXPANDED
    val maxBodyHeight = if (isExpanded) 460.dp else 285.dp
    val panelMaxWidth = if (isExpanded) 374.dp else 340.dp

    var copiedRecently by remember { mutableStateOf(false) }
    var showFullExplanation by remember(sessionState.latestAnswer?.timestamp) {
        mutableStateOf(false)
    }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(1800)
            copiedRecently = false
        }
    }

    Surface(
        modifier = Modifier
            .widthIn(min = 300.dp, max = panelMaxWidth)
            .fillMaxWidth()
            .testTag("floating_answer_panel"),
        shape = RoundedCornerShape(22.dp),
        color = ObsidianSurface.copy(alpha = 0.97f),
        shadowElevation = 20.dp,
        tonalElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, ObsidianBorder, RoundedCornerShape(22.dp))
        ) {
            // 1. Draggable Header Bar (Header NOVA, Status, Minimize, Expand, Close)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianElevated)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragPanel(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.DragIndicator,
                    contentDescription = "Geser panel",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "NOVA AI",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    StatusPill(sessionState.status)
                }

                IconButton(
                    onClick = onMinimize,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("panel_minimize_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Minimize,
                        contentDescription = "Minimalkan panel",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("panel_expand_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.OpenInFull,
                        contentDescription = if (isExpanded) "Ringkas panel" else "Perbesar panel",
                        tint = CyanPulse,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("panel_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Tutup panel",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 2. Unified 4-Tab Bar (Jawaban · Tanya · Riwayat · Pengaturan)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianVoid.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                HubTabPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.AutoAwesome,
                    label = "Jawaban",
                    selected = sessionState.activeHubTab == FloatingHubTab.ANSWER,
                    onClick = { onSelectTab(FloatingHubTab.ANSWER) },
                    testTag = "hub_tab_answer"
                )
                HubTabPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Search,
                    label = "Tanya",
                    selected = sessionState.activeHubTab == FloatingHubTab.ASK,
                    onClick = { onSelectTab(FloatingHubTab.ASK) },
                    testTag = "hub_tab_ask"
                )
                HubTabPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.History,
                    label = "Riwayat",
                    selected = sessionState.activeHubTab == FloatingHubTab.HISTORY,
                    onClick = { onSelectTab(FloatingHubTab.HISTORY) },
                    testTag = "hub_tab_history"
                )
                HubTabPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Tune,
                    label = "Atur",
                    selected = sessionState.activeHubTab == FloatingHubTab.SETTINGS,
                    onClick = { onSelectTab(FloatingHubTab.SETTINGS) },
                    testTag = "hub_tab_settings"
                )
            }

            HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.7f))

            // 3. Scrollable Tab Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxBodyHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (sessionState.activeHubTab) {
                    FloatingHubTab.ANSWER -> {
                        AnswerTabContent(
                            sessionState = sessionState,
                            showFullExplanation = showFullExplanation,
                            onToggleFullExplanation = { showFullExplanation = !showFullExplanation },
                            onConfirmOcr = onConfirmOcr,
                            onRescan = onRescan,
                            onOpenGallery = onOpenGallery,
                            onSwitchToAsk = { onSelectTab(FloatingHubTab.ASK) },
                            onRetry = onRetry,
                            onOpenUrl = onOpenUrl,
                            onOpenGoogleSearch = onOpenGoogleSearch
                        )
                    }

                    FloatingHubTab.ASK -> {
                        FloatingAskTab(
                            onSubmitQuestion = onSubmitDirectQuestion,
                            onScanArea = onRescan,
                            onOpenGallery = onOpenGallery
                        )
                    }

                    FloatingHubTab.HISTORY -> {
                        FloatingHistoryTab(
                            items = history,
                            onSearchFocusChanged = onHistorySearchFocusChanged,
                            onLoadInAnswerTab = onLoadHistoryItem,
                            onCopyAnswer = onCopyAnswer,
                            onDeleteItem = onDeleteHistoryItem,
                            onClearAll = onClearHistory,
                            onOpenUrl = onOpenUrl,
                            onOpenGallery = onOpenGallery
                        )
                    }

                    FloatingHubTab.SETTINGS -> {
                        FloatingSettingsTab(
                            settings = settings,
                            isCaptureSessionReady = sessionState.isCaptureSessionReady,
                            isScreenPinned = isScreenPinned,
                            apiConfig = apiConfig,
                            activeLicense = activeLicense,
                            onBubbleOpacityChange = onBubbleOpacityChange,
                            onBubbleSizeChange = onBubbleSizeChange,
                            onConfirmOcrChange = onConfirmOcrSettingChange,
                            onDefaultExpandedChange = onDefaultExpandedChange,
                            onLanguageChange = onLanguageChange,
                            onSaveHistoryChange = onSaveHistoryChange,
                            onActivateCaptureSession = onActivateCaptureSession,
                            onStopCaptureSession = onStopCaptureSession,
                            onChangeLicenseKey = onChangeLicenseKey,
                            onOpenHiddenAdminGate = onOpenHiddenAdminGate
                        )
                    }
                }
            }

            // 4. Bottom Action Bar when Completed Answer is shown on Tab Jawaban
            val answer = sessionState.latestAnswer
            if (sessionState.activeHubTab == FloatingHubTab.ANSWER &&
                sessionState.status == NovaAssistantStatus.COMPLETED &&
                answer != null
            ) {
                HorizontalDivider(color = ObsidianBorder.copy(alpha = 0.7f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianElevated.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val textToCopy = buildString {
                                appendLine("Pertanyaan: ${answer.question}")
                                appendLine()
                                appendLine("Jawaban: ${answer.shortAnswer}")
                                if (answer.fullExplanation.isNotBlank() && answer.fullExplanation != answer.shortAnswer) {
                                    appendLine()
                                    appendLine(answer.fullExplanation)
                                }
                            }.trim()
                            onCopyAnswer(textToCopy)
                            copiedRecently = true
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("panel_copy_button")
                    ) {
                        Icon(
                            imageVector = if (copiedRecently) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Salin jawaban",
                            tint = if (copiedRecently) NovaStatusCompleted else CyanPulse,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (copiedRecently) "Tersalin" else "Salin",
                            color = if (copiedRecently) NovaStatusCompleted else CyanPulse
                        )
                    }

                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("panel_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Ulangi pencarian",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ulangi", color = Color(0xFFCBD5E1))
                    }

                    TextButton(
                        onClick = onRescan,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("panel_rescan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CropFree,
                            contentDescription = "Scan Area baru",
                            tint = IndigoPrism,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan", color = IndigoPrism)
                    }
                }
            }
        }
    }
}

@Composable
private fun HubTabPill(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bg = if (selected) CyanPulse.copy(alpha = 0.2f) else Color.Transparent
    val border = if (selected) CyanPulse.copy(alpha = 0.55f) else Color.Transparent
    val fg = if (selected) CyanPulse else Color(0xFF94A3B8)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = fg,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun AnswerTabContent(
    sessionState: NovaSessionState,
    showFullExplanation: Boolean,
    onToggleFullExplanation: () -> Unit,
    onConfirmOcr: (String) -> Unit,
    onRescan: () -> Unit,
    onOpenGallery: () -> Unit,
    onSwitchToAsk: () -> Unit,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenGoogleSearch: (String) -> Unit
) {
    when (sessionState.status) {
        NovaAssistantStatus.SCANNING,
        NovaAssistantStatus.PROCESSING -> {
            ProcessingSection(
                statusMessage = sessionState.statusMessage,
                question = sessionState.currentQuestion
            )
        }
        NovaAssistantStatus.OCR_REVIEW -> {
            val ocr = sessionState.pendingOcr
            if (ocr != null) {
                OcrReviewSection(
                    initialText = ocr.cleanedQuestion,
                    blockCount = ocr.blockCount,
                    lineCount = ocr.lineCount,
                    wordCount = ocr.wordCount,
                    confidence = ocr.averageConfidence,
                    durationMs = ocr.ocrDurationMs,
                    onConfirm = onConfirmOcr,
                    onRescan = onRescan
                )
            }
        }
        NovaAssistantStatus.ERROR -> {
            ErrorSection(
                title = sessionState.errorTitle ?: "Terjadi Kesalahan",
                message = sessionState.errorMessage ?: "Proses gagal diselesaikan.",
                question = sessionState.currentQuestion,
                fallbackSources = sessionState.fallbackSources,
                onRetry = onRetry,
                onRescan = onRescan,
                onOpenUrl = onOpenUrl,
                onOpenGoogleSearch = onOpenGoogleSearch
            )
        }
        NovaAssistantStatus.COMPLETED -> {
            val answer = sessionState.latestAnswer
            if (answer != null) {
                AnswerResultSection(
                    answer = answer,
                    showFullExplanation = showFullExplanation,
                    onToggleFullExplanation = onToggleFullExplanation,
                    onOpenUrl = onOpenUrl
                )
            }
        }
        else -> {
            val existingAnswer = sessionState.latestAnswer
            if (existingAnswer != null) {
                AnswerResultSection(
                    answer = existingAnswer,
                    showFullExplanation = showFullExplanation,
                    onToggleFullExplanation = onToggleFullExplanation,
                    onOpenUrl = onOpenUrl
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianCard)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Siap memindai layar atau menjawab pertanyaan.",
                        style = MaterialTheme.typography.titleSmall.copy(color = Color.White)
                    )
                    Text(
                        text = "Pilih Scan Area untuk memilih pertanyaan di layar, pilih gambar dari Galeri, atau ketik langsung di tab Tanya.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRescan,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPulse,
                                contentColor = ObsidianVoid
                            )
                        ) {
                            Icon(Icons.Outlined.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Scan Area", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onOpenGallery,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Galeri", color = Color.White)
                        }
                    }
                    TextButton(
                        onClick = onSwitchToAsk,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Atau ketik pertanyaan di Tab Tanya →", color = CyanPulse)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingSection(statusMessage: String, question: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp,
            color = CyanPulse
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.titleSmall.copy(color = Color.White)
            )
            if (question.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = question,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun OcrReviewSection(
    initialText: String,
    blockCount: Int,
    lineCount: Int,
    wordCount: Int,
    confidence: Float?,
    durationMs: Long,
    onConfirm: (String) -> Unit,
    onRescan: () -> Unit
) {
    var editedText by remember(initialText) { mutableStateOf(initialText) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HASIL OCR ML KIT",
                style = MaterialTheme.typography.labelMedium.copy(color = CyanPulse)
            )
            val confLabel = if (confidence != null) {
                "${(confidence * 100).toInt()}% • ${durationMs}ms"
            } else {
                "$wordCount kata • ${durationMs}ms"
            }
            Text(
                text = confLabel,
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
            )
        }

        OutlinedTextField(
            value = editedText,
            onValueChange = { editedText = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ocr_review_input"),
            label = { Text("Periksa atau perbaiki pertanyaan", color = Color(0xFF94A3B8)) },
            minLines = 2,
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

        Text(
            text = "Struktur terdeteksi: $blockCount blok · $lineCount baris · $wordCount kata",
            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("ocr_rescan_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Pindai Ulang", color = Color(0xFFCBD5E1))
            }
            Button(
                onClick = { onConfirm(editedText) },
                modifier = Modifier
                    .weight(1.25f)
                    .heightIn(min = 48.dp)
                    .testTag("ocr_confirm_search_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPulse,
                    contentColor = ObsidianVoid
                )
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cari & Jawab", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AnswerResultSection(
    answer: NovaAnswerResult,
    showFullExplanation: Boolean,
    onToggleFullExplanation: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianCard)
                .padding(12.dp)
        ) {
            Text(
                text = "PERTANYAAN (${answer.sourceType.labelId.uppercase()})",
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = answer.question,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0E243B))
                .border(1.dp, CyanPulse.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(16.dp))
                Text(
                    text = "JAWABAN NOVA AI",
                    style = MaterialTheme.typography.labelMedium.copy(color = CyanPulse)
                )
                if (answer.insufficientInfo) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NovaStatusScanning.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "INFO TERBATAS",
                            style = MaterialTheme.typography.labelSmall.copy(color = NovaStatusScanning)
                        )
                    }
                }
            }

            Text(
                text = answer.shortAnswer,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            )

            if (answer.fullExplanation.isNotBlank() && answer.fullExplanation != answer.shortAnswer) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleFullExplanation)
                        .padding(vertical = 4.dp)
                        .testTag("toggle_full_explanation"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (showFullExplanation) "Sembunyikan penjelasan" else "Penjelasan lengkap",
                        style = MaterialTheme.typography.labelLarge.copy(color = CyanPulse)
                    )
                    Icon(
                        imageVector = if (showFullExplanation) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = CyanPulse,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = showFullExplanation) {
                    Text(
                        text = answer.fullExplanation,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFCBD5E1))
                    )
                }
            }
        }

        if (answer.sources.isNotEmpty()) {
            SourcesListSection(
                providerLabel = answer.searchProviderLabel,
                sources = answer.sources,
                onOpenUrl = onOpenUrl
            )
        }

        Text(
            text = "OCR ${answer.timings.ocrMs}ms · Search ${answer.timings.searchMs}ms · Groq AI ${answer.timings.aiMs}ms",
            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
        )
    }
}

@Composable
private fun ErrorSection(
    title: String,
    message: String,
    question: String,
    fallbackSources: List<SearchResultItem>,
    onRetry: () -> Unit,
    onRescan: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenGoogleSearch: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF2D121D))
                .border(1.dp, NovaStatusError.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = NovaStatusError, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFFFECDD3)))
                Text(text = message, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFDA4AF)))
            }
        }

        if (fallbackSources.isNotEmpty()) {
            SourcesListSection(
                providerLabel = "Referensi Pencarian Ditemukan",
                sources = fallbackSources,
                onOpenUrl = onOpenUrl
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("error_rescan_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Scan Ulang", color = Color(0xFFCBD5E1))
            }
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("error_retry_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPulse,
                    contentColor = ObsidianVoid
                )
            ) {
                Text("Coba Lagi", fontWeight = FontWeight.Bold)
            }
        }

        if (question.isNotBlank()) {
            OutlinedButton(
                onClick = { onOpenGoogleSearch(question) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("error_google_fallback_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Buka Google Search (Fallback)", color = CyanPulse)
            }
        }
    }
}

@Composable
private fun SourcesListSection(
    providerLabel: String,
    sources: List<SearchResultItem>,
    onOpenUrl: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "SUMBER PENCARIAN · ${providerLabel.uppercase()}",
            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
        )
        sources.take(4).forEachIndexed { idx, source ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianCard)
                    .border(1.dp, ObsidianBorder.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .clickable { onOpenUrl(source.url) }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    .testTag("source_item_$idx"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = source.displayDomain,
                        style = MaterialTheme.typography.labelSmall.copy(color = CyanPulse),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.OpenInNew,
                    contentDescription = "Buka sumber",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: NovaAssistantStatus) {
    val (label, color) = when (status) {
        NovaAssistantStatus.SCANNING -> "Scanning" to NovaStatusScanning
        NovaAssistantStatus.PROCESSING -> "Processing" to NovaStatusProcessing
        NovaAssistantStatus.OCR_REVIEW -> "Periksa OCR" to CyanPulse
        NovaAssistantStatus.COMPLETED -> "Completed" to NovaStatusCompleted
        NovaAssistantStatus.ERROR -> "Error" to NovaStatusError
        else -> "Active" to NovaStatusCompleted
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
