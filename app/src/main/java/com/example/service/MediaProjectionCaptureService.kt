package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.NovaApplication
import com.example.R
import com.example.core.utils.BitmapCropper
import com.example.data.models.ScanSourceType

/**
 * Foreground service (foregroundServiceType="mediaProjection") managing a user-approved
 * MediaProjection capture session. Captures frames strictly on explicit user Scan tap,
 * crops to the exact physical screen rectangle, and immediately recycles temporary bitmaps.
 */
class MediaProjectionCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var surfaceDensityDpi: Int = 0

    private val sessionController by lazy {
        (application as NovaApplication).container.sessionController
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post {
                releaseProjectionResources()
                sessionController.setCaptureSessionReady(
                    ready = false,
                    message = "Sesi Screen Capture berakhir. Berikan izin ulang saat ingin memindai."
                )
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val startSelectionImmediately =
                    intent.getBooleanExtra(EXTRA_START_SELECTION_AFTER, false)

                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startCaptureForeground()
                    initializeProjection(resultCode, resultData, startSelectionImmediately)
                } else {
                    sessionController.setCaptureSessionReady(false)
                    stopSelf()
                }
            }
            ACTION_CAPTURE_AREA -> {
                val left = intent.getFloatExtra(EXTRA_RECT_LEFT, 0f)
                val top = intent.getFloatExtra(EXTRA_RECT_TOP, 0f)
                val right = intent.getFloatExtra(EXTRA_RECT_RIGHT, 0f)
                val bottom = intent.getFloatExtra(EXTRA_RECT_BOTTOM, 0f)
                val overlayW = intent.getIntExtra(EXTRA_OVERLAY_WIDTH, 0)
                val overlayH = intent.getIntExtra(EXTRA_OVERLAY_HEIGHT, 0)
                val offsetX = intent.getIntExtra(EXTRA_OVERLAY_OFFSET_X, 0)
                val offsetY = intent.getIntExtra(EXTRA_OVERLAY_OFFSET_Y, 0)

                performDelayedCapture(
                    rect = RectF(left, top, right, bottom),
                    overlayWidth = overlayW,
                    overlayHeight = overlayH,
                    offsetX = offsetX,
                    offsetY = offsetY
                )
            }
            ACTION_STOP_PROJECTION -> {
                releaseProjectionResources()
                sessionController.setCaptureSessionReady(false, "Sesi Screen Capture dihentikan.")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startCaptureForeground() {
        ensureNotificationChannel()
        val notification = buildCaptureNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initializeProjection(
        resultCode: Int,
        data: Intent,
        openSelectionImmediately: Boolean
    ) {
        releaseProjectionResources()
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data)
            if (projection == null) {
                sessionController.reportError(
                    "Izin Screen Capture Gagal",
                    "Sistem tidak mengembalikan sesi MediaProjection. Silakan coba lagi."
                )
                stopSelf()
                return
            }

            val metrics = resolveRealScreenMetrics()
            surfaceWidth = metrics.widthPixels.coerceAtLeast(360)
            surfaceHeight = metrics.heightPixels.coerceAtLeast(640)
            surfaceDensityDpi = metrics.densityDpi.coerceAtLeast(160)

            captureThread = HandlerThread("NovaMediaProjectionThread").apply { start() }
            captureHandler = Handler(captureThread!!.looper)

            projection.registerCallback(projectionCallback, captureHandler)

            val reader = ImageReader.newInstance(
                surfaceWidth,
                surfaceHeight,
                PixelFormat.RGBA_8888,
                2
            )

            val vDisplay = projection.createVirtualDisplay(
                "NovaAreaCapture",
                surfaceWidth,
                surfaceHeight,
                surfaceDensityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                captureHandler
            )

            mediaProjection = projection
            imageReader = reader
            virtualDisplay = vDisplay

            sessionController.setCaptureSessionReady(
                ready = true,
                message = "Sesi Scan Area aktif & siap digunakan."
            )

            if (openSelectionImmediately) {
                mainHandler.postDelayed({
                    sessionController.beginAreaSelection()
                }, 150L)
            }
        } catch (e: SecurityException) {
            releaseProjectionResources()
            sessionController.setCaptureSessionReady(false)
            sessionController.reportError(
                "Izin MediaProjection Dibatasi",
                "Sistem membatasi sesi capture (${e.localizedMessage ?: "SecurityException"}). Coba aktifkan ulang izin Scan Area."
            )
            stopSelf()
        } catch (e: Exception) {
            releaseProjectionResources()
            sessionController.setCaptureSessionReady(false)
            sessionController.reportError(
                "Gagal Memulai Capture",
                e.localizedMessage ?: "Tidak dapat menginisialisasi MediaProjection."
            )
            stopSelf()
        }
    }

    private fun performDelayedCapture(
        rect: RectF,
        overlayWidth: Int,
        overlayHeight: Int,
        offsetX: Int,
        offsetY: Int
    ) {
        val reader = imageReader
        if (mediaProjection == null || reader == null) {
            sessionController.setCaptureSessionReady(false)
            sessionController.reportError(
                "Sesi Capture Kedaluwarsa",
                "Sesi MediaProjection tidak lagi aktif. Ketuk Scan Area lagi untuk memberikan persetujuan baru."
            )
            return
        }

        // Wait ~140ms so the area selection overlay has cleanly hidden before capturing the frame
        captureHandler?.postDelayed({
            captureFrameWithRetry(
                reader = reader,
                rect = rect,
                overlayWidth = overlayWidth,
                overlayHeight = overlayHeight,
                offsetX = offsetX,
                offsetY = offsetY,
                attemptsRemaining = 6
            )
        }, 140L)
    }

    private fun captureFrameWithRetry(
        reader: ImageReader,
        rect: RectF,
        overlayWidth: Int,
        overlayHeight: Int,
        offsetX: Int,
        offsetY: Int,
        attemptsRemaining: Int
    ) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image == null) {
                if (attemptsRemaining > 0) {
                    captureHandler?.postDelayed({
                        captureFrameWithRetry(
                            reader, rect, overlayWidth, overlayHeight, offsetX, offsetY,
                            attemptsRemaining - 1
                        )
                    }, 80L)
                } else {
                    mainHandler.post {
                        sessionController.reportError(
                            "Frame Layar Tidak Tersedia",
                            "Layar mungkin dilindungi (FLAG_SECURE) atau sesi capture dijeda oleh sistem."
                        )
                    }
                }
                return
            }

            val fullBitmap = imageToCleanBitmap(image)
            image.close()
            image = null

            if (fullBitmap == null) {
                mainHandler.post {
                    sessionController.reportError(
                        "Gagal Mengonversi Frame",
                        "Tidak dapat membaca buffer gambar layar. Silakan coba lagi."
                    )
                }
                return
            }

            val cropResult = try {
                BitmapCropper.cropScreenArea(
                    source = fullBitmap,
                    selectionRect = rect,
                    overlayWidth = overlayWidth,
                    overlayHeight = overlayHeight,
                    overlayLeftOffsetPx = offsetX,
                    overlayTopOffsetPx = offsetY
                )
            } finally {
                if (!fullBitmap.isRecycled) {
                    fullBitmap.recycle()
                }
            }

            mainHandler.post {
                if (cropResult == null) {
                    sessionController.reportError(
                        "Area Terlalu Kecil",
                        "Kotak seleksi terlalu kecil untuk dipindai. Perbesar area pilihan Anda."
                    )
                } else if (cropResult.isMostlyUniformBlank) {
                    if (!cropResult.bitmap.isRecycled) cropResult.bitmap.recycle()
                    sessionController.reportBlankAreaSelected()
                } else {
                    sessionController.processCapturedBitmap(
                        bitmap = cropResult.bitmap,
                        sourceType = ScanSourceType.SCAN_AREA,
                        cropDurationMs = cropResult.cropDurationMs
                    )
                }
            }
        } catch (e: Exception) {
            runCatching { image?.close() }
            mainHandler.post {
                sessionController.reportError(
                    "Kesalahan Pemindaian Layar",
                    e.localizedMessage ?: "Gagal memotong gambar layar."
                )
            }
        }
    }

    private fun imageToCleanBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer ?: return null
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride

        val paddedBitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        paddedBitmap.copyPixelsFromBuffer(buffer)

        return if (paddedWidth == image.width) {
            paddedBitmap
        } else {
            val exact = Bitmap.createBitmap(paddedBitmap, 0, 0, image.width, image.height)
            paddedBitmap.recycle()
            exact
        }
    }

    private fun resolveRealScreenMetrics(): DisplayMetrics {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.densityDpi = resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }
        return metrics
    }

    private fun releaseProjectionResources() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        runCatching { captureThread?.quitSafely() }
        captureThread = null
        captureHandler = null
    }

    override fun onDestroy() {
        releaseProjectionResources()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_capture),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_capture_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildCaptureNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaProjectionCaptureService::class.java).setAction(ACTION_STOP_PROJECTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_capture_title))
            .setContentText(getString(R.string.notif_capture_text))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "nova_capture_session_channel"
        private const val NOTIFICATION_ID = 4102

        const val ACTION_START_PROJECTION = "com.example.nova.action.START_PROJECTION"
        const val ACTION_CAPTURE_AREA = "com.example.nova.action.CAPTURE_AREA"
        const val ACTION_STOP_PROJECTION = "com.example.nova.action.STOP_PROJECTION"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_START_SELECTION_AFTER = "extra_start_selection_after"

        const val EXTRA_RECT_LEFT = "extra_rect_left"
        const val EXTRA_RECT_TOP = "extra_rect_top"
        const val EXTRA_RECT_RIGHT = "extra_rect_right"
        const val EXTRA_RECT_BOTTOM = "extra_rect_bottom"
        const val EXTRA_OVERLAY_WIDTH = "extra_overlay_width"
        const val EXTRA_OVERLAY_HEIGHT = "extra_overlay_height"
        const val EXTRA_OVERLAY_OFFSET_X = "extra_overlay_offset_x"
        const val EXTRA_OVERLAY_OFFSET_Y = "extra_overlay_offset_y"

        fun startWithPermission(
            context: Context,
            resultCode: Int,
            data: Intent,
            startSelectionAfter: Boolean
        ) {
            val intent = Intent(context, MediaProjectionCaptureService::class.java).apply {
                action = ACTION_START_PROJECTION
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_START_SELECTION_AFTER, startSelectionAfter)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun requestAreaCapture(
            context: Context,
            rect: RectF,
            overlayWidth: Int,
            overlayHeight: Int,
            offsetX: Int,
            offsetY: Int
        ) {
            val intent = Intent(context, MediaProjectionCaptureService::class.java).apply {
                action = ACTION_CAPTURE_AREA
                putExtra(EXTRA_RECT_LEFT, rect.left)
                putExtra(EXTRA_RECT_TOP, rect.top)
                putExtra(EXTRA_RECT_RIGHT, rect.right)
                putExtra(EXTRA_RECT_BOTTOM, rect.bottom)
                putExtra(EXTRA_OVERLAY_WIDTH, overlayWidth)
                putExtra(EXTRA_OVERLAY_HEIGHT, overlayHeight)
                putExtra(EXTRA_OVERLAY_OFFSET_X, offsetX)
                putExtra(EXTRA_OVERLAY_OFFSET_Y, offsetY)
            }
            context.startService(intent)
        }

        fun stopSession(context: Context) {
            val intent = Intent(context, MediaProjectionCaptureService::class.java).apply {
                action = ACTION_STOP_PROJECTION
            }
            context.startService(intent)
        }
    }
}
