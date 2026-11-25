package com.example.features.license

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.models.KeyActivationResult
import com.example.domain.LicenseManager
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NovaStatusError
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import kotlinx.coroutines.launch

@Composable
fun LicenseGateScreen(
    licenseManager: LicenseManager,
    userEmail: String?,
    statusReason: String?,
    onHiddenAdminTrigger: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var keyInput by remember { mutableStateOf("") }
    var isActivating by remember { mutableStateOf(false) }
    var errorMessage by remember(statusReason) { mutableStateOf(statusReason) }
    var deviceShortId by remember { mutableStateOf("...") }

    // Hidden 5-tap trigger state (no visual affordance for normal users)
    var secretTapCount by remember { mutableIntStateOf(0) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        deviceShortId = licenseManager.getDeviceId().take(8).uppercase()
    }

    fun submitActivation() {
        val cleaned = keyInput.trim().uppercase()
        if (cleaned.isBlank()) {
            errorMessage = "Masukkan License Key terlebih dahulu."
            return
        }
        errorMessage = null
        isActivating = true
        scope.launch {
            val outcome = licenseManager.activateKey(cleaned)
            isActivating = false
            errorMessage = when (outcome) {
                is KeyActivationResult.Success -> null
                is KeyActivationResult.InvalidFormat ->
                    "Format key tidak valid. Gunakan huruf kapital, angka, dan tanda hubung (4–32 karakter)."
                is KeyActivationResult.NotFound ->
                    "License Key tidak ditemukan. Periksa kembali kode Anda atau hubungi admin."
                is KeyActivationResult.Revoked ->
                    "License Key ini telah dinonaktifkan oleh admin."
                is KeyActivationResult.Expired ->
                    "Masa berlaku License Key ini telah berakhir. Hubungi admin untuk perpanjangan."
                is KeyActivationResult.BoundToOtherDevice -> {
                    val modelInfo = outcome.boundModel?.let { " ($it)" }.orEmpty()
                    "Key ini sudah terikat pada perangkat lain$modelInfo dan tidak bisa digunakan di dua perangkat bersamaan. Hubungi admin untuk reset perangkat."
                }
                is KeyActivationResult.Error -> outcome.message
            }
        }
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .testTag("license_gate_card"),
                shape = RoundedCornerShape(26.dp),
                color = ObsidianSurface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.border(1.dp, ObsidianBorder, RoundedCornerShape(26.dp))
                ) {
                    // Hero top header — tapping NOVA AI 5x opens the hidden Admin PIN Gate
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(155.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTimestamp > 2_000L) {
                                    secretTapCount = 1
                                } else {
                                    secretTapCount += 1
                                }
                                lastTapTimestamp = now
                                if (secretTapCount >= 5) {
                                    secretTapCount = 0
                                    onHiddenAdminTrigger()
                                }
                            }
                            .testTag("license_brand_header")
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
                                text = "Aktivasi License Key • Proteksi 1 Perangkat",
                                style = MaterialTheme.typography.bodySmall.copy(color = CyanPulse)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Masukkan License Key untuk membuka akses NOVA AI. Key akan terkunci khusus pada perangkat ini dan masa aktif mulai berjalan saat pertama kali diaktifkan.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFCBD5E1))
                        )

                        if (!errorMessage.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(NovaStatusError.copy(alpha = 0.12f))
                                    .border(1.dp, NovaStatusError.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = NovaStatusError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(color = NovaStatusError),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("license_error_banner")
                                )
                            }
                        }

                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { raw ->
                                val filtered = raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(32)
                                keyInput = filtered
                            },
                            label = { Text("License Key") },
                            placeholder = {
                                Text(
                                    text = "NOVA-XXXX-XXXX",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = Color(0xFF64748B)
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = Color.White
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null,
                                    tint = CyanPulse
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                        if (!clip.isNullOrBlank()) {
                                            keyInput = clip.trim().uppercase()
                                                .filter { it.isLetterOrDigit() || it == '-' }
                                                .take(32)
                                        }
                                    },
                                    modifier = Modifier.testTag("license_paste_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentPaste,
                                        contentDescription = "Tempel License Key",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submitActivation() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanPulse,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedContainerColor = ObsidianCard,
                                unfocusedContainerColor = ObsidianCard,
                                focusedLabelColor = CyanPulse,
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("license_key_input")
                        )

                        Button(
                            onClick = { submitActivation() },
                            enabled = !isActivating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("license_activate_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPulse,
                                contentColor = ObsidianVoid
                            )
                        ) {
                            if (isActivating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = ObsidianVoid,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Memverifikasi & Mengunci Perangkat...",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Aktifkan License Key",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        // Device & account metadata card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianElevated)
                                .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhoneAndroid,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${licenseManager.deviceModel} • ID #$deviceShortId",
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                                )
                                if (!userEmail.isNullOrBlank()) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                                    )
                                }
                            }
                            TextButton(
                                onClick = onSignOutClick,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("license_sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Logout,
                                    contentDescription = "Keluar Akun Google",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ganti Akun", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
