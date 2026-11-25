package com.example.features.admin

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.models.AdminLicenseItem
import com.example.data.models.DurationUpdateMode
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NovaStatusError
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateKeyDialog(
    initialRandomKey: String,
    onGenerateRandom: () -> String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (keyCode: String, durationDays: Int, buyerNote: String) -> Unit
) {
    var keyCode by remember { mutableStateOf(initialRandomKey) }
    var daysText by remember { mutableStateOf("7") }
    var buyerNote by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_create_key_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurface
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Buat License Key Baru",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
                )
                Text(
                    text = "Masa berlaku mulai dihitung saat key pertama kali diaktifkan pengguna.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                )

                OutlinedTextField(
                    value = keyCode,
                    onValueChange = { raw ->
                        keyCode = raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(32)
                    },
                    label = { Text("Kode Key (Acak atau Kustom)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = Color.White
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = { keyCode = onGenerateRandom() },
                            modifier = Modifier.testTag("admin_generate_random_key")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Casino,
                                contentDescription = "Generate Key Acak",
                                tint = CyanPulse
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = adminFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_create_key_input")
                )

                Text(
                    text = "Durasi Masa Aktif (Hari)",
                    style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFFCBD5E1))
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 3, 7, 14, 30, 90).forEach { preset ->
                        FilterChip(
                            selected = daysText == preset.toString(),
                            onClick = { daysText = preset.toString() },
                            label = { Text("$preset Hari", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPulse.copy(alpha = 0.22f),
                                selectedLabelColor = CyanPulse,
                                labelColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = daysText,
                    onValueChange = { raw ->
                        if (raw.length <= 4 && raw.all(Char::isDigit)) daysText = raw
                    },
                    label = { Text("Jumlah Hari (1–3650)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = Color.White
                    ),
                    colors = adminFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_create_days_input")
                )

                OutlinedTextField(
                    value = buyerNote,
                    onValueChange = { if (it.length <= 200) buyerNote = it },
                    label = { Text("Catatan Nama Pembeli (Opsional)") },
                    placeholder = { Text("Contoh: Budi - WA 0812xxx", color = Color(0xFF64748B)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = adminFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_create_note_input")
                )

                if (!localError.isNullOrBlank()) {
                    Text(
                        text = localError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(color = NovaStatusError)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Batal", color = Color(0xFFCBD5E1))
                    }
                    Button(
                        onClick = {
                            val code = keyCode.trim().uppercase()
                            val days = daysText.toIntOrNull()
                            when {
                                code.length !in 4..32 ->
                                    localError = "Kode key minimal 4 karakter (huruf besar, angka, atau -)."
                                days == null || days !in 1..3650 ->
                                    localError = "Masukkan jumlah hari valid antara 1 sampai 3650."
                                else -> {
                                    localError = null
                                    onConfirm(code, days, buyerNote.trim())
                                }
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("admin_create_key_submit"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPulse,
                            contentColor = ObsidianVoid
                        )
                    ) {
                        Text("Buat Key", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateDurationDialog(
    item: AdminLicenseItem,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (mode: DurationUpdateMode, days: Int) -> Unit
) {
    var mode by remember { mutableStateOf(DurationUpdateMode.ADD_DAYS) }
    var daysText by remember { mutableStateOf("7") }
    var localError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_duration_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurface
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Atur Masa Aktif",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
                )
                Text(
                    text = "${item.license.keyCode} • ${item.formatRemainingLabel()}",
                    style = MaterialTheme.typography.labelMedium.copy(color = CyanPulse)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == DurationUpdateMode.ADD_DAYS,
                        onClick = { mode = DurationUpdateMode.ADD_DAYS },
                        label = { Text("Perpanjang (+Hari)", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPulse.copy(alpha = 0.22f),
                            selectedLabelColor = CyanPulse,
                            labelColor = Color(0xFFCBD5E1)
                        )
                    )
                    FilterChip(
                        selected = mode == DurationUpdateMode.SET_DAYS,
                        onClick = { mode = DurationUpdateMode.SET_DAYS },
                        label = { Text("Atur Ulang Hari", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPulse.copy(alpha = 0.22f),
                            selectedLabelColor = CyanPulse,
                            labelColor = Color(0xFFCBD5E1)
                        )
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1, 3, 7, 14, 30).forEach { preset ->
                        val prefix = if (mode == DurationUpdateMode.ADD_DAYS) "+" else ""
                        FilterChip(
                            selected = daysText == preset.toString(),
                            onClick = { daysText = preset.toString() },
                            label = { Text("$prefix$preset H", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPulse.copy(alpha = 0.22f),
                                selectedLabelColor = CyanPulse,
                                labelColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = daysText,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) daysText = it },
                    label = {
                        Text(
                            if (mode == DurationUpdateMode.ADD_DAYS) "Tambah Hari" else "Total Sisa Hari Baru"
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = Color.White
                    ),
                    colors = adminFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_duration_days_input")
                )

                if (!localError.isNullOrBlank()) {
                    Text(localError.orEmpty(), style = MaterialTheme.typography.bodySmall.copy(color = NovaStatusError))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Batal", color = Color(0xFFCBD5E1))
                    }
                    Button(
                        onClick = {
                            val d = daysText.toIntOrNull()
                            if (d == null || d !in 1..3650) {
                                localError = "Masukkan jumlah hari antara 1 sampai 3650."
                            } else {
                                localError = null
                                onConfirm(mode, d)
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("admin_duration_submit"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPulse,
                            contentColor = ObsidianVoid
                        )
                    ) {
                        Text("Simpan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EditBuyerNoteDialog(
    item: AdminLicenseItem,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var noteText by remember { mutableStateOf(item.buyerNote) }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_note_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurface
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Catatan Pembeli",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
                )
                Text(
                    text = item.license.keyCode,
                    style = MaterialTheme.typography.labelMedium.copy(color = CyanPulse)
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { if (it.length <= 200) noteText = it },
                    label = { Text("Nama / Catatan Pembeli") },
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = adminFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_note_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Batal", color = Color(0xFFCBD5E1))
                    }
                    Button(
                        onClick = { onConfirm(noteText.trim()) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("admin_note_submit"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPulse,
                            contentColor = ObsidianVoid
                        )
                    ) {
                        Text("Simpan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ChangeAdminPinDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (currentPin: String, newPin: String) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_change_pin_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurface
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Ubah PIN Admin",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
                )
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) currentPin = it },
                    label = { Text("PIN Lama") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = adminFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
                    label = { Text("PIN Baru (4–8 digit)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = adminFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text("Konfirmasi PIN Baru") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = adminFieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!localError.isNullOrBlank()) {
                    Text(localError.orEmpty(), style = MaterialTheme.typography.bodySmall.copy(color = NovaStatusError))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Batal", color = Color(0xFFCBD5E1))
                    }
                    Button(
                        onClick = {
                            when {
                                currentPin.length < 4 -> localError = "Masukkan PIN lama Anda."
                                newPin.length !in 4..8 -> localError = "PIN baru harus 4 hingga 8 digit angka."
                                newPin != confirmPin -> localError = "Konfirmasi PIN baru tidak cocok."
                                else -> {
                                    localError = null
                                    onConfirm(currentPin, newPin)
                                }
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("admin_change_pin_submit"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPulse,
                            contentColor = ObsidianVoid
                        )
                    ) {
                        Text("Perbarui PIN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmAdminActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurface
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Batal", color = Color(0xFFCBD5E1))
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("admin_confirm_action_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDestructive) NovaStatusError else CyanPulse,
                            contentColor = ObsidianVoid
                        )
                    ) {
                        Text(confirmLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun adminFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyanPulse,
    unfocusedBorderColor = ObsidianBorder,
    focusedContainerColor = ObsidianCard,
    unfocusedContainerColor = ObsidianCard,
    focusedLabelColor = CyanPulse,
    unfocusedLabelColor = Color(0xFF94A3B8),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)
