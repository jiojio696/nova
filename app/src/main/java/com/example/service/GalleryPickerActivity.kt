package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.NovaApplication
import com.example.core.utils.BitmapCropper
import com.example.data.models.ScanSourceType

/**
 * Translucent trampoline Activity invoking Android's zero-permission Photo Picker
 * (ActivityResultContracts.PickVisualMedia) from the Floating Menu.
 */
class GalleryPickerActivity : ComponentActivity() {

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val controller = (application as NovaApplication).container.sessionController
        if (uri != null) {
            val bitmap = BitmapCropper.decodeSampledBitmapFromUri(this, uri)
            if (bitmap != null) {
                controller.processCapturedBitmap(
                    bitmap = bitmap,
                    sourceType = ScanSourceType.GALLERY,
                    cropDurationMs = 0L
                )
            } else {
                controller.reportError(
                    "Gambar Tidak Dapat Dibaca",
                    "Format gambar galeri tidak didukung atau rusak. Silakan pilih gambar JPG/PNG lain."
                )
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (_: Exception) {
            val controller = (application as NovaApplication).container.sessionController
            controller.reportError(
                "Galeri Dibatasi Sistem",
                "Android Photo Picker tidak dapat dibuka (kemungkinan karena Screen Pinning aktif). Gunakan Scan Area internal sebagai alternatif."
            )
            finish()
        }
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, GalleryPickerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
