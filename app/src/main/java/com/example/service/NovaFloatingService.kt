package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.NovaApplication
import com.example.R
import com.example.core.permissions.PermissionHelper
import com.example.data.models.FloatingHubTab
import com.example.data.models.FloatingPanelMode
import com.example.data.models.NovaAssistantStatus
import com.example.features.floating.AreaSelectionOverlay
import com.example.features.floating.FloatingAnswerPanelOverlay
import com.example.features.floating.FloatingBubbleOverlay
import com.example.features.floating.FloatingMenuOverlay
import com.example.ui.theme.NovaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Full-Floating Foreground Service (foregroundServiceType="specialUse") hosting all NOVA AI
 * WindowManager overlays: Floating Bubble, Quick Menu, Area Selection Viewfinder, and the
 * unified 4-tab Floating Hub Panel (Jawaban, Tanya, Riwayat, Pengaturan).
 */
class NovaFloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val sessionController by lazy {
        (application as NovaApplication).container.sessionController
    }

    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private var bubbleView: ComposeView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var menuView: ComposeView? = null
    private var selectionView: ComposeView? = null

    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelCurrentlyFocusable: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAssistantCompletely()
                return START_NOT_STICKY
            }
            ACTION_START_SCAN_AREA -> {
                ensureRunning()
                handleScanAreaRequested()
                return START_STICKY
            }
            ACTION_OPEN_HUB -> {
                ensureRunning()
                sessionController.openHubTab(FloatingHubTab.ANSWER)
                return START_STICKY
            }
            else -> {
                val openMenuOnLaunch = intent?.getBooleanExtra(EXTRA_OPEN_MENU_ON_START, false) ?: false
                ensureRunning()
                if (openMenuOnLaunch) {
                    sessionController.toggleMenu()
                }
                return START_STICKY
            }
        }
    }

    private fun ensureRunning() {
        if (!PermissionHelper.hasOverlayPermission(this)) {
            sessionController.setFloatingServiceRunning(false)
            Toast.makeText(
                this,
                "Izin tampil di atas aplikasi lain diperlukan untuk Floating NOVA.",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return
        }

        startOverlayForeground()

        if (lifecycleOwner == null) {
            lifecycleOwner = OverlayLifecycleOwner().apply { onResume() }
            attachBubbleView()
            observeSessionState()
        }
        sessionController.setFloatingServiceRunning(true)
    }

    private fun startOverlayForeground() {
        ensureNotificationChannel()
        val notification = buildFloatingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeSessionState() {
        serviceScope.launch {
            sessionController.state.collectLatest { state ->
                state.toastMessage?.let { msg ->
                    Toast.makeText(this@NovaFloatingService, msg, Toast.LENGTH_SHORT).show()
                    sessionController.clearToast()
                }

                // Hide bubble while selecting area or grabbing MediaProjection frame
                bubbleView?.visibility = if (state.isSelectingArea || state.isCaptureInProgress) {
                    View.GONE
                } else {
                    View.VISIBLE
                }

                if (state.isMenuOpen && !state.isSelectingArea) {
                    attachMenuView()
                } else {
                    detachMenuView()
                }

                if (state.isSelectingArea) {
                    attachSelectionView()
                } else {
                    detachSelectionView()
                }

                val showPanel = (state.panelMode == FloatingPanelMode.COMPACT ||
                    state.panelMode == FloatingPanelMode.EXPANDED) &&
                    !state.isSelectingArea && !state.isCaptureInProgress

                if (showPanel) {
                    val needsKeyboardFocus = (state.activeHubTab == FloatingHubTab.ASK) ||
                        (state.activeHubTab == FloatingHubTab.ANSWER && state.status == NovaAssistantStatus.OCR_REVIEW) ||
                        (state.activeHubTab == FloatingHubTab.HISTORY && state.isHistorySearchFocused)

                    attachOrUpdatePanelView(needsKeyboardFocus = needsKeyboardFocus)
                } else {
                    detachPanelView()
                }
            }
        }
    }

    private fun attachBubbleView() {
        if (bubbleView != null) return
        val owner = lifecycleOwner ?: return

        val screenWidth = resources.displayMetrics.widthPixels
        val params = createOverlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - (74 * resources.displayMetrics.density).roundToInt()
            y = (resources.displayMetrics.heightPixels * 0.34f).roundToInt()
        }

        val view = createComposeOverlayView(owner) {
            NovaTheme(darkTheme = true) {
                val state by sessionController.state.collectAsState()
                val settings by sessionController.settings.collectAsState()
                FloatingBubbleOverlay(
                    sessionState = state,
                    settings = settings,
                    onTap = {
                        if (state.panelMode == FloatingPanelMode.MINIMIZED) {
                            sessionController.restoreLastAnswerPanel()
                        } else {
                            sessionController.toggleMenu()
                        }
                    },
                    onDrag = { dx, dy ->
                        val p = bubbleParams ?: return@FloatingBubbleOverlay
                        p.x += dx.roundToInt()
                        p.y = (p.y + dy.roundToInt()).coerceIn(
                            0,
                            (resources.displayMetrics.heightPixels - 120).coerceAtLeast(0)
                        )
                        safeUpdateView(bubbleView, p)
                    },
                    onDragEnd = {
                        val p = bubbleParams ?: return@FloatingBubbleOverlay
                        val sw = resources.displayMetrics.widthPixels
                        val bubbleW = bubbleView?.width ?: 140
                        val snapRight = p.x + bubbleW / 2 >= sw / 2
                        p.x = if (snapRight) (sw - bubbleW - 8).coerceAtLeast(0) else 8
                        safeUpdateView(bubbleView, p)
                    }
                )
            }
        }

        bubbleParams = params
        bubbleView = view
        safeAddView(view, params)
    }

    private fun attachMenuView() {
        if (menuView != null) return
        val owner = lifecycleOwner ?: return

        val params = createOverlayParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )

        val view = createComposeOverlayView(owner) {
            NovaTheme(darkTheme = true) {
                val state by sessionController.state.collectAsState()
                val history by sessionController.history.collectAsState()
                FloatingMenuOverlay(
                    sessionState = state,
                    isScreenPinned = PermissionHelper.isScreenPinningActive(this@NovaFloatingService),
                    historyCount = history.size,
                    onDismiss = { sessionController.closeMenu() },
                    onScanArea = { handleScanAreaRequested() },
                    onGallery = { handleGalleryRequested() },
                    onOpenHubTab = { tab -> sessionController.openHubTab(tab) },
                    onMinimizeBubble = { sessionController.toggleMinimizeBubble() },
                    onStopAssistant = { stopAssistantCompletely() }
                )
            }
        }

        menuView = view
        safeAddView(view, params)
    }

    private fun detachMenuView() {
        safeRemoveView(menuView)
        menuView = null
    }

    private fun attachSelectionView() {
        if (selectionView != null) return
        val owner = lifecycleOwner ?: return

        val params = createOverlayParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val view = createComposeOverlayView(owner) {
            NovaTheme(darkTheme = true) {
                AreaSelectionOverlay(
                    onCancel = { sessionController.cancelAreaSelection() },
                    onConfirmScan = { rect, overlayW, overlayH ->
                        triggerAreaCapture(rect, overlayW, overlayH)
                    }
                )
            }
        }

        selectionView = view
        safeAddView(view, params)
    }

    private fun detachSelectionView() {
        safeRemoveView(selectionView)
        selectionView = null
    }

    private fun attachOrUpdatePanelView(needsKeyboardFocus: Boolean) {
        val owner = lifecycleOwner ?: return

        if (panelView == null) {
            val flags = buildPanelWindowFlags(needsKeyboardFocus)
            val params = createOverlayParams(
                width = WindowManager.LayoutParams.WRAP_CONTENT,
                height = WindowManager.LayoutParams.WRAP_CONTENT,
                flags = flags
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (resources.displayMetrics.heightPixels * 0.12f).roundToInt()
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

            val view = createComposeOverlayView(owner) {
                NovaTheme(darkTheme = true) {
                    val state by sessionController.state.collectAsState()
                    val settings by sessionController.settings.collectAsState()
                    val history by sessionController.history.collectAsState()

                    FloatingAnswerPanelOverlay(
                        sessionState = state,
                        settings = settings,
                        history = history,
                        apiConfig = sessionController.getApiConfigStatus(),
                        isScreenPinned = PermissionHelper.isScreenPinningActive(this@NovaFloatingService),
                        onSelectTab = { sessionController.selectHubTab(it) },
                        onHistorySearchFocusChanged = { sessionController.setHistorySearchFocused(it) },
                        onDragPanel = { dx, dy ->
                            val p = panelParams ?: return@FloatingAnswerPanelOverlay
                            p.x += dx.roundToInt()
                            p.y = (p.y + dy.roundToInt()).coerceIn(
                                0,
                                (resources.displayMetrics.heightPixels - 180).coerceAtLeast(0)
                            )
                            safeUpdateView(panelView, p)
                        },
                        onSubmitDirectQuestion = { q -> sessionController.submitDirectQuestion(q) },
                        onConfirmOcr = { edited -> sessionController.confirmOcrAndSearch(edited) },
                        onRescan = { handleScanAreaRequested() },
                        onOpenGallery = { handleGalleryRequested() },
                        onCopyAnswer = { text ->
                            PermissionHelper.copyToClipboard(this@NovaFloatingService, "NOVA AI", text)
                            sessionController.showToast("Disalin ke clipboard.")
                        },
                        onRetry = { sessionController.retryCurrentQuestion() },
                        onToggleExpand = { sessionController.toggleExpandPanel() },
                        onMinimize = { sessionController.minimizePanel() },
                        onClose = { sessionController.closePanel() },
                        onOpenUrl = { url ->
                            if (!PermissionHelper.openUrlInBrowser(this@NovaFloatingService, url)) {
                                sessionController.showToast("Tidak dapat membuka browser saat ini.")
                            }
                        },
                        onOpenGoogleSearch = { q ->
                            if (!PermissionHelper.openGoogleSearchFallback(this@NovaFloatingService, q)) {
                                sessionController.showToast("Browser dibatasi oleh mode sistem saat ini.")
                            }
                        },
                        onLoadHistoryItem = { item -> sessionController.openHistoryAnswerInPanel(item) },
                        onDeleteHistoryItem = { id -> sessionController.deleteHistoryItem(id) },
                        onClearHistory = { sessionController.clearAllHistory() },
                        onBubbleOpacityChange = { sessionController.updateBubbleOpacity(it) },
                        onBubbleSizeChange = { sessionController.updateBubbleSizeDp(it) },
                        onConfirmOcrSettingChange = { sessionController.updateConfirmOcrBeforeSearch(it) },
                        onDefaultExpandedChange = { sessionController.updateDefaultExpandedPanel(it) },
                        onLanguageChange = { sessionController.updateAnswerLanguage(it) },
                        onSaveHistoryChange = { sessionController.updateSaveHistoryLocally(it) },
                        onActivateCaptureSession = { handleActivateCaptureOnly() },
                        onStopCaptureSession = { MediaProjectionCaptureService.stopSession(this@NovaFloatingService) }
                    )
                }
            }

            panelParams = params
            panelView = view
            panelCurrentlyFocusable = needsKeyboardFocus
            safeAddView(view, params)
        } else if (panelCurrentlyFocusable != needsKeyboardFocus) {
            val params = panelParams ?: return
            params.flags = buildPanelWindowFlags(needsKeyboardFocus)
            panelCurrentlyFocusable = needsKeyboardFocus
            safeUpdateView(panelView, params)
        }
    }

    private fun buildPanelWindowFlags(needsKeyboardFocus: Boolean): Int {
        return if (needsKeyboardFocus) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
    }

    private fun detachPanelView() {
        safeRemoveView(panelView)
        panelView = null
        panelParams = null
        panelCurrentlyFocusable = false
    }

    private fun handleActivateCaptureOnly() {
        if (PermissionHelper.isScreenPinningActive(this)) {
            sessionController.showToast("Nonaktifkan Screen Pinning sebentar untuk memberi izin Screen Capture.")
            return
        }
        CapturePermissionActivity.launch(this, openSelectionAfter = false)
    }

    private fun handleScanAreaRequested() {
        sessionController.closeMenu()
        if (sessionController.state.value.isCaptureSessionReady) {
            sessionController.beginAreaSelection()
        } else {
            if (PermissionHelper.isScreenPinningActive(this)) {
                sessionController.reportError(
                    "Sesi Capture Belum Aktif",
                    "Mode Screen Pinning aktif. Aktifkan sesi Screen Capture di Tab Pengaturan NOVA sebelum menyematkan layar agar Scan Area internal berjalan langsung."
                )
            } else {
                CapturePermissionActivity.launch(this, openSelectionAfter = true)
            }
        }
    }

    private fun triggerAreaCapture(rect: RectF, overlayWidth: Int, overlayHeight: Int) {
        val loc = IntArray(2)
        selectionView?.getLocationOnScreen(loc)
        val offsetX = loc[0]
        val offsetY = loc[1]

        sessionController.setCaptureInProgress(true)
        detachSelectionView()

        MediaProjectionCaptureService.requestAreaCapture(
            context = this,
            rect = rect,
            overlayWidth = overlayWidth,
            overlayHeight = overlayHeight,
            offsetX = offsetX,
            offsetY = offsetY
        )
    }

    private fun handleGalleryRequested() {
        sessionController.closeMenu()
        if (PermissionHelper.isScreenPinningActive(this)) {
            sessionController.reportError(
                "Galeri Dibatasi Screen Pinning",
                "Android Photo Picker dibatasi saat Screen Pinning aktif. Gunakan Scan Area internal NOVA selama sesi capture aktif."
            )
            return
        }
        GalleryPickerActivity.launch(this)
    }

    private fun stopAssistantCompletely() {
        MediaProjectionCaptureService.stopSession(this)
        cleanupAllOverlays()
        sessionController.setFloatingServiceRunning(false)
        stopSelf()
    }

    private fun cleanupAllOverlays() {
        detachMenuView()
        detachSelectionView()
        detachPanelView()
        safeRemoveView(bubbleView)
        bubbleView = null
        bubbleParams = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    private fun createComposeOverlayView(
        owner: OverlayLifecycleOwner,
        content: @androidx.compose.runtime.Composable () -> Unit
    ): ComposeView {
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent(content)
        }
    }

    private fun createOverlayParams(
        width: Int,
        height: Int,
        flags: Int
    ): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun safeAddView(view: View, params: WindowManager.LayoutParams) {
        runCatching { windowManager.addView(view, params) }
    }

    private fun safeUpdateView(view: View?, params: WindowManager.LayoutParams) {
        if (view != null && view.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun safeRemoveView(view: View?) {
        if (view != null && view.isAttachedToWindow) {
            runCatching { windowManager.removeView(view) }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        cleanupAllOverlays()
        sessionController.setFloatingServiceRunning(false)
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_floating),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_floating_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildFloatingNotification(): Notification {
        // Tapping notification opens the Floating Hub directly without launching a full-screen activity
        val openHubIntent = PendingIntent.getService(
            this,
            10,
            Intent(this, NovaFloatingService::class.java).setAction(ACTION_OPEN_HUB),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, NovaFloatingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_floating_title))
            .setContentText(getString(R.string.notif_floating_text))
            .setOngoing(true)
            .setContentIntent(openHubIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "nova_floating_assistant_channel"
        private const val NOTIFICATION_ID = 4101

        const val ACTION_START = "com.example.nova.action.START_FLOATING"
        const val ACTION_STOP = "com.example.nova.action.STOP_FLOATING"
        const val ACTION_START_SCAN_AREA = "com.example.nova.action.START_SCAN_AREA"
        const val ACTION_OPEN_HUB = "com.example.nova.action.OPEN_HUB"
        private const val EXTRA_OPEN_MENU_ON_START = "extra_open_menu_on_start"

        fun start(context: Context, openMenuImmediately: Boolean = false) {
            val intent = Intent(context, NovaFloatingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OPEN_MENU_ON_START, openMenuImmediately)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startScanArea(context: Context) {
            val intent = Intent(context, NovaFloatingService::class.java).setAction(ACTION_START_SCAN_AREA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NovaFloatingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
