package com.sharepark.domain.usecase

import java.net.URLEncoder

/**
 * Builds a wa.me deep link that opens a WhatsApp chat with a specific contact, pre-filled with
 * a message. Manual shares leave the send to the user; the automation path has
 * WhatsAppAutoSendService press send.
 *
 * Kept free of android.* so it can be unit-tested on the plain JVM.
 */
object WhatsAppLinkBuilder {

    fun buildUrl(phoneNumber: String, message: String): String {
        val normalized = normalizePhoneNumber(phoneNumber)
        // URLEncoder is form encoding: it turns spaces into '+', which wa.me would show as a
        // literal plus. A real '+' in the text is already escaped to %2B, so this is safe.
        val encodedMessage = URLEncoder.encode(message, Charsets.UTF_8.name()).replace("+", "%20")
        return "https://wa.me/$normalized?text=$encodedMessage"
    }

    // Best-effort normalization for Israeli numbers entered in local format (05X-XXXXXXX),
    // since wa.me requires the full international number with no leading zero.
    internal fun normalizePhoneNumber(raw: String): String {
        val digitsOnly = raw.filter { it.isDigit() }
        return when {
            raw.trim().startsWith("+") -> digitsOnly
            digitsOnly.startsWith("972") -> digitsOnly
            digitsOnly.startsWith("0") -> "972" + digitsOnly.drop(1)
            else -> digitsOnly
        }
    }
}
