package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.core.security.PinCryptoHelper
import com.example.data.models.AdminKeyNoteDoc
import com.example.data.models.AdminLicenseItem
import com.example.data.models.AdminSecurityConfigDoc
import com.example.data.models.DurationUpdateMode
import com.example.data.models.KeyActivationResult
import com.example.data.models.LicenseKeyDoc
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date
import kotlin.math.max

class LicenseRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth
    private val random = SecureRandom()

    companion object {
        const val COLLECTION_LICENSE_KEYS = "licenseKeys"
        const val COLLECTION_ADMIN_KEY_NOTES = "adminKeyNotes"
        const val COLLECTION_ADMIN_CONFIG = "adminConfig"
        const val DOC_SECURITY = "security"
        const val BOOTSTRAP_ADMIN_EMAIL = "susimelani2108@gmail.com"
        private const val MILLIS_PER_DAY = 86_400_000L
        private val KEY_CODE_REGEX = Regex("^[A-Z0-9\\-]{4,32}$")
        private const val KEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
    }

    fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)
    }

    fun normalizeKeyCode(raw: String): String = raw.trim().uppercase()

    fun isValidKeyCodeFormat(keyCode: String): Boolean = KEY_CODE_REGEX.matches(keyCode)

    fun generateRandomKeyCode(): String {
        fun segment(len: Int): String = buildString(len) {
            repeat(len) { append(KEY_ALPHABET[random.nextInt(KEY_ALPHABET.length)]) }
        }
        return "NOVA-${segment(4)}-${segment(4)}"
    }

    /**
     * Real-time document listener for a single license key using ServerTimestampBehavior.ESTIMATE.
     */
    fun observeLicenseKey(rawKeyCode: String): Flow<LicenseKeyDoc?> = flow {
        val uid = requireUserId()
        val keyCode = normalizeKeyCode(rawKeyCode)
        if (!isValidKeyCodeFormat(keyCode)) {
            emit(null)
            return@flow
        }
        val path = "$COLLECTION_LICENSE_KEYS/$keyCode"
        emitAll(
            db.collection(COLLECTION_LICENSE_KEYS).document(keyCode)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        LicenseKeyDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Atomically verifies and binds a license key to this device using a Firestore Transaction.
     * Enforces single-device lock and activation-triggered daily expiration.
     */
    suspend fun activateOrVerifyKey(
        rawKeyCode: String,
        deviceId: String,
        deviceModel: String
    ): Result<KeyActivationResult> {
        val keyCode = normalizeKeyCode(rawKeyCode)
        if (!isValidKeyCodeFormat(keyCode)) {
            return Result.success(KeyActivationResult.InvalidFormat)
        }
        val docPath = "$COLLECTION_LICENSE_KEYS/$keyCode"
        return try {
            val uid = requireUserId()
            val safeModel = deviceModel.trim().take(100).ifBlank { "Android Device" }
            val docRef = db.collection(COLLECTION_LICENSE_KEYS).document(keyCode)

            val outcome = db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) return@runTransaction KeyActivationResult.NotFound

                val doc = snap.toObject(
                    LicenseKeyDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: return@runTransaction KeyActivationResult.NotFound

                if (doc.status != LicenseKeyDoc.STATUS_ACTIVE) {
                    return@runTransaction KeyActivationResult.Revoked
                }

                val nowMs = System.currentTimeMillis()
                val existingBoundId = doc.boundDeviceId?.takeIf { it.isNotBlank() }

                if (existingBoundId != null) {
                    if (existingBoundId != deviceId) {
                        return@runTransaction KeyActivationResult.BoundToOtherDevice(doc.boundDeviceModel)
                    }
                    // Already bound to THIS device — verify not expired
                    val expMs = doc.expiresAt?.toDate()?.time
                    if (expMs != null && expMs <= nowMs) {
                        return@runTransaction KeyActivationResult.Expired
                    }
                    return@runTransaction KeyActivationResult.Success(doc)
                }

                // Unbound key: distinguish first-time activation vs re-bind after Admin Reset Device
                val alreadyActivated = doc.activatedAt != null && doc.expiresAt != null
                if (alreadyActivated) {
                    val existingExpMs = doc.expiresAt!!.toDate().time
                    if (existingExpMs <= nowMs) {
                        return@runTransaction KeyActivationResult.Expired
                    }
                    // Tier 2 Action B: Re-bind new device, preserving original activatedAt & expiresAt
                    tx.update(
                        docRef,
                        mapOf(
                            "boundDeviceId" to deviceId,
                            "boundDeviceModel" to safeModel,
                            "boundUserId" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    KeyActivationResult.Success(
                        doc.copy(
                            boundDeviceId = deviceId,
                            boundDeviceModel = safeModel,
                            boundUserId = uid
                        )
                    )
                } else {
                    // Tier 2 Action A: First-time activation — start daily countdown now!
                    val computedExp = Timestamp(Date(nowMs + doc.durationDays.toLong() * MILLIS_PER_DAY))
                    tx.update(
                        docRef,
                        mapOf(
                            "boundDeviceId" to deviceId,
                            "boundDeviceModel" to safeModel,
                            "boundUserId" to uid,
                            "activatedAt" to FieldValue.serverTimestamp(),
                            "expiresAt" to computedExp,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    KeyActivationResult.Success(
                        doc.copy(
                            boundDeviceId = deviceId,
                            boundDeviceModel = safeModel,
                            boundUserId = uid,
                            activatedAt = Timestamp(Date(nowMs)),
                            expiresAt = computedExp
                        )
                    )
                }
            }.await()

            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- ADMIN REALTIME STREAM & CRUD ---

    fun observeAllAdminKeys(): Flow<List<AdminLicenseItem>> = flow {
        val uid = requireUserId()
        val keysFlow = db.collection(COLLECTION_LICENSE_KEYS)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    LicenseKeyDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
        val notesFlow = db.collection(COLLECTION_ADMIN_KEY_NOTES)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    AdminKeyNoteDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).associateBy { it.keyCode }
            }

        emitAll(
            combine(keysFlow, notesFlow) { keys, notesMap ->
                keys.map { key ->
                    AdminLicenseItem(
                        license = key,
                        buyerNote = notesMap[key.keyCode]?.buyerNote.orEmpty()
                    )
                }.sortedByDescending { it.license.createdAt?.toDate()?.time ?: 0L }
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, COLLECTION_LICENSE_KEYS)
                }
                throw error
            }
        )
    }

    suspend fun createLicenseKey(
        rawKeyCode: String,
        durationDays: Int,
        buyerNote: String
    ): Result<String> {
        val keyCode = normalizeKeyCode(rawKeyCode)
        val docPath = "$COLLECTION_LICENSE_KEYS/$keyCode"
        return try {
            requireUserId()
            require(isValidKeyCodeFormat(keyCode)) {
                "Format key harus 4-32 karakter huruf kapital, angka, atau tanda hubung (-)."
            }
            val safeDays = durationDays.coerceIn(1, 3650)
            val safeNote = buyerNote.trim().take(200)

            val keyRef = db.collection(COLLECTION_LICENSE_KEYS).document(keyCode)
            val existingSnap = keyRef.get().await()
            if (existingSnap.exists()) {
                return Result.failure(IllegalStateException("Kode key '$keyCode' sudah terdaftar."))
            }

            val batch = db.batch()
            batch.set(
                keyRef,
                mapOf(
                    "durationDays" to safeDays,
                    "status" to LicenseKeyDoc.STATUS_ACTIVE,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (safeNote.isNotEmpty()) {
                val noteRef = db.collection(COLLECTION_ADMIN_KEY_NOTES).document(keyCode)
                batch.set(
                    noteRef,
                    mapOf(
                        "buyerNote" to safeNote,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(keyCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateKeyDuration(
        keyCode: String,
        mode: DurationUpdateMode,
        days: Int
    ): Result<Unit> {
        val normalized = normalizeKeyCode(keyCode)
        val docPath = "$COLLECTION_LICENSE_KEYS/$normalized"
        return try {
            requireUserId()
            val safeInputDays = days.coerceIn(1, 3650)
            val docRef = db.collection(COLLECTION_LICENSE_KEYS).document(normalized)
            val snap = docRef.get().await()
            val doc = snap.toObject(
                LicenseKeyDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Key tidak ditemukan."))

            val nowMs = System.currentTimeMillis()
            val isActivated = doc.activatedAt != null || doc.expiresAt != null

            val updates = if (!isActivated) {
                val newDuration = when (mode) {
                    DurationUpdateMode.ADD_DAYS -> (doc.durationDays + safeInputDays).coerceIn(1, 3650)
                    DurationUpdateMode.SET_DAYS -> safeInputDays
                }
                mapOf(
                    "durationDays" to newDuration,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                val newExpMs = when (mode) {
                    DurationUpdateMode.ADD_DAYS -> {
                        val baseMs = max(doc.expiresAt?.toDate()?.time ?: nowMs, nowMs)
                        baseMs + safeInputDays.toLong() * MILLIS_PER_DAY
                    }
                    DurationUpdateMode.SET_DAYS -> {
                        nowMs + safeInputDays.toLong() * MILLIS_PER_DAY
                    }
                }
                val newDurationDays = when (mode) {
                    DurationUpdateMode.ADD_DAYS -> (doc.durationDays + safeInputDays).coerceIn(1, 3650)
                    DurationUpdateMode.SET_DAYS -> safeInputDays
                }
                mapOf(
                    "durationDays" to newDurationDays,
                    "expiresAt" to Timestamp(Date(newExpMs)),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }

            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun resetDeviceBinding(keyCode: String): Result<Unit> {
        val normalized = normalizeKeyCode(keyCode)
        val docPath = "$COLLECTION_LICENSE_KEYS/$normalized"
        return try {
            requireUserId()
            db.collection(COLLECTION_LICENSE_KEYS).document(normalized)
                .update(
                    mapOf(
                        "boundDeviceId" to null,
                        "boundDeviceModel" to null,
                        "boundUserId" to null,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setKeyStatus(keyCode: String, status: String): Result<Unit> {
        val normalized = normalizeKeyCode(keyCode)
        val docPath = "$COLLECTION_LICENSE_KEYS/$normalized"
        return try {
            requireUserId()
            require(status == LicenseKeyDoc.STATUS_ACTIVE || status == LicenseKeyDoc.STATUS_REVOKED)
            db.collection(COLLECTION_LICENSE_KEYS).document(normalized)
                .update(
                    mapOf(
                        "status" to status,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateBuyerNote(keyCode: String, buyerNote: String): Result<Unit> {
        val normalized = normalizeKeyCode(keyCode)
        val docPath = "$COLLECTION_ADMIN_KEY_NOTES/$normalized"
        return try {
            requireUserId()
            val safeNote = buyerNote.trim().take(200)
            val noteRef = db.collection(COLLECTION_ADMIN_KEY_NOTES).document(normalized)
            val snap = noteRef.get().await()
            if (snap.exists()) {
                noteRef.update(
                    mapOf(
                        "buyerNote" to safeNote,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                noteRef.set(
                    mapOf(
                        "buyerNote" to safeNote,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteLicenseKey(keyCode: String): Result<Unit> {
        val normalized = normalizeKeyCode(keyCode)
        val docPath = "$COLLECTION_LICENSE_KEYS/$normalized"
        return try {
            requireUserId()
            val batch = db.batch()
            batch.delete(db.collection(COLLECTION_LICENSE_KEYS).document(normalized))
            batch.delete(db.collection(COLLECTION_ADMIN_KEY_NOTES).document(normalized))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- ADMIN SECRET PIN CONFIG (/adminConfig/security) ---

    suspend fun getAdminSecurityConfig(): Result<AdminSecurityConfigDoc?> {
        val docPath = "$COLLECTION_ADMIN_CONFIG/$DOC_SECURITY"
        return try {
            requireUserId()
            val snap = db.collection(COLLECTION_ADMIN_CONFIG).document(DOC_SECURITY).get().await()
            val doc = if (snap.exists()) {
                snap.toObject(
                    AdminSecurityConfigDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else null
            Result.success(doc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveAdminPin(newPin: String): Result<Unit> {
        val docPath = "$COLLECTION_ADMIN_CONFIG/$DOC_SECURITY"
        return try {
            requireUserId()
            val trimmed = newPin.trim()
            require(trimmed.length in 4..8 && trimmed.all { it.isDigit() }) {
                "PIN harus terdiri dari 4 hingga 8 digit angka."
            }
            val salt = PinCryptoHelper.generateSaltHex()
            val hash = PinCryptoHelper.hashPin(salt, trimmed)
            val docRef = db.collection(COLLECTION_ADMIN_CONFIG).document(DOC_SECURITY)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "pinSalt" to salt,
                        "pinHash" to hash,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "pinSalt" to salt,
                        "pinHash" to hash,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun verifyAdminPin(pin: String): Result<Boolean> {
        val configResult = getAdminSecurityConfig()
        return configResult.map { config ->
            if (config == null || config.pinSalt.isBlank() || config.pinHash.isBlank()) {
                false
            } else {
                PinCryptoHelper.verifyPin(pin.trim(), config.pinSalt, config.pinHash)
            }
        }
    }
}
