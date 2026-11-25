package com.example.domain

import android.content.Context
import com.example.core.security.DeviceIdentityProvider
import com.example.data.models.KeyActivationResult
import com.example.data.models.LicenseAccessState
import com.example.data.models.LicenseKeyDoc
import com.example.data.repository.LicenseRepository
import com.example.data.repository.SettingsRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Coordinates live single-device license verification across both MainActivity and NovaFloatingService.
 * Automatically revokes floating access in real time if the key expires, is revoked, or is reset by Admin.
 */
class LicenseManager(
    private val appContext: Context,
    private val licenseRepository: LicenseRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _accessState = MutableStateFlow<LicenseAccessState>(LicenseAccessState.Loading)
    val accessState: StateFlow<LicenseAccessState> = _accessState.asStateFlow()

    private val _deviceId = MutableStateFlow<String?>(null)
    val deviceModel: String = DeviceIdentityProvider.getDeviceModel()

    private var activeUserUid: String? = null
    private var observerJob: Job? = null
    private var expiryTickerJob: Job? = null

    suspend fun getDeviceId(): String {
        _deviceId.value?.let { return it }
        val seed = settingsRepository.getOrCreateFallbackDeviceSeed()
        val resolved = DeviceIdentityProvider.resolveDeviceId(appContext, seed)
        _deviceId.value = resolved
        return resolved
    }

    fun startObservingForUser(uid: String) {
        if (uid.isBlank()) return
        if (activeUserUid == uid && observerJob?.isActive == true) return

        stopObserving()
        activeUserUid = uid
        _accessState.value = LicenseAccessState.Loading

        observerJob = scope.launch {
            val currentDeviceId = getDeviceId()
            settingsRepository.activeLicenseKeyFlow
                .distinctUntilChanged()
                .collectLatest { savedKey ->
                    expiryTickerJob?.cancel()
                    if (savedKey.isNullOrBlank()) {
                        _accessState.value = LicenseAccessState.Unlicensed(reason = null)
                        return@collectLatest
                    }

                    _accessState.value = LicenseAccessState.Loading
                    licenseRepository.observeLicenseKey(savedKey)
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            _accessState.value = LicenseAccessState.Unlicensed(
                                reason = "Gagal memverifikasi lisensi dari server. Periksa koneksi internet Anda."
                            )
                        }
                        .collectLatest { doc ->
                            evaluateLicenseDocument(doc, currentDeviceId)
                        }
                }
        }
    }

    private fun evaluateLicenseDocument(doc: LicenseKeyDoc?, currentDeviceId: String) {
        expiryTickerJob?.cancel()
        if (doc == null) {
            _accessState.value = LicenseAccessState.Unlicensed(
                reason = "Key lisensi tidak ditemukan atau telah dihapus oleh admin."
            )
            return
        }
        if (doc.status != LicenseKeyDoc.STATUS_ACTIVE) {
            _accessState.value = LicenseAccessState.Unlicensed(
                reason = "Key lisensi Anda telah dinonaktifkan oleh admin."
            )
            return
        }
        val boundId = doc.boundDeviceId?.takeIf { it.isNotBlank() }
        if (boundId == null) {
            _accessState.value = LicenseAccessState.Unlicensed(
                reason = "Ikatan perangkat telah direset oleh admin. Silakan aktivasi ulang jika masih memiliki akses."
            )
            return
        }
        if (boundId != currentDeviceId) {
            _accessState.value = LicenseAccessState.Unlicensed(
                reason = "Key ini terikat pada perangkat lain dan tidak dapat dijalankan di dua perangkat bersamaan."
            )
            return
        }
        val expMs = doc.expiresAt?.toDate()?.time
        if (expMs != null && expMs <= System.currentTimeMillis()) {
            _accessState.value = LicenseAccessState.Unlicensed(
                reason = "Masa berlaku key lisensi Anda telah berakhir. Hubungi admin untuk perpanjangan."
            )
            return
        }

        _accessState.value = LicenseAccessState.Licensed(doc)

        // Watch local clock against expiresAt so expiry revokes floating mode promptly even without a write event
        if (expMs != null) {
            expiryTickerJob = scope.launch {
                while (true) {
                    val remaining = expMs - System.currentTimeMillis()
                    if (remaining <= 0L) {
                        _accessState.value = LicenseAccessState.Unlicensed(
                            reason = "Masa berlaku key lisensi Anda baru saja berakhir. Hubungi admin untuk perpanjangan."
                        )
                        break
                    }
                    delay(minOf(remaining, 30_000L))
                }
            }
        }
    }

    fun stopObserving() {
        observerJob?.cancel()
        observerJob = null
        expiryTickerJob?.cancel()
        expiryTickerJob = null
        activeUserUid = null
        _accessState.value = LicenseAccessState.Loading
    }

    suspend fun activateKey(rawKeyCode: String): KeyActivationResult {
        val currentDeviceId = getDeviceId()
        val result = licenseRepository.activateOrVerifyKey(
            rawKeyCode = rawKeyCode,
            deviceId = currentDeviceId,
            deviceModel = deviceModel
        )
        return result.fold(
            onSuccess = { outcome ->
                if (outcome is KeyActivationResult.Success) {
                    settingsRepository.updateActiveLicenseKey(outcome.license.keyCode)
                    _accessState.value = LicenseAccessState.Licensed(outcome.license)
                }
                outcome
            },
            onFailure = { err ->
                KeyActivationResult.Error(
                    err.localizedMessage ?: "Gagal terhubung ke server lisensi."
                )
            }
        )
    }

    suspend fun clearSavedKey(reason: String? = null) {
        expiryTickerJob?.cancel()
        settingsRepository.updateActiveLicenseKey(null)
        _accessState.value = LicenseAccessState.Unlicensed(reason = reason)
    }
}
