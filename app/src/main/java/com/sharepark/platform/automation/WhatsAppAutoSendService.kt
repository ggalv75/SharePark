package com.sharepark.platform.automation

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.sharepark.data.local.prefs.AutomationConfig
import com.sharepark.domain.usecase.WhatsAppLinkBuilder

/**
 * Accessibility-driven WhatsApp automation: when a parking event fires with automation enabled,
 * we open the target chat with the message pre-filled (wa.me link for a contact, WhatsApp's
 * share picker for a group) and this service presses the send button on the user's behalf.
 *
 * If the phone is locked/screen-off when parking is detected (the common case — phone in
 * pocket), the send is persisted and fires automatically at the next unlock.
 *
 * The user must explicitly enable this service under Android's Accessibility settings —
 * that grant is the "permission" for the whole feature.
 */
class WhatsAppAutoSendService : AccessibilityService() {

    private data class PendingSend(
        val text: String,
        val mode: String,
        val groupName: String,
        val createdAt: Long = System.currentTimeMillis(),
        var searchOpened: Boolean = false,
        var searchTyped: Boolean = false,
        var groupChatPicked: Boolean = false,
        var scrollAttempts: Int = 0
    )

    // Fires the deferred send once the user unlocks the phone.
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isScreenUsable(context)) return
            val stored = PendingAutomationStore.load(context) ?: return
            PendingAutomationStore.clear(context)
            Log.i(TAG, "Phone unlocked — firing deferred automated send")
            launchNow(
                context = this@WhatsAppAutoSendService,
                mode = stored.mode,
                phone = stored.phone,
                groupName = stored.groupName,
                message = stored.message
            )
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, unlockReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        Log.i(TAG, "Accessibility service connected")

        // A send may have been deferred while the process was dead — if the phone is already
        // unlocked when we reconnect, deliver it now.
        if (isScreenUsable(this)) {
            PendingAutomationStore.load(this)?.let { stored ->
                PendingAutomationStore.clear(this)
                Log.i(TAG, "Delivering automated send stored from a previous session")
                launchNow(this, stored.mode, stored.phone, stored.groupName, stored.message)
            }
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            // not registered — nothing to do
        }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != WHATSAPP_PACKAGE) return

        // Learn mode: record the exact chat label the user taps, so automation later searches
        // for a string WhatsApp provably renders.
        if (WhatsAppAutomationBridge.learnMode) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                extractChatLabel(event)?.let { label ->
                    Log.i(TAG, "Learned chat from user tap")
                    WhatsAppAutomationBridge.onChatLearned(label)
                }
            }
            return
        }

        val root = rootInActiveWindow ?: return
        val pending = pendingSend ?: return

        // Give up quietly if the flow didn't complete within the window (e.g. WhatsApp
        // showed something unexpected) — never keep clicking around indefinitely.
        if (System.currentTimeMillis() - pending.createdAt > PENDING_TIMEOUT_MS) {
            Log.w(TAG, "Automated send timed out before completing")
            pendingSend = null
            return
        }
        if (System.currentTimeMillis() - lastActionAt < ACTION_DEBOUNCE_MS) return

        if (pending.mode == AutomationConfig.MODE_GROUP && !pending.groupChatPicked) {
            if (handleGroupSelection(root, pending)) return
        }

        // Both flows: whenever a send button is visible (picker confirm arrow or the chat's
        // send button next to the pre-filled message), press it.
        val sendButton = root.findAccessibilityNodeInfosByViewId("$WHATSAPP_PACKAGE:id/send")
            ?.firstOrNull() ?: return
        if (clickNodeOrParent(sendButton)) {
            lastActionAt = System.currentTimeMillis()

            // The final send is the one that happens while the chat's message field holds our
            // text; after pressing it the flow is complete.
            val entry = root.findAccessibilityNodeInfosByViewId("$WHATSAPP_PACKAGE:id/entry")
                ?.firstOrNull()
            if (entry != null || pending.mode == AutomationConfig.MODE_CONTACT) {
                pendingSend = null
                Log.i(TAG, "Automated WhatsApp parking message sent")
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    /** Pulls the chat title out of a clicked row, falling back to the event's own text. */
    private fun extractChatLabel(event: AccessibilityEvent): String? {
        event.source?.let { source ->
            CHAT_NAME_VIEW_IDS.forEach { id ->
                source.findAccessibilityNodeInfosByViewId("$WHATSAPP_PACKAGE:id/$id")
                    ?.firstOrNull()
                    ?.text
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }
            source.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return event.text
            .mapNotNull { it?.toString() }
            .firstOrNull { it.isNotBlank() && it.length <= MAX_CHAT_NAME_LENGTH }
    }

    /**
     * Drives WhatsApp's share picker to the target group. Prefers the picker's own search box
     * (reliable regardless of how far down the chat list the group sits) and falls back to
     * scrolling if no search field is available.
     *
     * @return true when this pass performed an action and the caller should wait for the next event.
     */
    private fun handleGroupSelection(root: AccessibilityNodeInfo, pending: PendingSend): Boolean {
        val target = pending.groupName.trim()
        if (target.isEmpty()) return false

        // 1. Try clicking the row directly — it may already be visible among recent chats.
        findChatRowMatching(root, target)?.let { row ->
            if (clickNodeOrParent(row)) {
                pending.groupChatPicked = true
                lastActionAt = System.currentTimeMillis()
                Log.i(TAG, "Group row clicked")
                return true
            }
        }

        // 2. Open the picker's search field.
        if (!pending.searchOpened) {
            val searchButton = root.findAccessibilityNodeInfosByViewId("$WHATSAPP_PACKAGE:id/menuitem_search")
                ?.firstOrNull()
            if (searchButton != null && clickNodeOrParent(searchButton)) {
                pending.searchOpened = true
                lastActionAt = System.currentTimeMillis()
                Log.d(TAG, "Opened picker search field")
                return true
            }
        }

        // 3. Type the group name into the search field to filter the list.
        if (!pending.searchTyped) {
            val searchField = findEditableNode(root)
            if (searchField != null) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, target)
                }
                if (searchField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    pending.searchTyped = true
                    lastActionAt = System.currentTimeMillis()
                    Log.d(TAG, "Typed group name into search field")
                    return true
                }
            }
        }

        // 4. No search field available — fall back to scrolling the list.
        if (!pending.searchOpened && pending.scrollAttempts < MAX_SCROLL_ATTEMPTS) {
            val scrollable = findScrollableNode(root)
            if (scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true) {
                pending.scrollAttempts++
                lastActionAt = System.currentTimeMillis()
                Log.d(TAG, "Scrolled picker (attempt ${pending.scrollAttempts})")
                return true
            }
        }
        return false
    }

    private fun findChatRowMatching(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val normalizedTarget = normalize(target)
        if (normalizedTarget.isEmpty()) return null

        return chatNameNodes(root).firstOrNull { node ->
            val normalized = normalize(node.text?.toString().orEmpty())
            normalized.isNotEmpty() &&
                    (normalized == normalizedTarget ||
                            normalized.contains(normalizedTarget) ||
                            normalizedTarget.contains(normalized))
        }
    }

    /** Chat/contact title nodes in WhatsApp's picker and chat list. */
    private fun chatNameNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val byId = CHAT_NAME_VIEW_IDS.flatMap { id ->
            root.findAccessibilityNodeInfosByViewId("$WHATSAPP_PACKAGE:id/$id").orEmpty()
        }
        if (byId.isNotEmpty()) return byId

        // Fallback for WhatsApp builds whose view ids differ: any non-editable text node.
        val results = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!node.isEditable && !node.text.isNullOrBlank()) {
                results.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return results
    }

    /**
     * Strips emojis, punctuation, bidi/zero-width marks and collapses whitespace, so a name the
     * user picked still matches what WhatsApp renders.
     */
    private fun normalize(raw: String): String =
        raw.filter { it.isLetterOrDigit() || it.isWhitespace() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "WhatsAppAutoSend"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val PENDING_TIMEOUT_MS = 60_000L
        private const val ACTION_DEBOUNCE_MS = 700L
        private const val MAX_SCROLL_ATTEMPTS = 12
        private const val MAX_CHAT_NAME_LENGTH = 60
        private const val DEFERRED_DEADLINE_MS = 6 * 60 * 60_000L
        private const val LEARN_PLACEHOLDER_TEXT = "SharePark — בחירת יעד (אל תשלחו, רק הקישו על הצ'אט)"

        private val CHAT_NAME_VIEW_IDS = listOf(
            "conversations_row_contact_name",
            "contactpicker_row_name",
            "chat_able_contact_name"
        )

        @Volatile
        private var instance: WhatsAppAutoSendService? = null

        @Volatile
        private var pendingSend: PendingSend? = null

        @Volatile
        private var lastActionAt: Long = 0L

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val component = ComponentName(context, WhatsAppAutoSendService::class.java)
            return enabledServices.split(':').any {
                it.equals(component.flattenToString(), ignoreCase = true) ||
                        it.equals(component.flattenToShortString(), ignoreCase = true)
            }
        }

        /** Opens WhatsApp's picker so the user can tap the chat they want to target. */
        fun openPickerForLearning(context: Context) {
            WhatsAppAutomationBridge.startLearning()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, LEARN_PLACEHOLDER_TEXT)
                setPackage(WHATSAPP_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                (instance ?: context).startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open WhatsApp picker for learning", e)
                WhatsAppAutomationBridge.stopLearning()
            }
        }

        /**
         * Kicks off an automated send. If the phone is locked or the screen is off (parking
         * with the phone in your pocket), the send is persisted and fires at the next unlock —
         * a secure lock screen cannot be driven by automation.
         */
        fun begin(context: Context, mode: String, phone: String, groupName: String, message: String) {
            if (isScreenUsable(context)) {
                launchNow(context, mode, phone, groupName, message)
                return
            }

            // Screen is off — try waking it. If the device isn't securely locked this is enough
            // to proceed immediately; otherwise the send waits for the user to unlock.
            wakeScreen(context)
            if (isScreenUsable(context)) {
                launchNow(context, mode, phone, groupName, message)
                return
            }

            PendingAutomationStore.save(
                context,
                PendingAutomationStore.Pending(
                    message = message,
                    mode = mode,
                    phone = phone,
                    groupName = groupName,
                    deadline = System.currentTimeMillis() + DEFERRED_DEADLINE_MS
                )
            )
            Log.i(TAG, "Screen off or locked — deferred automated send until unlock")
        }

        private fun wakeScreen(context: Context) {
            try {
                val powerManager = context.getSystemService(PowerManager::class.java) ?: return
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                    "SharePark:automation"
                )
                wakeLock.acquire(WAKE_LOCK_MS)
                wakeLock.release()
            } catch (e: Exception) {
                Log.w(TAG, "Could not wake screen for automated send", e)
            }
        }

        private const val WAKE_LOCK_MS = 10_000L

        private fun isScreenUsable(context: Context): Boolean {
            val powerManager = context.getSystemService(PowerManager::class.java)
            val keyguardManager = context.getSystemService(KeyguardManager::class.java)
            return powerManager?.isInteractive == true && keyguardManager?.isKeyguardLocked == false
        }

        private fun launchNow(
            context: Context,
            mode: String,
            phone: String,
            groupName: String,
            message: String
        ) {
            pendingSend = PendingSend(text = message, mode = mode, groupName = groupName)
            lastActionAt = 0L

            val intent = if (mode == AutomationConfig.MODE_CONTACT) {
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(WhatsAppLinkBuilder.buildUrl(phone, message))
                ).apply { setPackage(WHATSAPP_PACKAGE) }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage(WHATSAPP_PACKAGE)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            try {
                // Launching from the connected service instance (when available) keeps the
                // activity start inside a system-bound context, which background restrictions
                // treat leniently.
                (instance ?: context).startActivity(intent)
                Log.i(TAG, "Launched WhatsApp for automated send (mode=$mode)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch WhatsApp for automated send", e)
                pendingSend = null
            }
        }
    }
}
