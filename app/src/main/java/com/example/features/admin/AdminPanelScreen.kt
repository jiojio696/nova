package com.example.features.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.permissions.PermissionHelper
import com.example.data.models.AdminKeyFilter
import com.example.data.models.AdminLicenseItem
import com.example.data.models.LicenseKeyDoc
import com.example.data.models.LicenseKeyLifecycle
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NovaStatusActive
import com.example.ui.theme.NovaStatusError
import com.example.ui.theme.NovaStatusInactive
import com.example.ui.theme.NovaStatusScanning
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: AdminPanelViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.busyAction.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var durationTarget by remember { mutableStateOf<AdminLicenseItem?>(null) }
    var noteTarget by remember { mutableStateOf<AdminLicenseItem?>(null) }
    var resetDeviceTarget by remember { mutableStateOf<AdminLicenseItem?>(null) }
    var deleteTarget by remember { mutableStateOf<AdminLicenseItem?>(null) }

    LaunchedEffect(feedback) {
        feedback?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianVoid,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Panel Admin Lisensi",
                            style = MaterialTheme.typography.titleLarge.copy(color = Color.White)
                        )
                        Text(
                            text = "Kontrol Key 1-Device & Expired Harian",
                            style = MaterialTheme.typography.labelSmall.copy(color = CyanPulse)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showChangePinDialog = true },
                        modifier = Modifier.testTag("admin_open_change_pin")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VpnKey,
                            contentDescription = "Ubah PIN Admin",
                            tint = CyanPulse
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = CyanPulse,
                contentColor = ObsidianVoid,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("admin_fab_create_key"),
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Buat Key", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                when (val state = uiState) {
                    is AdminKeysUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = CyanPulse)
                                Text(
                                    text = "Memuat daftar License Key...",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                                )
                            }
                        }
                    }

                    is AdminKeysUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(NovaStatusError.copy(alpha = 0.12f))
                                .border(1.dp, NovaStatusError.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium.copy(color = NovaStatusError)
                            )
                        }
                    }

                    is AdminKeysUiState.Success -> {
                        // KPI Metrics Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KpiCard("Total", state.totalCount, CyanPulse, Modifier.weight(1f))
                            KpiCard("Aktif", state.activeCount, NovaStatusActive, Modifier.weight(1f))
                            KpiCard("Siap", state.unusedCount, NovaStatusScanning, Modifier.weight(1f))
                            KpiCard("Expired", state.expiredCount, NovaStatusError, Modifier.weight(1f))
                        }

                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Cari kode key, nama pembeli, atau HP...", color = Color(0xFF64748B)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null, tint = Color(0xFF94A3B8))
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanPulse,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedContainerColor = ObsidianSurface,
                                unfocusedContainerColor = ObsidianSurface
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_search_input")
                        )

                        // Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AdminKeyFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = selectedFilter == filter,
                                    onClick = { viewModel.updateFilter(filter) },
                                    label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanPulse.copy(alpha = 0.22f),
                                        selectedLabelColor = CyanPulse,
                                        labelColor = Color(0xFFCBD5E1)
                                    )
                                )
                            }
                        }

                        if (state.filteredItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Key,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = if (state.allItems.isEmpty()) {
                                            "Belum ada License Key. Tekan 'Buat Key' untuk membuat key pertama."
                                        } else {
                                            "Tidak ada key yang cocok dengan filter atau pencarian."
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = state.filteredItems,
                                    key = { it.license.keyCode }
                                ) { item ->
                                    AdminLicenseCard(
                                        item = item,
                                        onCopyKey = {
                                            PermissionHelper.copyToClipboard(
                                                context,
                                                "NOVA License Key",
                                                item.license.keyCode
                                            )
                                            android.widget.Toast.makeText(
                                                context,
                                                "Key ${item.license.keyCode} disalin.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onEditNote = { noteTarget = item },
                                        onEditDuration = { durationTarget = item },
                                        onResetDevice = { resetDeviceTarget = item },
                                        onToggleStatus = { viewModel.toggleStatus(item) },
                                        onDelete = { deleteTarget = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateKeyDialog(
            initialRandomKey = remember { viewModel.generateRandomKeyCode() },
            onGenerateRandom = { viewModel.generateRandomKeyCode() },
            isBusy = isBusy,
            onDismiss = { showCreateDialog = false },
            onConfirm = { code, days, note ->
                viewModel.createKey(code, days, note) { createdCode ->
                    PermissionHelper.copyToClipboard(context, "NOVA License Key", createdCode)
                    showCreateDialog = false
                }
            }
        )
    }

    durationTarget?.let { target ->
        UpdateDurationDialog(
            item = target,
            isBusy = isBusy,
            onDismiss = { durationTarget = null },
            onConfirm = { mode, days ->
                viewModel.updateDuration(target.license.keyCode, mode, days) {
                    durationTarget = null
                }
            }
        )
    }

    noteTarget?.let { target ->
        EditBuyerNoteDialog(
            item = target,
            isBusy = isBusy,
            onDismiss = { noteTarget = null },
            onConfirm = { note ->
                viewModel.updateBuyerNote(target.license.keyCode, note) {
                    noteTarget = null
                }
            }
        )
    }

    resetDeviceTarget?.let { target ->
        ConfirmAdminActionDialog(
            title = "Reset Ikatan Perangkat?",
            message = "Perangkat saat ini (${target.license.boundDeviceModel ?: "Tidak diketahui"}) akan dilepas dari key ${target.license.keyCode} dan langsung keluar dari layanan floating. Sisa masa aktif tetap dipertahankan.",
            confirmLabel = "Reset Device",
            isDestructive = false,
            onDismiss = { resetDeviceTarget = null },
            onConfirm = {
                viewModel.resetDevice(target.license.keyCode)
                resetDeviceTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        ConfirmAdminActionDialog(
            title = "Hapus Key Permanen?",
            message = "Key ${target.license.keyCode} beserta catatan pembelinya akan dihapus permanen dan perangkat yang sedang aktif akan langsung terputus.",
            confirmLabel = "Hapus",
            isDestructive = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.deleteKey(target.license.keyCode)
                deleteTarget = null
            }
        )
    }

    if (showChangePinDialog) {
        ChangeAdminPinDialog(
            isBusy = isBusy,
            onDismiss = { showChangePinDialog = false },
            onConfirm = { oldPin, newPin ->
                viewModel.changeAdminPin(oldPin, newPin) {
                    showChangePinDialog = false
                }
            }
        )
    }
}

@Composable
private fun KpiCard(label: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = JetBrainsMonoFontFamily,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
        )
    }
}

@Composable
private fun AdminLicenseCard(
    item: AdminLicenseItem,
    onCopyKey: () -> Unit,
    onEditNote: () -> Unit,
    onEditDuration: () -> Unit,
    onResetDevice: () -> Unit,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    val lifecycle = item.lifecycle()
    val statusColor = when (lifecycle) {
        LicenseKeyLifecycle.ACTIVE -> NovaStatusActive
        LicenseKeyLifecycle.UNUSED -> NovaStatusScanning
        LicenseKeyLifecycle.EXPIRED -> NovaStatusError
        LicenseKeyLifecycle.REVOKED -> NovaStatusInactive
    }
    val hasBoundDevice = !item.license.boundDeviceId.isNullOrBlank()
    val isActiveStatus = item.license.status == LicenseKeyDoc.STATUS_ACTIVE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("admin_key_card_${item.license.keyCode}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header: Key Code + Copy + Status pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.license.keyCode,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onCopyKey,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Salin Key",
                    tint = CyanPulse,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(statusColor.copy(alpha = 0.16f))
                    .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = lifecycle.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Metadata details
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianCard)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(15.dp))
                Text(
                    text = item.formatRemainingLabel(),
                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                Text(
                    text = if (hasBoundDevice) {
                        val shortId = item.license.boundDeviceId?.take(8)?.uppercase().orEmpty()
                        "Terikat: ${item.license.boundDeviceModel ?: "Perangkat"} (#$shortId)"
                    } else {
                        "Belum terikat ke perangkat mana pun"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (hasBoundDevice) Color(0xFFE2E8F0) else Color(0xFF94A3B8)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                Text(
                    text = item.buyerNote.ifBlank { "Tanpa catatan pembeli" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (item.buyerNote.isNotBlank()) Color(0xFFCBD5E1) else Color(0xFF64748B)
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onEditNote,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Ubah Catatan Pembeli",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Action Buttons Row (all touch targets >= 48dp height)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onEditDuration,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Atur Hari", color = CyanPulse, style = MaterialTheme.typography.labelLarge)
            }

            OutlinedButton(
                onClick = onResetDevice,
                enabled = hasBoundDevice,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    Icons.Outlined.LockReset,
                    contentDescription = null,
                    tint = if (hasBoundDevice) NovaStatusScanning else Color(0xFF475569),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Reset Device",
                    color = if (hasBoundDevice) NovaStatusScanning else Color(0xFF475569),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButton(
                onClick = onToggleStatus,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = if (isActiveStatus) Icons.Outlined.Block else Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (isActiveStatus) NovaStatusError else NovaStatusActive,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isActiveStatus) "Nonaktifkan" else "Aktifkan",
                    color = if (isActiveStatus) NovaStatusError else NovaStatusActive,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Hapus Key",
                    tint = NovaStatusError,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
