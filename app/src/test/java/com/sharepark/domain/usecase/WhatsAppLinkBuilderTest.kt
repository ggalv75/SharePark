package com.sharepark.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppLinkBuilderTest {

    @Test
    fun `local israeli number gets the country code instead of the leading zero`() {
        assertEquals("972501234567", WhatsAppLinkBuilder.normalizePhoneNumber("050-123-4567"))
    }

    @Test
    fun `international number keeps its digits`() {
        assertEquals("972501234567", WhatsAppLinkBuilder.normalizePhoneNumber("+972 50 123 4567"))
        assertEquals("14155550100", WhatsAppLinkBuilder.normalizePhoneNumber("+1 (415) 555-0100"))
    }

    @Test
    fun `number already starting with 972 is left alone`() {
        assertEquals("972501234567", WhatsAppLinkBuilder.normalizePhoneNumber("972501234567"))
    }

    @Test
    fun `message is percent-encoded with spaces as %20, not plus`() {
        val url = WhatsAppLinkBuilder.buildUrl("0501234567", "a b+c")
        assertEquals("https://wa.me/972501234567?text=a%20b%2Bc", url)
    }

    @Test
    fun `hebrew text and newlines survive encoding`() {
        val url = WhatsAppLinkBuilder.buildUrl("0501234567", "חניתי\nכאן")
        assertTrue(url.startsWith("https://wa.me/972501234567?text="))
        assertFalse(url.contains('\n'))
        assertFalse(url.contains(' '))
        assertTrue(url.contains("%0A"))
    }
}
