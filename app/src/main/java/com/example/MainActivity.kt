package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.auth.GoogleAuthHelper
import com.example.core.permissions.PermissionHelper
import com.example.data.models.AdminLicenseItem
import com.example.data.models.LicenseAccessState
import com.example.data.models.LicenseKeyDoc
import com.example.features.admin.AdminPanelScreen
import com.example.features.admin.AdminPanelViewModel
import com.example.features.admin.AdminPinDialog
import com.example.features.auth.GoogleSignInScreen
import com.example.features.license.LicenseGateScreen
import com.example.service.NovaFloatingService
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NovaStatusCompleted
import com.example.ui.theme.NovaTheme
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

/**
 * Auth-gated, License-gated Auto-Floating Launcher:
 * 1. Google Sign-In Gate (Credential Manager with silent auto sign-in + interactive fallback)
 * 2. Single-Device License Key Gate (atomic Firestore transaction + real-time expiry monitoring)
 * 3. Hidden 5-tap PIN-protected Admin Panel (create/manage keys, daily expiry, device reset, buyer notes)
 * 4. Overlay Permission Gate -> Auto-launch into 100% Full-Floating NOVA AI mode.
 */
class MainActivity : ComponentActivity() {

    private var hasNotificationPermission by mutableStateOf(true)
    private var hasOverlayPermission by mutableStateOf(false)
    private var openAdminPinRequested by mutableStateOf(false)
    private var incomingLicenseReason by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        hasNotificationPermission = PermissionHelper.hasNotificationPermission(this)
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = PermissionHelper.hasOverlayPermission(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        hasNotificationPermission = PermissionHelper.hasNotificationPermission(this)
        hasOverlayPermission = PermissionHelper.hasOverlayPermission(this)
        enableEdgeToEdge()

        val container = (application as NovaApplication).container

        setContent {
            NovaTheme(darkTheme = true) {
                NovaRootGate(
                    container = container,
                    hasOverlayPermission = hasOverlayPermission,
                    hasNotificationPermission = hasNotificationPermission,
                    openAdminPinRequested = openAdminPinRequested,
                    incomingLicenseReason = incomingLicenseReason,
                    onConsumeAdminPinRequest = { openAdminPinRequested = false },
                    onRequestOverlay = {
                        overlayPermissionLauncher.launch(
                            PermissionHelper.createOverlaySettingsIntent(this)
                        )
                    },
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onLaunchFloatingAndExit = {
                        launchFloatingAndExitToBackground()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        hasNotificationPermission = PermissionHelper.hasNotificationPermission(this)
        hasOverlayPermission = PermissionHelper.hasOverlayPermission(this)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_ADMIN_PIN_GATE, false)) {
            openAdminPinRequested = true
            intent.removeExtra(EXTRA_OPEN_ADMIN_PIN_GATE)
        }
        val reason = intent.getStringExtra(EXTRA_LICENSE_REASON)
        if (!reason.isNullOrBlank()) {
            incomingLicenseReason = reason
            intent.removeExtra(EXTRA_LICENSE_REASON)
        }
    }

    private fun launchFloatingAndExitToBackground() {
        NovaFloatingService.start(this, openMenuImmediately = false)
        moveTaskToBack(true)
        finish()
    }

    companion object {
        const val EXTRA_OPEN_ADMIN_PIN_GATE = "extra_open_admin_pin_gate"
        const val EXTRA_LICENSE_REASON = "extra_license_reason"

        fun createLicenseGateIntent(context: Context, reason: String? = null): Intent {
            return Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (!reason.isNullOrBlank()) {
                    putExtra(EXTRA_LICENSE_REASON, reason)
                }
            }
        }

        fun createAdminPinGateIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_OPEN_ADMIN_PIN_GATE, true)
            }
        }
    }
}

@Composable
private fun NovaRootGate(
    container: NovaContainer,
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    openAdminPinRequested: Boolean,
    incomingLicenseReason: String?,
    onConsumeAdminPinRequest: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onLaunchFloatingAndExit: () -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    if (currentUser == null) {
        GoogleSignInScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = currentUser!!.uid
        val email = currentUser!!.email

        key(uid) {
            AuthenticatedLicenseFlow(
                uid = uid,
                userEmail = email,
                container = container,
                hasOverlayPermission = hasOverlayPermission,
                hasNotificationPermission = hasNotificationPermission,
                openAdminPinRequested = openAdminPinRequested,
                incomingLicenseReason = incomingLicenseReason,
                onConsumeAdminPinRequest = onConsumeAdminPinRequest,
                onRequestOverlay = onRequestOverlay,
                onRequestNotification = onRequestNotification,
                onLaunchFloatingAndExit = onLaunchFloatingAndExit
            )
        }
    }
}

