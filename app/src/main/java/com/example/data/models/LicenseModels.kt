package com.example.data.models

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Keep
@IgnoreExtraProperties
data class LicenseKeyDoc(
    @DocumentId val keyCode: String = "",
    val durationDays: Int = 1,
    val status: String = STATUS_ACTIVE,
    val boundDeviceId: String? = null,
    val boundDeviceModel: String? = null,
    val boundUserId: String? = null,
    @ServerTimestamp val activatedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_REVOKED = "revoked"
    }
}

@Keep
@IgnoreExtraProperties
data class AdminKeyNoteDoc(
    @DocumentId val keyCode: String = "",
    val buyerNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminSecurityConfigDoc(
    val pinSalt: String = "",
    val pinHash: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class LicenseKeyLifecycle(val label: String) {
    UNUSED("Belum Dipakai"),
    ACTIVE("Aktif"),
    EXPIRED("Kedaluwarsa"),
    REVOKED("Nonaktif")
}

enum class AdminKeyFilter(val label: String) {
    ALL("Semua"),
    ACTIVE("Aktif"),
    UNUSED("Belum Dipakai"),
    EXPIRED("Expired"),
    REVOKED("Nonaktif")
}

enum class DurationUpdateMode {
    ADD_DAYS,
    SET_DAYS
}

data class AdminLicenseItem(
    val license: LicenseKeyDoc,
    val buyerNote: String = ""
) {
    fun lifecycle(nowMillis: Long = System.currentTimeMillis()): LicenseKeyLifecycle {
        if (license.status != LicenseKeyDoc.STATUS_ACTIVE) return LicenseKeyLifecycle.REVOKED
        val exp = license.expiresAt?.toDate()?.time
        val act = license.activatedAt
        return when {
            act == null && exp == null -> LicenseKeyLifecycle.UNUSED
            exp != null && exp <= nowMillis -> LicenseKeyLifecycle.EXPIRED
            else -> LicenseKeyLifecycle.ACTIVE
        }
    }

    fun formatRemainingLabel(nowMillis: Long = System.currentTimeMillis()): String {
        val state = lifecycle(nowMillis)
        return when (state) {
            LicenseKeyLifecycle.REVOKED -> "Dinonaktifkan Admin"
            LicenseKeyLifecycle.UNUSED -> "Belum dipakai • Durasi ${license.durationDays} Hari"
            LicenseKeyLifecycle.EXPIRED -> {
                val dateText = license.expiresAt?.toDate()?.let { formatShortDate(it) } ?: "-"
                "Kedaluwarsa ($dateText)"
            }
            LicenseKeyLifecycle.ACTIVE -> {
                val expMs = license.expiresAt?.toDate()?.time ?: return "Aktif (${license.durationDays} Hari)"
                val diffMs = max(0L, expMs - nowMillis)
                val totalMinutes = diffMs / 60_000L
                val days = totalMinutes / (60L * 24L)
                val hours = (totalMinutes % (60L * 24L)) / 60L
                val mins = totalMinutes % 60L
                val countdown = when {
                    days > 0 -> "$days Hari $hours Jam"
                    hours > 0 -> "$hours Jam $mins Menit"
                    else -> "${max(1L, mins)} Menit"
                }
                val dateText = formatShortDate(Date(expMs))
                "Sisa $countdown • Exp $dateText"
            }
        }
    }

    companion object {
        fun formatShortDate(date: Date): String {
            return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID")).format(date)
        }
    }
}

sealed class LicenseAccessState {
    data object Loading : LicenseAccessState()
    data class Unlicensed(val reason: String? = null) : LicenseAccessState()
    data class Licensed(val license: LicenseKeyDoc) : LicenseAccessState()
}

sealed class KeyActivationResult {
    data class Success(val license: LicenseKeyDoc) : KeyActivationResult()
    data object InvalidFormat : KeyActivationResult()
    data object NotFound : KeyActivationResult()
    data object Revoked : KeyActivationResult()
    data object Expired : KeyActivationResult()
    data class BoundToOtherDevice(val boundModel: String?) : KeyActivationResult()
    data class Error(val message: String) : KeyActivationResult()
}

enum class AdminPinGateStatus {
    CHECKING,
    NOT_ADMIN,
    NEEDS_SETUP,
    NEEDS_PIN,
    UNLOCKED,
    ERROR
}
