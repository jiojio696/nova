package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.permissions.PermissionHelper
import com.example.service.NovaFloatingService
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.NovaStatusCompleted
import com.example.ui.theme.NovaTheme
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid

/**
 * Smart Auto-Floating Launcher Gate:
 * - When overlay permission is ALREADY granted, immediately starts NovaFloatingService
 *   (spawning the Floating Bubble) and moves cleanly to background so all features run 100% floating.
 * - When overlay permission is not yet granted, displays a minimal Obsidian Prism permission gate
 *   and auto-launches into floating mode the instant overlay permission is enabled.
 */
class MainActivity : ComponentActivity() {

    private var hasNotificationPermission by mutableStateOf(true)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        hasNotificationPermission = PermissionHelper.hasNotificationPermission(this)
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (PermissionHelper.hasOverlayPermission(this)) {
            launchFloatingAndExitToBackground()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Zero-friction full-floating launch when overlay permission is already granted
        if (PermissionHelper.hasOverlayPermission(this)) {
            launchFloatingAndExitToBackground()
            return
        }

        hasNotificationPermission = PermissionHelper.hasNotificationPermission(this)
        enableEdgeToEdge()

        setContent {
            NovaTheme(darkTheme = true) {
                FloatingPermissionGateScreen(
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestOverlay = {
                        overlayPermissionLauncher.launch(
                            PermissionHelper.createOverlaySettingsIntent(this)
                        )
                    },
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasNotificationPermission = PermissionHelper.hasNotificationPermission(this)
        if (PermissionHelper.hasOverlayPermission(this)) {
            launchFloatingAndExitToBackground()
        }
    }

    private fun launchFloatingAndExitToBackground() {
        NovaFloatingService.start(this, openMenuImmediately = false)
        moveTaskToBack(true)
        finish()
    }
}

@Composable
private fun FloatingPermissionGateScreen(
    hasNotificationPermission: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianVoid,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .testTag("floating_permission_gate"),
                shape = RoundedCornerShape(26.dp),
                color = ObsidianSurface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(26.dp))
                ) {
                    // Hero top visual
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_nova_hero_banner_1795430099062),
                            contentDescription = stringResource(R.string.cd_hero_banner),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0x55070B14), ObsidianSurface)
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "NOVA AI",
                                style = MaterialTheme.typography.displaySmall.copy(color = Color.White)
                            )
                            Text(
                                text = "100% Full-Floating Assistant • Scan, OCR, Search & Groq AI",
                                style = MaterialTheme.typography.bodySmall.copy(color = CyanPulse)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Berikan izin Overlay agar seluruh fitur NOVA AI (Scan Area, Galeri, Tanya, Riwayat, dan Pengaturan) langsung mengambang di atas aplikasi lain.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFCBD5E1))
                        )

                        // Overlay permission requirement card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianCard)
                                .border(1.dp, CyanPulse.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = CyanPulse,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Izin Tampil di Atas Aplikasi Lain (Wajib)",
                                    style = MaterialTheme.typography.titleSmall.copy(color = Color.White)
                                )
                                Text(
                                    text = "Setelah diaktifkan, NOVA langsung memunculkan Floating Bubble & berpindah ke latar.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                                )
                            }
                        }

                        // Optional Notification permission row (Android 13+)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianCard)
                                .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = if (hasNotificationPermission) NovaStatusCompleted else Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notifikasi Indikator Layanan",
                                    style = MaterialTheme.typography.titleSmall.copy(color = Color.White)
                                )
                                Text(
                                    text = if (hasNotificationPermission) "Sudah diizinkan" else "Menampilkan kontrol cepat & status aktif",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                                )
                            }
                            if (hasNotificationPermission) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Diizinkan",
                                    tint = NovaStatusCompleted,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                OutlinedButton(
                                    onClick = onRequestNotification,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("gate_request_notif_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Izinkan", color = CyanPulse)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Button(
                            onClick = onRequestOverlay,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("gate_enable_overlay_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPulse,
                                contentColor = ObsidianVoid
                            )
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Aktifkan Izin Overlay & Mulai Floating",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}