@Composable
private fun AuthenticatedLicenseFlow(
    uid: String,
    userEmail: String?,
    container: NovaContainer,
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    openAdminPinRequested: Boolean,
    incomingLicenseReason: String?,
    onConsumeAdminPinRequest: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onLaunchFloatingAndExit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val accessState by container.licenseManager.accessState.collectAsStateWithLifecycle()

    var showAdminPinDialog by remember { mutableStateOf(false) }
    var showAdminPanel by remember { mutableStateOf(false) }
    var suppressAutoFloating by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        container.licenseManager.startObservingForUser(uid)
    }

    LaunchedEffect(openAdminPinRequested) {
        if (openAdminPinRequested) {
            suppressAutoFloating = true
            showAdminPinDialog = true
            onConsumeAdminPinRequest()
        }
    }

    // Auto-launch Floating Service seamlessly when licensed & overlay permission is granted
    // (unless admin explicitly opened MainActivity to access the PIN Gate / Admin Panel)
    LaunchedEffect(accessState, hasOverlayPermission, showAdminPinDialog, showAdminPanel, suppressAutoFloating) {
        if (accessState is LicenseAccessState.Licensed &&
            hasOverlayPermission &&
            !showAdminPinDialog &&
            !showAdminPanel &&
            !suppressAutoFloating
        ) {
            onLaunchFloatingAndExit()
        }
    }

    if (showAdminPanel) {
        val adminViewModel: AdminPanelViewModel = viewModel(
            key = uid,
            factory = AdminPanelViewModel.Factory(container.licenseRepository)
        )
        AdminPanelScreen(
            viewModel = adminViewModel,
            onNavigateBack = {
                showAdminPanel = false
                suppressAutoFloating = false
            }
        )
    } else {
        when (val state = accessState) {
            is LicenseAccessState.Loading -> {
                LicenseCheckingScreen()
            }

            is LicenseAccessState.Unlicensed -> {
                LicenseGateScreen(
                    licenseManager = container.licenseManager,
                    userEmail = userEmail,
                    statusReason = state.reason ?: incomingLicenseReason,
                    onHiddenAdminTrigger = {
                        suppressAutoFloating = true
                        showAdminPinDialog = true
                    },
                    onSignOutClick = {
                        GoogleAuthHelper.signOut(
                            credentialManager = credentialManager,
                            scope = scope,
                            onBeforeSignOut = { container.licenseManager.stopObserving() }
                        )
                    }
                )
            }

            is LicenseAccessState.Licensed -> {
                FloatingPermissionGateScreen(
                    activeLicense = state.license,
                    hasOverlayPermission = hasOverlayPermission,
                    hasNotificationPermission = hasNotificationPermission,
                    onRequestOverlay = onRequestOverlay,
                    onRequestNotification = onRequestNotification,
                    onLaunchFloating = onLaunchFloatingAndExit,
                    onHiddenAdminTrigger = {
                        suppressAutoFloating = true
                        showAdminPinDialog = true
                    }
                )
            }
        }
    }

    if (showAdminPinDialog) {
        AdminPinDialog(
            licenseRepository = container.licenseRepository,
            onDismiss = {
                showAdminPinDialog = false
                suppressAutoFloating = false
            },
            onUnlocked = {
                showAdminPinDialog = false
                showAdminPanel = true
            }
        )
    }
}

@Composable
private fun LicenseCheckingScreen() {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianVoid,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(color = CyanPulse)
                Text(
                    text = "Memverifikasi lisensi perangkat...",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFCBD5E1))
                )
            }
        }
    }
}

@Composable
private fun FloatingPermissionGateScreen(
    activeLicense: LicenseKeyDoc,
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onLaunchFloating: () -> Unit,
    onHiddenAdminTrigger: () -> Unit
) {
    var secretTapCount by remember { mutableIntStateOf(0) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    val remainingText = remember(activeLicense) {
        AdminLicenseItem(activeLicense).formatRemainingLabel()
    }

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
                    // Hero top visual (also supports hidden 5-tap admin trigger)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val now = System.currentTimeMillis()
                                secretTapCount = if (now - lastTapTimestamp > 2_000L) 1 else secretTapCount + 1
                                lastTapTimestamp = now
                                if (secretTapCount >= 5) {
                                    secretTapCount = 0
                                    onHiddenAdminTrigger()
                                }
                            }
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
                        // Active License verified badge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianCard)
                                .border(1.dp, NovaStatusCompleted.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null,
                                tint = NovaStatusCompleted,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lisensi Aktif: ${activeLicense.keyCode}",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = remainingText,
                                    style = MaterialTheme.typography.bodySmall.copy(color = NovaStatusCompleted)
                                )
                            }
                        }

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
                            onClick = if (hasOverlayPermission) onLaunchFloating else onRequestOverlay,
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
                                text = if (hasOverlayPermission) "Mulai Floating NOVA" else "Aktifkan Izin Overlay & Mulai Floating",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}
