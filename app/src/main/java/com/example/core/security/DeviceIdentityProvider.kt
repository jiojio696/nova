package com.example.core.security

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.security.SecureRandom

object DeviceIdentityProvider {

    /**
     * Produces a deterministic, privacy-safe SHA-256 device fingerprint matching ^[a-zA-Z0-9_\-]{8,128}$.
     * Falls back to a DataStore-persisted random UUID if ANDROID_ID is unavailable.
     */
    @SuppressLint("HardwareIds")
    fun resolveDeviceId(context: Context, fallbackSeed: String): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }

        val rawInput = if (androidId != null) {
            "nova-device-v1:${context.packageName}:$androidId"
        } else {
            "nova-device-fallback-v1:${context.packageName}:$fallbackSeed"
        }
        return sha256Hex(rawInput).take(32)
    }

    /**
     * Human-readable model string (1..100 chars) shown in the Admin Panel for bound device visibility.
     */
    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        val combined = when {
            manufacturer.isBlank() && model.isBlank() -> "Android Device"
            model.startsWith(manufacturer, ignoreCase = true) -> model
            manufacturer.isBlank() -> model
            else -> "$manufacturer $model"
        }
        return combined.replaceFirstChar { it.uppercase() }.take(100).ifBlank { "Android Device" }
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}

object PinCryptoHelper {
    private val secureRandom = SecureRandom()

    fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    fun hashPin(saltHex: String, pin: String): String {
        return DeviceIdentityProvider.sha256Hex("nova-admin-pin-v1:$saltHex:$pin")
    }

    fun verifyPin(pin: String, saltHex: String, expectedHash: String): Boolean {
        val computed = hashPin(saltHex, pin)
        return MessageDigest.isEqual(
            computed.toByteArray(Charsets.UTF_8),
            expectedHash.lowercase().toByteArray(Charsets.UTF_8)
        )
    }
}
