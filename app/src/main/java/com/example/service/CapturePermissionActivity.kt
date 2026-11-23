package com.example.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.NovaApplication

/**
 * Translucent trampoline Activity launched when Scan Area needs fresh MediaProjection user consent.
 */
class CapturePermissionActivity : ComponentActivity() {

    private var openSelectionAfter: Boolean = true

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val controller = (application as NovaApplication).container.sessionController
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionCaptureService.startWithPermission(
                context = this,
                resultCode = result.resultCode,
                data = result.data!!,
                startSelectionAfter = openSelectionAfter
            )
        } else {
            controller.setCaptureSessionReady(false)
            controller.showToast("Izin Screen Capture dibatalkan pengguna.")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openSelectionAfter = intent.getBooleanExtra(EXTRA_OPEN_SELECTION_AFTER, true)

        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        } catch (e: Exception) {
            val controller = (application as NovaApplication).container.sessionController
            controller.reportError(
                "Tidak Dapat Meminta Izin Capture",
                "Sistem atau mode Screen Pinning memblokir dialog izin saat ini. Aktifkan sesi Screen Capture dari NOVA sebelum mengaktifkan Screen Pinning."
            )
            finish()
        }
    }

    companion object {
        private const val EXTRA_OPEN_SELECTION_AFTER = "extra_open_selection_after"

        fun launch(context: Context, openSelectionAfter: Boolean) {
            val intent = Intent(context, CapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_OPEN_SELECTION_AFTER, openSelectionAfter)
            }
            context.startActivity(intent)
        }
    }
}
