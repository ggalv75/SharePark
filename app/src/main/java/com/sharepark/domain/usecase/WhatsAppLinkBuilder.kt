package com.sharepark.domain.usecase

import android.net.Uri

/**
 * Builds a wa.me deep link that opens a WhatsApp chat with a specific contact,
 * pre-filled with a message. The user still has to tap send in WhatsApp themselves.
 */
object WhatsAppLinkBuilder {

    fun buildUrl(phoneNumber: String, message: String): String {
        val normalized = normalizePhoneNumber(phoneNumber)
        val encodedMessage = Uri.encode(message)
        return "https://wa.me/$normalized?text=$encodedMessage"
    }

    // Best-effort normalization for Israeli numbers entered in local format (05X-XXXXXXX),
    // since wa.me requires the full international number with no leading zero.
    private fun normalizePhoneNumber(raw: String): String {
        val digitsOnly = raw.filter { it.isDigit() }
        return when {
            raw.trim().startsWith("+") -> digitsOnly
            digitsOnly.startsWith("972") -> digitsOnly
            digitsOnly.startsWith("0") -> "972" + digitsOnly.drop(1)
            else -> digitsOnly
        }
    }
}
