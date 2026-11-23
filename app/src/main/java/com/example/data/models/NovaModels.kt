package com.example.data.models

enum class NovaAssistantStatus {
    INACTIVE,
    ACTIVE,
    SCANNING,
    PROCESSING,
    OCR_REVIEW,
    COMPLETED,
    ERROR
}

enum class ScanSourceType(val labelId: String) {
    SCAN_AREA("Scan Area"),
    GALLERY("Galeri"),
    MANUAL_TEXT("Teks Langsung")
}

enum class FloatingPanelMode {
    HIDDEN,
    COMPACT,
    EXPANDED,
    MINIMIZED
}

enum class FloatingHubTab(val label: String) {
    ANSWER("Jawaban"),
    ASK("Tanya"),
    HISTORY("Riwayat"),
    SETTINGS("Pengaturan")
}

enum class AnswerLanguage(val code: String, val displayLabel: String) {
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English")
}

data class SearchResultItem(
    val title: String,
    val snippet: String,
    val url: String,
    val displayDomain: String
)

data class OcrEvaluation(
    val rawText: String,
    val cleanedQuestion: String,
    val blockCount: Int,
    val lineCount: Int,
    val wordCount: Int,
    val averageConfidence: Float?,
    val ocrDurationMs: Long
)

data class PipelineTimings(
    val cropMs: Long = 0L,
    val ocrMs: Long = 0L,
    val searchMs: Long = 0L,
    val aiMs: Long = 0L
) {
    val totalMs: Long get() = cropMs + ocrMs + searchMs + aiMs
}

data class NovaAnswerResult(
    val id: Long = 0L,
    val question: String,
    val shortAnswer: String,
    val fullExplanation: String,
    val sources: List<SearchResultItem>,
    val searchProviderLabel: String,
    val sourceType: ScanSourceType,
    val insufficientInfo: Boolean = false,
    val timings: PipelineTimings = PipelineTimings(),
    val timestamp: Long = System.currentTimeMillis()
)

data class NovaSettings(
    val bubbleOpacity: Float = 0.92f,
    val bubbleSizeDp: Int = 58,
    val confirmOcrBeforeSearch: Boolean = true,
    val defaultExpandedPanel: Boolean = false,
    val answerLanguage: AnswerLanguage = AnswerLanguage.INDONESIAN,
    val saveHistoryLocally: Boolean = true
)

data class NovaSessionState(
    val isFloatingServiceRunning: Boolean = false,
    val isCaptureSessionReady: Boolean = false,
    val isMenuOpen: Boolean = false,
    val isBubbleMinimized: Boolean = false,
    val isSelectingArea: Boolean = false,
    val isCaptureInProgress: Boolean = false,
    val panelMode: FloatingPanelMode = FloatingPanelMode.HIDDEN,
    val activeHubTab: FloatingHubTab = FloatingHubTab.ANSWER,
    val isHistorySearchFocused: Boolean = false,
    val status: NovaAssistantStatus = NovaAssistantStatus.INACTIVE,
    val statusMessage: String = "Floating NOVA belum aktif",
    val pendingOcr: OcrEvaluation? = null,
    val pendingSourceType: ScanSourceType = ScanSourceType.SCAN_AREA,
    val pendingCropMs: Long = 0L,
    val currentQuestion: String = "",
    val latestAnswer: NovaAnswerResult? = null,
    val fallbackSources: List<SearchResultItem> = emptyList(),
    val errorTitle: String? = null,
    val errorMessage: String? = null,
    val isMissingOpenAiKey: Boolean = false,
    val toastMessage: String? = null
)
