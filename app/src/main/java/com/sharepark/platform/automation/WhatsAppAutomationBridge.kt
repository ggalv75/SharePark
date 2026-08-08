package com.sharepark.platform.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state between the accessibility service and the settings UI.
 *
 * "Learn mode" exists because matching a group by a hand-typed name is unreliable: emojis,
 * bidi marks and double spaces make the typed string differ from what WhatsApp renders.
 * Instead the user taps the target chat once inside WhatsApp and the service records the
 * exact label it saw — so the later automated run searches for a string that provably exists.
 */
object WhatsAppAutomationBridge {

    @Volatile
    var learnMode: Boolean = false
        private set

    private val _learnedChatName = MutableStateFlow<String?>(null)
    val learnedChatName: StateFlow<String?> = _learnedChatName.asStateFlow()

    fun startLearning() {
        _learnedChatName.value = null
        learnMode = true
    }

    fun stopLearning() {
        learnMode = false
    }

    fun onChatLearned(name: String) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return
        _learnedChatName.value = cleaned
        learnMode = false
    }

    fun consumeLearnedChat() {
        _learnedChatName.value = null
    }
}
