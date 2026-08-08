package com.sharepark.domain.usecase

import javax.inject.Inject

class ShareLocationUseCase @Inject constructor() {
    operator fun invoke(vehicleName: String, address: String?, latitude: Double, longitude: Double): String {
        val displayAddress = address ?: "$latitude, $longitude"
        val mapLink = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
        return """
            היי! הנה איפה חניתי את $vehicleName:
            📍 כתובת: $displayAddress
            🗺️ קישור למפה: $mapLink
        """.trimIndent()
    }
}
