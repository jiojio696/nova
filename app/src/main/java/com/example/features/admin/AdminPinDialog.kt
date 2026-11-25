package com.example.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.models.AdminPinGateStatus
import com.example.data.repository.LicenseRepository
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NovaStatusError
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import kotlinx.coroutines.launch

@Composable
fun AdminPinDialog(
    licenseRepository: LicenseRepository,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var gateStatus by remember { mutableStateOf(AdminPinGateStatus.CHECKING) }
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!licenseRepository.isCurrentUserAdmin()) {
            gateStatus = AdminPinGateStatus.NOT_ADMIN
            return@LaunchedEffect
        }
        val configRes = licenseRepository.getAdminSecurityConfig()
        configRes.fold(
            onSuccess = { cfg ->
                gateStatus = if (cfg == null || cfg.pinHash.isBlank()) {
                    AdminPinGateStatus.NEEDS_SETUP
                } else {
                    AdminPinGateStatus.NEEDS_PIN
                }
            },
            onFailure = { err ->
                errorMessage = err.localizedMessage ?: "Gagal memeriksa konfigurasi PIN admin."
                gateStatus = AdminPinGateStatus.ERROR
            }
        )
    }

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_pin_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurface,
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyanPulse.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AdminPanelSettings,
                            contentDescription = null,
                            tint = CyanPulse,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = when (gateStatus) {
                                AdminPinGateStatus.NEEDS_SETUP -> "Buat PIN Admin"
                                AdminPinGateStatus.NOT_ADMIN -> "Akses Dibatasi"
                                else -> "Verifikasi PIN Admin"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                        )
                        Text(
                            text = "Gerbang Tersembunyi Panel Lisensi",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                        )
                    }
                }

                when (gateStatus) {
                    AdminPinGateStatus.CHECKING -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = CyanPulse,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Memeriksa hak akses admin...",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                            )
                        }
                    }

                    AdminPinGateStatus.NOT_ADMIN -> {
                        Text(
                            text = "Akun Google yang sedang masuk bukan akun Admin terverifikasi. Masuk menggunakan akun Google admin untuk membuka Panel Admin.",
                            style = MaterialTheme.typography.bodySmall.copy(color = NovaStatusError)
                        )
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("admin_pin_close_not_admin"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Tutup", color = Color.White)
                        }
                    }

                    AdminPinGateStatus.ERROR -> {
                        Text(
                            text = errorMessage ?: "Terjadi kesalahan saat menghubungi server.",
                            style = MaterialTheme.typography.bodySmall.copy(color = NovaStatusError)
                        )
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Tutup", color = Color.White)
                        }
                    }

                    AdminPinGateStatus.NEEDS_SETUP -> {
                        Text(
                            text = "Tetapkan PIN rahasia (4–8 digit angka) untuk melindungi Panel Admin. PIN disimpan dalam bentuk hash terenkripsi di Firestore.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                        )

                        PinField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                            label = "PIN Admin Baru (4–8 digit)",
                            showPin = showPin,
                            onToggleVisibility = { showPin = !showPin },
                            testTag = "admin_pin_setup_input"
                        )

                        PinField(
                            value = confirmPinInput,
                            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPinInput = it },
                            label = "Konfirmasi PIN Admin",
                            showPin = showPin,
                            onToggleVisibility = { showPin = !showPin },
                            testTag = "admin_pin_setup_confirm"
                        )

                        if (!errorMessage.isNullOrBlank()) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(color = NovaStatusError)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Batal", color = Color(0xFFCBD5E1))
                            }
                            Button(
                                onClick = {
                                    errorMessage = null
                                    if (pinInput.length !in 4..8) {
                                        errorMessage = "PIN harus 4 hingga 8 digit angka."
                                        return@Button
                                    }
                                    if (pinInput != confirmPinInput) {
                                        errorMessage = "Konfirmasi PIN tidak cocok."
                                        return@Button
                                    }
                                    isSubmitting = true
                                    scope.launch {
                                        val res = licenseRepository.saveAdminPin(pinInput)
                                        isSubmitting = false
                                        res.fold(
                                            onSuccess = {
                                                gateStatus = AdminPinGateStatus.UNLOCKED
                                                onUnlocked()
                                            },
                                            onFailure = { err ->
                                                errorMessage = err.localizedMessage ?: "Gagal menyimpan PIN."
                                            }
                                        )
                                    }
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("admin_pin_setup_submit"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanPulse,
                                    contentColor = ObsidianVoid
                                )
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = ObsidianVoid,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Simpan & Masuk", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    AdminPinGateStatus.NEEDS_PIN, AdminPinGateStatus.UNLOCKED -> {
                        Text(
                            text = "Masukkan PIN rahasia Admin untuk mengelola License Key & masa aktif.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                        )

                        PinField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                            label = "PIN Admin",
                            showPin = showPin,
                            onToggleVisibility = { showPin = !showPin },
                            testTag = "admin_pin_verify_input"
                        )

                        if (!errorMessage.isNullOrBlank()) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(color = NovaStatusError),
                                modifier = Modifier.testTag("admin_pin_error")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Batal", color = Color(0xFFCBD5E1))
                            }
                            Button(
                                onClick = {
                                    errorMessage = null
                                    if (pinInput.length < 4) {
                                        errorMessage = "Masukkan PIN minimal 4 digit."
                                        return@Button
                                    }
                                    isSubmitting = true
                                    scope.launch {
                                        val res = licenseRepository.verifyAdminPin(pinInput)
                                        isSubmitting = false
                                        res.fold(
                                            onSuccess = { ok ->
                                                if (ok) {
                                                    gateStatus = AdminPinGateStatus.UNLOCKED
                                                    onUnlocked()
                                                } else {
                                                    errorMessage = "PIN Admin salah. Silakan coba lagi."
                                                }
                                            },
                                            onFailure = { err ->
                                                errorMessage = err.localizedMessage ?: "Gagal memverifikasi PIN."
                                            }
                                        )
                                    }
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("admin_pin_verify_submit"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanPulse,
                                    contentColor = ObsidianVoid
                                )
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = ObsidianVoid,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buka Panel", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showPin: Boolean,
    onToggleVisibility: () -> Unit,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = MaterialTheme.typography.titleMedium.copy(
            fontFamily = JetBrainsMonoFontFamily,
            color = Color.White
        ),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (showPin) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showPin) "Sembunyikan PIN" else "Tampilkan PIN",
                    tint = Color(0xFF94A3B8)
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanPulse,
            unfocusedBorderColor = ObsidianBorder,
            focusedContainerColor = ObsidianCard,
            unfocusedContainerColor = ObsidianCard,
            focusedLabelColor = CyanPulse,
            unfocusedLabelColor = Color(0xFF94A3B8)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}
