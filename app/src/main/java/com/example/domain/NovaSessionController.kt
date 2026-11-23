package com.example.domain

import android.graphics.Bitmap
import com.example.data.models.AnswerLanguage
import com.example.data.models.FloatingHubTab
import com.example.data.models.FloatingPanelMode
import com.example.data.models.NovaAnswerResult
import com.example.data.models.NovaAssistantStatus
import com.example.data.models.NovaSessionState
import com.example.data.models.NovaSettings
import com.example.data.models.PipelineTimings
import com.example.data.models.ScanSourceType
import com.example.data.repository.AiAnswerOutcome
import com.example.data.repository.ApiConfigStatus
import com.example.data.repository.HistoryRepository
import com.example.data.repository.NovaApiRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.scanner.OcrEngine
import com.example.domain.scanner.OcrResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NovaSessionController(
    private val ocrEngine: OcrEngine,
    private val apiRepository: NovaApiRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: StateFlow<NovaSettings> = settingsRepository.settingsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = NovaSettings()
    )

    val history: StateFlow<List<NovaAnswerResult>> = historyRepository.allHistory.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val _state = MutableStateFlow(NovaSessionState())
    val state: StateFlow<NovaSessionState> = _state.asStateFlow()

    private var activePipelineJob: Job? = null

    fun getApiConfigStatus(): ApiConfigStatus = apiRepository.getConfigStatus()

    fun setFloatingServiceRunning(running: Boolean) {
        _state.update { current ->
            if (running) {
                current.copy(
                    isFloatingServiceRunning = true,
                    status = if (current.status == NovaAssistantStatus.INACTIVE) {
                        NovaAssistantStatus.ACTIVE
                    } else {
                        current.status
                    },
                    statusMessage = "NOVA AI siap di atas aplikasi"
                )
            } else {
                activePipelineJob?.cancel()
                current.copy(
                    isFloatingServiceRunning = false,
                    isMenuOpen = false,
                    isSelectingArea = false,
                    isCaptureInProgress = false,
                    isHistorySearchFocused = false,
                    panelMode = FloatingPanelMode.HIDDEN,
                    status = NovaAssistantStatus.INACTIVE,
                    statusMessage = "Floating NOVA nonaktif"
                )
            }
        }
    }

    fun setCaptureSessionReady(ready: Boolean, message: String? = null) {
        _state.update { current ->
            current.copy(
                isCaptureSessionReady = ready,
                toastMessage = message ?: current.toastMessage
            )
        }
    }

    fun toggleMenu() {
        _state.update { current ->
            if (current.isSelectingArea) return@update current
            if (current.isBubbleMinimized) {
                current.copy(isBubbleMinimized = false, isMenuOpen = true)
            } else {
                current.copy(isMenuOpen = !current.isMenuOpen)
            }
        }
    }

    fun closeMenu() {
        _state.update { it.copy(isMenuOpen = false) }
    }

    fun toggleMinimizeBubble() {
        _state.update { current ->
            current.copy(
                isBubbleMinimized = !current.isBubbleMinimized,
                isMenuOpen = false
            )
        }
    }

    fun openHubTab(tab: FloatingHubTab, expandIfNeeded: Boolean = false) {
        val defaultMode = if (expandIfNeeded || settings.value.defaultExpandedPanel || tab == FloatingHubTab.HISTORY || tab == FloatingHubTab.SETTINGS) {
            FloatingPanelMode.EXPANDED
        } else {
            FloatingPanelMode.COMPACT
        }
        _state.update {
            it.copy(
                isMenuOpen = false,
                isBubbleMinimized = false,
                isSelectingArea = false,
                activeHubTab = tab,
                isHistorySearchFocused = false,
                panelMode = defaultMode
            )
        }
    }

    fun selectHubTab(tab: FloatingHubTab) {
        _state.update {
            it.copy(
                activeHubTab = tab,
                isHistorySearchFocused = false
            )
        }
    }

    fun setHistorySearchFocused(focused: Boolean) {
        _state.update { it.copy(isHistorySearchFocused = focused) }
    }

    fun beginAreaSelection() {
        _state.update {
            it.copy(
                isMenuOpen = false,
                isBubbleMinimized = false,
                isSelectingArea = true,
                isHistorySearchFocused = false,
                panelMode = FloatingPanelMode.HIDDEN,
                activeHubTab = FloatingHubTab.ANSWER,
                status = NovaAssistantStatus.SCANNING,
                statusMessage = "Pilih area pertanyaan di layar",
                errorTitle = null,
                errorMessage = null
            )
        }
    }

    fun cancelAreaSelection() {
        _state.update { current ->
            current.copy(
                isSelectingArea = false,
                isCaptureInProgress = false,
                status = if (current.isFloatingServiceRunning) NovaAssistantStatus.ACTIVE else NovaAssistantStatus.INACTIVE,
                statusMessage = "Pemindaian dibatalkan"
            )
        }
    }

    fun setCaptureInProgress(capturing: Boolean) {
        _state.update {
            it.copy(
                isSelectingArea = false,
                isCaptureInProgress = capturing,
                status = NovaAssistantStatus.SCANNING,
                statusMessage = "Mengambil area layar terpilih..."
            )
        }
    }

    fun reportBlankAreaSelected() {
        _state.update {
            it.copy(
                isSelectingArea = false,
                isCaptureInProgress = false,
                activeHubTab = FloatingHubTab.ANSWER,
                panelMode = FloatingPanelMode.COMPACT,
                status = NovaAssistantStatus.ERROR,
                statusMessage = "Area kosong terdeteksi",
                errorTitle = "Area Pilihan Kosong",
                errorMessage = "Tidak ada teks yang terlihat pada area yang Anda pilih. Silakan pindai ulang area pertanyaan dengan kontras lebih jelas.",
                isMissingOpenAiKey = false
            )
        }
    }

    fun reportError(title: String, message: String) {
        _state.update {
            it.copy(
                isMenuOpen = false,
                isSelectingArea = false,
                isCaptureInProgress = false,
                activeHubTab = FloatingHubTab.ANSWER,
                panelMode = FloatingPanelMode.COMPACT,
                status = NovaAssistantStatus.ERROR,
                statusMessage = title,
                errorTitle = title,
                errorMessage = message,
                isMissingOpenAiKey = false
            )
        }
    }

    fun showToast(message: String) {
        _state.update { it.copy(toastMessage = message) }
    }

    fun clearToast() {
        _state.update { it.copy(toastMessage = null) }
    }

    /**
     * Runs Google ML Kit OCR on the temporary [bitmap] and recycles it immediately after extraction.
     * No screenshot bitmap is ever persisted to disk.
     */
    fun processCapturedBitmap(
        bitmap: Bitmap,
        sourceType: ScanSourceType,
        cropDurationMs: Long = 0L
    ) {
        activePipelineJob?.cancel()
        activePipelineJob = scope.launch {
            val defaultMode = if (settings.value.defaultExpandedPanel) {
                FloatingPanelMode.EXPANDED
            } else {
                FloatingPanelMode.COMPACT
            }

            _state.update {
                it.copy(
                    isMenuOpen = false,
                    isSelectingArea = false,
                    isCaptureInProgress = false,
                    isHistorySearchFocused = false,
                    activeHubTab = FloatingHubTab.ANSWER,
                    panelMode = defaultMode,
                    status = NovaAssistantStatus.PROCESSING,
                    statusMessage = "Membaca teks dengan ML Kit OCR...",
                    pendingSourceType = sourceType,
                    pendingCropMs = cropDurationMs,
                    errorTitle = null,
                    errorMessage = null,
                    isMissingOpenAiKey = false
                )
            }

            val ocrOutcome = try {
                ocrEngine.recognize(bitmap)
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

            when (ocrOutcome) {
                is OcrResult.Unreadable -> {
                    _state.update {
                        it.copy(
                            status = NovaAssistantStatus.ERROR,
                            statusMessage = "Teks tidak terbaca jelas",
                            pendingOcr = null,
                            errorTitle = "Teks Tidak Terbaca",
                            errorMessage = ocrOutcome.reason,
                            isMissingOpenAiKey = false
                        )
                    }
                }
                is OcrResult.Success -> {
                    val eval = ocrOutcome.evaluation
                    if (settings.value.confirmOcrBeforeSearch) {
                        _state.update {
                            it.copy(
                                status = NovaAssistantStatus.OCR_REVIEW,
                                statusMessage = "Periksa teks hasil OCR lalu konfirmasi",
                                pendingOcr = eval,
                                currentQuestion = eval.cleanedQuestion,
                                errorTitle = null,
                                errorMessage = null
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                pendingOcr = eval,
                                currentQuestion = eval.cleanedQuestion
                            )
                        }
                        runSearchAndAnswerPipeline(
                            question = eval.cleanedQuestion,
                            sourceType = sourceType,
                            cropMs = cropDurationMs,
                            ocrMs = eval.ocrDurationMs
                        )
                    }
                }
            }
        }
    }

    fun confirmOcrAndSearch(editedQuestion: String) {
        val trimmed = editedQuestion.trim()
        if (trimmed.length < 2) {
            showToast("Masukkan pertanyaan yang valid terlebih dahulu.")
            return
        }
        val current = _state.value
        runSearchAndAnswerPipeline(
            question = trimmed,
            sourceType = current.pendingSourceType,
            cropMs = current.pendingCropMs,
            ocrMs = current.pendingOcr?.ocrDurationMs ?: 0L
        )
    }

    fun submitDirectQuestion(question: String, sourceType: ScanSourceType = ScanSourceType.MANUAL_TEXT) {
        val trimmed = question.trim()
        if (trimmed.length < 2) {
            showToast("Tulis pertanyaan minimal 2 karakter.")
            return
        }
        val defaultMode = if (settings.value.defaultExpandedPanel) {
            FloatingPanelMode.EXPANDED
        } else {
            FloatingPanelMode.COMPACT
        }
        _state.update {
            it.copy(
                isMenuOpen = false,
                isHistorySearchFocused = false,
                activeHubTab = FloatingHubTab.ANSWER,
                panelMode = if (it.panelMode == FloatingPanelMode.EXPANDED) FloatingPanelMode.EXPANDED else defaultMode,
                pendingOcr = null,
                pendingSourceType = sourceType,
                pendingCropMs = 0L
            )
        }
        runSearchAndAnswerPipeline(
            question = trimmed,
            sourceType = sourceType,
            cropMs = 0L,
            ocrMs = 0L
        )
    }

    fun retryCurrentQuestion() {
        val current = _state.value
        val q = current.currentQuestion.ifBlank { current.latestAnswer?.question.orEmpty() }
        if (q.isBlank()) {
            beginAreaSelection()
            return
        }
        runSearchAndAnswerPipeline(
            question = q,
            sourceType = current.pendingSourceType,
            cropMs = current.pendingCropMs,
            ocrMs = current.pendingOcr?.ocrDurationMs ?: 0L
        )
    }

    private fun runSearchAndAnswerPipeline(
        question: String,
        sourceType: ScanSourceType,
        cropMs: Long,
        ocrMs: Long
    ) {
        activePipelineJob?.cancel()
        activePipelineJob = scope.launch {
            val activeSettings = settings.value
            val targetPanelMode = when (_state.value.panelMode) {
                FloatingPanelMode.EXPANDED -> FloatingPanelMode.EXPANDED
                FloatingPanelMode.COMPACT -> FloatingPanelMode.COMPACT
                else -> if (activeSettings.defaultExpandedPanel) {
                    FloatingPanelMode.EXPANDED
                } else {
                    FloatingPanelMode.COMPACT
                }
            }

            _state.update {
                it.copy(
                    activeHubTab = FloatingHubTab.ANSWER,
                    panelMode = targetPanelMode,
                    status = NovaAssistantStatus.PROCESSING,
                    statusMessage = "Mencari referensi Google...",
                    currentQuestion = question,
                    pendingOcr = null,
                    fallbackSources = emptyList(),
                    errorTitle = null,
                    errorMessage = null,
                    isMissingOpenAiKey = false
                )
            }

            val searchOutcome = apiRepository.searchWeb(question, activeSettings.answerLanguage)

            _state.update {
                it.copy(
                    statusMessage = "Menyusun jawaban NOVA AI (Groq)...",
                    fallbackSources = searchOutcome.results
                )
            }

            when (
                val aiOutcome = apiRepository.generateAnswer(
                    question = question,
                    sources = searchOutcome.results,
                    language = activeSettings.answerLanguage
                )
            ) {
                is AiAnswerOutcome.MissingApiKey -> {
                    _state.update {
                        it.copy(
                            status = NovaAssistantStatus.ERROR,
                            statusMessage = "OPENAI_API_KEY (Groq) diperlukan",
                            errorTitle = "Konfigurasi Groq API Diperlukan",
                            errorMessage = aiOutcome.message,
                            fallbackSources = searchOutcome.results,
                            isMissingOpenAiKey = true
                        )
                    }
                }
                is AiAnswerOutcome.Failure -> {
                    _state.update {
                        it.copy(
                            status = NovaAssistantStatus.ERROR,
                            statusMessage = "Gagal menghasilkan jawaban AI",
                            errorTitle = "Groq Answer Engine Gagal",
                            errorMessage = aiOutcome.message,
                            fallbackSources = searchOutcome.results,
                            isMissingOpenAiKey = false
                        )
                    }
                }
                is AiAnswerOutcome.Success -> {
                    val result = NovaAnswerResult(
                        question = question,
                        shortAnswer = aiOutcome.shortAnswer,
                        fullExplanation = aiOutcome.fullExplanation,
                        sources = searchOutcome.results,
                        searchProviderLabel = searchOutcome.providerLabel,
                        sourceType = sourceType,
                        insufficientInfo = aiOutcome.insufficientInfo,
                        timings = PipelineTimings(
                            cropMs = cropMs,
                            ocrMs = ocrMs,
                            searchMs = searchOutcome.durationMs,
                            aiMs = aiOutcome.durationMs
                        ),
                        timestamp = System.currentTimeMillis()
                    )

                    val savedId = if (activeSettings.saveHistoryLocally) {
                        runCatching { historyRepository.saveResult(result) }.getOrDefault(0L)
                    } else 0L

                    _state.update {
                        it.copy(
                            status = NovaAssistantStatus.COMPLETED,
                            statusMessage = "Selesai dalam ${result.timings.totalMs} ms",
                            latestAnswer = result.copy(id = savedId),
                            fallbackSources = searchOutcome.results,
                            errorTitle = null,
                            errorMessage = null,
                            isMissingOpenAiKey = false
                        )
                    }
                }
            }
        }
    }

    fun openHistoryAnswerInPanel(item: NovaAnswerResult) {
        _state.update {
            it.copy(
                currentQuestion = item.question,
                latestAnswer = item,
                fallbackSources = item.sources,
                pendingOcr = null,
                errorTitle = null,
                errorMessage = null,
                isHistorySearchFocused = false,
                activeHubTab = FloatingHubTab.ANSWER,
                status = NovaAssistantStatus.COMPLETED,
                statusMessage = "Riwayat pemindaian dimuat",
                panelMode = if (it.panelMode == FloatingPanelMode.EXPANDED) FloatingPanelMode.EXPANDED else FloatingPanelMode.COMPACT
            )
        }
    }

    fun deleteHistoryItem(id: Long) {
        scope.launch {
            historyRepository.deleteById(id)
            showToast("Riwayat dihapus.")
        }
    }

    fun clearAllHistory() {
        scope.launch {
            historyRepository.clearAll()
            showToast("Seluruh riwayat lokal dihapus.")
        }
    }

    fun updateBubbleOpacity(value: Float) {
        scope.launch { settingsRepository.updateBubbleOpacity(value) }
    }

    fun updateBubbleSizeDp(sizeDp: Int) {
        scope.launch { settingsRepository.updateBubbleSizeDp(sizeDp) }
    }

    fun updateConfirmOcrBeforeSearch(enabled: Boolean) {
        scope.launch { settingsRepository.updateConfirmOcrBeforeSearch(enabled) }
    }

    fun updateDefaultExpandedPanel(expanded: Boolean) {
        scope.launch { settingsRepository.updateDefaultExpandedPanel(expanded) }
    }

    fun updateAnswerLanguage(language: AnswerLanguage) {
        scope.launch { settingsRepository.updateAnswerLanguage(language) }
    }

    fun updateSaveHistoryLocally(enabled: Boolean) {
        scope.launch { settingsRepository.updateSaveHistoryLocally(enabled) }
    }

    fun restoreLastAnswerPanel() {
        _state.update { current ->
            current.copy(
                isMenuOpen = false,
                isBubbleMinimized = false,
                activeHubTab = FloatingHubTab.ANSWER,
                panelMode = FloatingPanelMode.COMPACT
            )
        }
    }

    fun toggleExpandPanel() {
        _state.update { current ->
            val next = if (current.panelMode == FloatingPanelMode.EXPANDED) {
                FloatingPanelMode.COMPACT
            } else {
                FloatingPanelMode.EXPANDED
            }
            current.copy(panelMode = next)
        }
    }

    fun minimizePanel() {
        _state.update {
            it.copy(
                panelMode = FloatingPanelMode.MINIMIZED,
                isMenuOpen = false,
                isHistorySearchFocused = false
            )
        }
    }

    fun closePanel() {
        activePipelineJob?.cancel()
        _state.update { current ->
            current.copy(
                panelMode = FloatingPanelMode.HIDDEN,
                isHistorySearchFocused = false,
                pendingOcr = null,
                errorTitle = null,
                errorMessage = null,
                status = if (current.isFloatingServiceRunning) NovaAssistantStatus.ACTIVE else NovaAssistantStatus.INACTIVE,
                statusMessage = if (current.isFloatingServiceRunning) "NOVA AI siap" else "Nonaktif"
            )
        }
    }
}
