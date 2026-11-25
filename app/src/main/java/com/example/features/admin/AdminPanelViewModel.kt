package com.example.features.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.models.AdminKeyFilter
import com.example.data.models.AdminLicenseItem
import com.example.data.models.DurationUpdateMode
import com.example.data.models.LicenseKeyDoc
import com.example.data.models.LicenseKeyLifecycle
import com.example.data.repository.LicenseRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AdminKeysUiState {
    data object Loading : AdminKeysUiState()
    data class Success(
        val allItems: List<AdminLicenseItem>,
        val filteredItems: List<AdminLicenseItem>,
        val totalCount: Int,
        val activeCount: Int,
        val unusedCount: Int,
        val expiredCount: Int
    ) : AdminKeysUiState()
    data class Error(val message: String) : AdminKeysUiState()
}

class AdminPanelViewModel(
    private val licenseRepository: LicenseRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(AdminKeyFilter.ALL)
    val selectedFilter: StateFlow<AdminKeyFilter> = _selectedFilter.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _busyAction = MutableStateFlow(false)
    val busyAction: StateFlow<Boolean> = _busyAction.asStateFlow()

    private val rawKeysStream: StateFlow<Result<List<AdminLicenseItem>>?> =
        licenseRepository.observeAllAdminKeys()
            .map<List<AdminLicenseItem>, Result<List<AdminLicenseItem>>?> { Result.success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                emit(Result.failure(root))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = null
            )

    val uiState: StateFlow<AdminKeysUiState> = combine(
        rawKeysStream,
        _searchQuery,
        _selectedFilter
    ) { result, query, filter ->
        if (result == null) {
            AdminKeysUiState.Loading
        } else {
            result.fold(
                onSuccess = { items ->
                    val now = System.currentTimeMillis()
                    val q = query.trim().lowercase()
                    val filtered = items.filter { item ->
                        val matchesQuery = q.isEmpty() ||
                            item.license.keyCode.lowercase().contains(q) ||
                            item.buyerNote.lowercase().contains(q) ||
                            (item.license.boundDeviceModel?.lowercase()?.contains(q) == true)
                        val lifecycle = item.lifecycle(now)
                        val matchesFilter = when (filter) {
                            AdminKeyFilter.ALL -> true
                            AdminKeyFilter.ACTIVE -> lifecycle == LicenseKeyLifecycle.ACTIVE
                            AdminKeyFilter.UNUSED -> lifecycle == LicenseKeyLifecycle.UNUSED
                            AdminKeyFilter.EXPIRED -> lifecycle == LicenseKeyLifecycle.EXPIRED
                            AdminKeyFilter.REVOKED -> lifecycle == LicenseKeyLifecycle.REVOKED
                        }
                        matchesQuery && matchesFilter
                    }
                    AdminKeysUiState.Success(
                        allItems = items,
                        filteredItems = filtered,
                        totalCount = items.size,
                        activeCount = items.count { it.lifecycle(now) == LicenseKeyLifecycle.ACTIVE },
                        unusedCount = items.count { it.lifecycle(now) == LicenseKeyLifecycle.UNUSED },
                        expiredCount = items.count { it.lifecycle(now) == LicenseKeyLifecycle.EXPIRED }
                    )
                },
                onFailure = { err ->
                    AdminKeysUiState.Error(
                        err.localizedMessage ?: "Gagal memuat daftar lisensi dari Firestore."
                    )
                }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AdminKeysUiState.Loading
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFilter(filter: AdminKeyFilter) {
        _selectedFilter.value = filter
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun generateRandomKeyCode(): String = licenseRepository.generateRandomKeyCode()

    fun createKey(
        rawKeyCode: String,
        durationDays: Int,
        buyerNote: String,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = licenseRepository.createLicenseKey(rawKeyCode, durationDays, buyerNote)
            _busyAction.value = false
            res.fold(
                onSuccess = { code ->
                    _feedbackMessage.value = "Key $code berhasil dibuat ($durationDays hari)."
                    onCreated(code)
                },
                onFailure = { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Gagal membuat key."
                }
            )
        }
    }

    fun updateDuration(keyCode: String, mode: DurationUpdateMode, days: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = licenseRepository.updateKeyDuration(keyCode, mode, days)
            _busyAction.value = false
            res.fold(
                onSuccess = {
                    val label = if (mode == DurationUpdateMode.ADD_DAYS) "+$days hari" else "$days hari"
                    _feedbackMessage.value = "Masa aktif $keyCode diperbarui ($label)."
                    onDone()
                },
                onFailure = { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Gagal memperbarui masa aktif."
                }
            )
        }
    }

    fun resetDevice(keyCode: String) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = licenseRepository.resetDeviceBinding(keyCode)
            _busyAction.value = false
            res.fold(
                onSuccess = {
                    _feedbackMessage.value = "Perangkat terikat pada $keyCode berhasil direset."
                },
                onFailure = { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Gagal mereset perangkat."
                }
            )
        }
    }

    fun toggleStatus(item: AdminLicenseItem) {
        val nextStatus = if (item.license.status == LicenseKeyDoc.STATUS_ACTIVE) {
            LicenseKeyDoc.STATUS_REVOKED
        } else {
            LicenseKeyDoc.STATUS_ACTIVE
        }
        viewModelScope.launch {
            _busyAction.value = true
            val res = licenseRepository.setKeyStatus(item.license.keyCode, nextStatus)
            _busyAction.value = false
            res.fold(
                onSuccess = {
                    val verb = if (nextStatus == LicenseKeyDoc.STATUS_ACTIVE) "diaktifkan kembali" else "dinonaktifkan"
                    _feedbackMessage.value = "Key ${item.license.keyCode} $verb."
                },
                onFailure = { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Gagal mengubah status key."
                }
            )
        }
    }

    fun updateBuyerNote(keyCode: String, buyerNote: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = licenseRepository.updateBuyerNote(keyCode, buyerNote)
            _busyAction.value = false
            res.fold(
                onSuccess = {
                    _feedbackMessage.value = "Catatan pembeli untuk $keyCode disimpan."
                    onDone()
                },
                onFailure = { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Gagal menyimpan catatan."
                }
            )
        }
    }

    fun deleteKey(keyCode: String) {
        viewModelScope.launch {
            _busyAction.value = true
            val res = licenseRepository.deleteLicenseKey(keyCode)
            _busyAction.value = false
            res.fold(
                onSuccess = {
                    _feedbackMessage.value = "Key $keyCode dihapus permanen."
                },
                onFailure = { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Gagal menghapus key."
                }
            )
        }
    }

    fun changeAdminPin(currentPin: String, newPin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _busyAction.value = true
            val verified = licenseRepository.verifyAdminPin(currentPin).getOrDefault(false)
            if (!verified) {
                _busyAction.value = false
                _feedbackMessage.value = "PIN lama tidak sesuai."
                return@launch
            }
            val res = licenseRepository.saveAdminPin(newPin)
            _busyAction.value = false
            res.fold(
                onSuccess = {
                    _feedbackMessage.value = "PIN Admin berhasil diperbarui."
                    onSuccess()
                },
                onFailure = { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Gagal memperbarui PIN."
                }
            )
        }
    }

    class Factory(private val repository: LicenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdminPanelViewModel(repository) as T
        }
    }
}
