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
import android.os.Handler
import android.os.Looper
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

    /**
     * Where the automated send has got to. The group flow has to walk WhatsApp's share picker
     * before it reaches a conversation, and every screen along the way looks similar enough
     * that guessing from the current window alone gets it wrong — so we track it explicitly.
     */
    private enum class Step {
        /** Group flow only: find the target chat in the share picker and tap it. */
        PICK_CHAT,

        /** Group flow only: press the picker's confirm arrow after the chat is selected. */
        CONFIRM_PICKER,

        /** Both flows: we're inside the conversation with the text pre-filled — press send. */
        SEND_IN_CHAT
    }

    private data class PendingSend(
        val text: String,
        val mode: String,
        val groupName: String,
        val createdAt: Long = System.currentTimeMillis(),
        var step: Step,
        var searchOpened: Boolean = false,
        var searchTyped: Boolean = false,
        var scrollAttempts: Int = 0,
        var confirmClickedAt: Long = 0L,
        var sendAttempts: Int = 0,
        var sendPresses: Int = 0,
        var lastSendPressAt: Long = 0L
    ) {
        /**
         * A distinctive slice of the message. Once it shows up in the window as a *message*
         * rather than as draft text, the send provably went through — the one positive
         * confirmation WhatsApp gives us.
         */
        val signature: String = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.length >= MIN_SIGNATURE_LENGTH }
            ?.take(MAX_SIGNATURE_LENGTH)
            .orEmpty()

        /** True once we've clicked something that could have sent the message. */
        val hasPressedSomething: Boolean get() = sendPresses > 0 || confirmClickedAt > 0L
    }

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
        val safeEvent = event ?: return
        if (safeEvent.packageName?.toString() !in WHATSAPP_PACKAGES) return

        // Learn mode: record the exact chat label the user taps, so automation later searches
        // for a string WhatsApp provably renders.
        if (WhatsAppAutomationBridge.learnMode) {
            if (safeEvent.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                extractChatLabel(safeEvent)?.let { label ->
                    Log.i(TAG, "Learned chat from user tap")
                    WhatsAppAutomationBridge.onChatLearned(label)
                }
            }
            return
        }

        val pending = pendingSend ?: return
        val root = rootInActiveWindow ?: return

        // Give up quietly if the flow didn't complete within the window (e.g. WhatsApp
        // showed something unexpected) — never keep clicking around indefinitely.
        if (System.currentTimeMillis() - pending.createdAt > PENDING_TIMEOUT_MS) {
            abort(pending, "לא הושלמה שליחה בזמן — ${describeStep(pending)}")
            return
        }
        if (System.currentTimeMillis() - lastActionAt < ACTION_DEBOUNCE_MS) return

        // Our message appearing as a bubble means it went out. Only trust this once we've
        // actually pressed something — the chat's history is full of previous parking
        // messages that read exactly the same, and those must not count as confirmation.
        if (pending.hasPressedSomething && isMessageVisible(root, pending)) {
            complete(pending)
            return
        }

        when (pending.step) {
            Step.PICK_CHAT -> handlePickChat(root, pending)
            Step.CONFIRM_PICKER -> handleConfirmPicker(root, pending)
            Step.SEND_IN_CHAT -> handleSendInChat(root, pending)
        }
    }

    // ── Group flow: picking the chat out of WhatsApp's share picker ─────────

    /**
     * Drives WhatsApp's share picker to the target group. Prefers the picker's own search box
     * (reliable regardless of how far down the chat list the group sits) and falls back to
     * scrolling if no search field is available.
     */
    private fun handlePickChat(root: AccessibilityNodeInfo, pending: PendingSend) {
        val target = pending.groupName.trim()
        if (target.isEmpty()) {
            abort(pending, "לא הוגדר שם קבוצה")
            return
        }

        // WhatsApp variants that open the conversation straight from the picker land here.
        // Only accept it when the conversation title is actually the chat we asked for —
        // otherwise we'd be typing into whatever chat happened to be open.
        if (findEntryNode(root) != null) {
            if (conversationTitleMatches(root, target)) {
                Log.i(TAG, "Picker opened the target conversation directly")
                pending.step = Step.SEND_IN_CHAT
            }
            // A conversation has no chat rows to pick from — anything that looks like one is a
            // message bubble, so never fall through to row matching here.
            return
        }

        // 1. Click the row directly — it may already be visible among the recent chats.
        findChatRowMatching(root, target)?.let { row ->
            if (clickNodeOrParent(row)) {
                pending.step = Step.CONFIRM_PICKER
                lastActionAt = System.currentTimeMillis()
                Log.i(TAG, "Group row clicked")
                return
            }
        }

        // 2. Open the picker's search field.
        if (!pending.searchOpened) {
            val searchButton = SEARCH_BUTTON_VIEW_IDS
                .firstNotNullOfOrNull { id -> findNodesByViewId(root, id).firstOrNull() }
            if (searchButton != null && clickNodeOrParent(searchButton)) {
                pending.searchOpened = true
                lastActionAt = System.currentTimeMillis()
                Log.d(TAG, "Opened picker search field")
                return
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
                    return
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
            }
        }
    }

    /**
     * The chat is selected; now press whatever confirms it. Depending on the WhatsApp build
     * that's a green arrow in the picker, a dialog's SEND button, or nothing at all because
     * we already landed in the conversation.
     */
    private fun handleConfirmPicker(root: AccessibilityNodeInfo, pending: PendingSend) {
        if (findEntryNode(root) != null) {
            Log.d(TAG, "Reached the conversation — sending from there")
            pending.step = Step.SEND_IN_CHAT
            return
        }

        // Press confirm exactly once. The picker can linger on screen for a frame or two after
        // the tap, and pressing it again would post the message to the group twice.
        if (pending.confirmClickedAt == 0L) {
            val confirmButton = findConfirmButton(root)
            if (confirmButton != null && clickNodeOrParent(confirmButton)) {
                pending.confirmClickedAt = System.currentTimeMillis()
                lastActionAt = pending.confirmClickedAt
                Log.i(TAG, "Pressed the picker's confirm button")
                armPostConfirmCompletion(pending)
                return
            }
        }

        // Some builds send straight from the picker and drop us back where we came from, so
        // there is no conversation to inspect. Treat a quiet stretch after the confirm as done.
        if (pending.confirmClickedAt > 0L &&
            System.currentTimeMillis() - pending.confirmClickedAt > POST_CONFIRM_GRACE_MS
        ) {
            Log.i(TAG, "No further screens after confirm — assuming the message went out")
            complete(pending)
        }
    }

    /**
     * Confirming in the picker can be the whole send: WhatsApp fires the message and leaves,
     * so no further events arrive to tell us it worked. Without this timer that success would
     * sit until the watchdog reported it as a failure.
     */
    private fun armPostConfirmCompletion(pending: PendingSend) {
        mainHandler.postDelayed(
            {
                // Only if this exact send is still the one in flight and hasn't moved on.
                if (pendingSend === pending && pending.step == Step.CONFIRM_PICKER) {
                    Log.i(TAG, "Nothing followed the confirm — treating the send as delivered")
                    complete(pending)
                }
            },
            POST_CONFIRM_GRACE_MS
        )
    }

    /** We're in the conversation with the text pre-filled: press the chat's send button. */
    private fun handleSendInChat(root: AccessibilityNodeInfo, pending: PendingSend) {
        val entry = findEntryNode(root)

        if (pending.sendPresses > 0) {
            // An empty input box — or no conversation at all any more — after we pressed send
            // means WhatsApp took the message.
            if (entry == null || entry.text.isNullOrBlank()) {
                complete(pending)
                return
            }
        }

        // Re-pressing send is only for a press that visibly did nothing — give WhatsApp real
        // time to clear the input box first, or a slow frame turns into a duplicate message.
        if (pending.sendPresses > 0 &&
            System.currentTimeMillis() - pending.lastSendPressAt < RESEND_INTERVAL_MS
        ) {
            return
        }

        val sendButton = SEND_BUTTON_VIEW_IDS
            .firstNotNullOfOrNull { id -> findNodesByViewId(root, id).firstOrNull { it.isVisibleToUser } }
            ?: findNodeByLabel(root, SEND_LABELS)

        if (sendButton == null) {
            // The conversation is open but the send button isn't there yet; wait for the next
            // frame rather than declaring failure.
            return
        }

        pending.sendAttempts++
        if (clickNodeOrParent(sendButton)) {
            pending.sendPresses++
            pending.lastSendPressAt = System.currentTimeMillis()
            lastActionAt = pending.lastSendPressAt
            Log.i(TAG, "Pressed send in the conversation")

            // Contact flow has nothing after this — the wa.me link opened the chat directly,
            // so one press is the whole job.
            if (pending.mode == AutomationConfig.MODE_CONTACT) {
                complete(pending)
            }
        } else if (pending.sendAttempts >= MAX_SEND_ATTEMPTS) {
            abort(pending, "כפתור השליחה ב-WhatsApp לא הגיב")
        }
    }

    // ── Completion ──────────────────────────────────────────────────────────

    private fun complete(pending: PendingSend) {
        pendingSend = null
        cancelWatchdog()
        Log.i(TAG, "Automated WhatsApp parking message sent (mode=${pending.mode})")
        AutomationStatusStore.record(
            this,
            AutomationStatusStore.Outcome.SENT,
            if (pending.mode == AutomationConfig.MODE_GROUP) {
                "ההודעה נשלחה לקבוצה \"${pending.groupName}\""
            } else {
                "ההודעה נשלחה לאיש הקשר"
            }
        )
        // Leave WhatsApp, but not so fast that we cut the send short.
        mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, HOME_DELAY_MS)
    }

    private fun abort(pending: PendingSend, reason: String) {
        pendingSend = null
        cancelWatchdog()
        Log.w(TAG, "Automated send aborted: $reason")
        AutomationStatusStore.record(this, AutomationStatusStore.Outcome.FAILED, reason)
    }

    private fun describeStep(pending: PendingSend): String = when (pending.step) {
        Step.PICK_CHAT -> "הקבוצה \"${pending.groupName}\" לא נמצאה ברשימת הצ'אטים של WhatsApp"
        Step.CONFIRM_PICKER -> "לא נמצא כפתור האישור אחרי בחירת הקבוצה"
        Step.SEND_IN_CHAT -> "לא נמצא כפתור השליחה בצ'אט"
    }

    // ── Node lookup helpers ─────────────────────────────────────────────────

    /** Pulls the chat title out of a clicked row, falling back to the event's own text. */
    private fun extractChatLabel(event: AccessibilityEvent): String? {
        event.source?.let { source ->
            CHAT_NAME_VIEW_IDS.forEach { id ->
                findNodesByViewId(source, id)
                    .firstOrNull()
                    ?.text
                    ?.toString()
                    ?.let { cleanChatLabel(it) }
                    ?.let { return it }
            }
            // No known title id — take the row's first text that isn't its message preview.
            firstTitleTextIn(source)?.let { return it }
            source.text?.toString()?.let { cleanChatLabel(it) }?.let { return it }
        }
        return event.text
            .mapNotNull { it?.toString() }
            .firstNotNullOfOrNull { cleanChatLabel(it) }
    }

    /**
     * A clicked row reports its whole subtree as one string — name, last message, timestamp,
     * unread count. Only the first line is the chat name, and a label carrying the rest never
     * matches anything later.
     */
    private fun cleanChatLabel(raw: String): String? = raw
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.take(MAX_CHAT_NAME_LENGTH)
        ?.takeIf { it.isNotBlank() }

    private fun firstTitleTextIn(source: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(source)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val viewId = node.viewIdResourceName?.substringAfterLast('/')
            if (viewId !in SUBTITLE_VIEW_IDS && !node.isEditable && !node.text.isNullOrBlank()) {
                cleanChatLabel(node.text.toString())?.let { return it }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /**
     * Picks the chat row for [target]. Matching is ranked rather than "any overlap wins": a
     * loose contains-match against every text on screen will happily click the wrong chat.
     */
    private fun findChatRowMatching(
        root: AccessibilityNodeInfo,
        target: String
    ): AccessibilityNodeInfo? {
        val normalizedTarget = normalize(target)
        if (normalizedTarget.isEmpty()) return null

        var best: AccessibilityNodeInfo? = null
        var bestScore = 0

        chatNameNodes(root).forEach { node ->
            val normalized = normalize(node.text?.toString().orEmpty())
            if (normalized.isEmpty()) return@forEach
            val score = when {
                normalized == normalizedTarget -> 3
                normalized.startsWith(normalizedTarget) || normalizedTarget.startsWith(normalized) -> 2
                // Substring matches only count once there's enough text for it to mean something.
                normalized.length >= MIN_PARTIAL_MATCH_LENGTH &&
                        normalizedTarget.length >= MIN_PARTIAL_MATCH_LENGTH &&
                        (normalized.contains(normalizedTarget) || normalizedTarget.contains(normalized)) -> 1
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                best = node
            }
        }
        return best
    }

    /** Chat/contact title nodes in WhatsApp's picker and chat list. */
    private fun chatNameNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val byId = CHAT_NAME_VIEW_IDS.flatMap { id -> findNodesByViewId(root, id) }
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

    private fun conversationTitleMatches(root: AccessibilityNodeInfo, target: String): Boolean {
        val normalizedTarget = normalize(target)
        if (normalizedTarget.isEmpty()) return false
        return CONVERSATION_TITLE_VIEW_IDS.any { id ->
            findNodesByViewId(root, id).any { node ->
                val normalized = normalize(node.text?.toString().orEmpty())
                normalized.isNotEmpty() &&
                        (normalized == normalizedTarget ||
                                normalized.startsWith(normalizedTarget) ||
                                normalizedTarget.startsWith(normalized))
            }
        }
    }

    /** True once our message is on screen as a sent bubble rather than as draft text. */
    private fun isMessageVisible(root: AccessibilityNodeInfo, pending: PendingSend): Boolean {
        if (pending.signature.isEmpty()) return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!node.isEditable && node.text?.contains(pending.signature) == true) return true
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun findEntryNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        ENTRY_VIEW_IDS.firstNotNullOfOrNull { id -> findNodesByViewId(root, id).firstOrNull() }

    private fun findConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        CONFIRM_BUTTON_VIEW_IDS
            .firstNotNullOfOrNull { id -> findNodesByViewId(root, id).firstOrNull { it.isVisibleToUser } }
            ?: findNodeByLabel(root, SEND_LABELS)

    /**
     * Looks a node up by id in both WhatsApp's own namespace and Android's, so dialog buttons
     * (`android:id/button1`) resolve through the same path as WhatsApp's own views.
     */
    private fun findNodesByViewId(
        root: AccessibilityNodeInfo,
        viewId: String
    ): List<AccessibilityNodeInfo> {
        val qualified = if (viewId.contains(':')) {
            listOf(viewId)
        } else {
            WHATSAPP_PACKAGES.map { "$it:id/$viewId" }
        }
        return qualified.flatMap { id ->
            root.findAccessibilityNodeInfosByViewId(id).orEmpty()
        }
    }

    /** Last-resort lookup for builds whose ids we don't know: match visible text or description. */
    private fun findNodeByLabel(
        root: AccessibilityNodeInfo,
        labels: List<String>
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser && !node.isEditable) {
                val text = node.text?.toString()?.trim().orEmpty()
                val description = node.contentDescription?.toString()?.trim().orEmpty()
                if (labels.any { it.equals(text, ignoreCase = true) || it.equals(description, ignoreCase = true) }) {
                    return node
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
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

    private fun cancelWatchdog() {
        watchdog?.let { mainHandler.removeCallbacks(it) }
        watchdog = null
    }

    companion object {
        private const val TAG = "WhatsAppAutoSend"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        private const val PENDING_TIMEOUT_MS = 60_000L
        private const val ACTION_DEBOUNCE_MS = 700L
        private const val POST_CONFIRM_GRACE_MS = 4_000L
        private const val RESEND_INTERVAL_MS = 2_500L
        private const val HOME_DELAY_MS = 1_200L
        private const val MAX_SCROLL_ATTEMPTS = 12
        private const val MAX_SEND_ATTEMPTS = 3
        private const val MAX_CHAT_NAME_LENGTH = 60
        private const val MIN_PARTIAL_MATCH_LENGTH = 3
        private const val MIN_SIGNATURE_LENGTH = 12
        private const val MAX_SIGNATURE_LENGTH = 40
        private const val DEFERRED_DEADLINE_MS = 6 * 60 * 60_000L
        private const val LEARN_PLACEHOLDER_TEXT = "SharePark — בחירת יעד (אל תשלחו, רק הקישו על הצ'אט)"

        private val WHATSAPP_PACKAGES = listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)

        private val CHAT_NAME_VIEW_IDS = listOf(
            "conversations_row_contact_name",
            "contactpicker_row_name",
            "chat_able_contact_name"
        )

        /** Row subtitles — never the chat's name, so they must not be learned as one. */
        private val SUBTITLE_VIEW_IDS = listOf(
            "conversations_row_message_text",
            "contactpicker_row_status",
            "single_msg_tv",
            "date_time",
            "conversations_row_date"
        )

        private val CONVERSATION_TITLE_VIEW_IDS = listOf(
            "conversation_contact_name",
            "conversation_contact_status"
        )

        private val SEARCH_BUTTON_VIEW_IDS = listOf(
            "menuitem_search",
            "search_button",
            "search"
        )

        private val ENTRY_VIEW_IDS = listOf("entry")

        private val SEND_BUTTON_VIEW_IDS = listOf("send")

        private val CONFIRM_BUTTON_VIEW_IDS = listOf(
            "send",
            "send_fab",
            "fab",
            "ok_btn",
            "android:id/button1"
        )

        private val SEND_LABELS = listOf("שלח", "שליחה", "Send", "SEND", "אישור", "OK")

        private val mainHandler = Handler(Looper.getMainLooper())

        @Volatile
        private var instance: WhatsAppAutoSendService? = null

        @Volatile
        private var pendingSend: PendingSend? = null

        @Volatile
        private var lastActionAt: Long = 0L

        @Volatile
        private var watchdog: Runnable? = null

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
            AutomationStatusStore.record(
                context,
                AutomationStatusStore.Outcome.DEFERRED,
                "המסך היה נעול — ההודעה תישלח עם פתיחת הנעילה"
            )
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
            val isGroup = mode == AutomationConfig.MODE_GROUP
            pendingSend = PendingSend(
                text = message,
                mode = mode,
                groupName = groupName,
                step = if (isGroup) Step.PICK_CHAT else Step.SEND_IN_CHAT
            )
            lastActionAt = 0L

            val intent = if (isGroup) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage(WHATSAPP_PACKAGE)
                }
            } else {
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(WhatsAppLinkBuilder.buildUrl(phone, message))
                ).apply { setPackage(WHATSAPP_PACKAGE) }
            }
            // CLEAR_TOP so a WhatsApp already sitting in recents doesn't just resume its old
            // screen instead of showing the picker/conversation we asked for.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

            try {
                // Launching from the connected service instance (when available) keeps the
                // activity start inside a system-bound context, which background restrictions
                // treat leniently.
                (instance ?: context).startActivity(intent)
                Log.i(TAG, "Launched WhatsApp for automated send (mode=$mode)")
                armWatchdog(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch WhatsApp for automated send", e)
                pendingSend = null
                AutomationStatusStore.record(
                    context,
                    AutomationStatusStore.Outcome.FAILED,
                    "לא ניתן היה לפתוח את WhatsApp"
                )
            }
        }

        /**
         * Accessibility events only arrive while WhatsApp is on screen, so a flow that stalls
         * after the user walks away would otherwise hang forever holding a stale pending send.
         */
        private fun armWatchdog(context: Context) {
            val appContext = context.applicationContext
            watchdog?.let { mainHandler.removeCallbacks(it) }
            val runnable = Runnable {
                val stalled = pendingSend ?: return@Runnable
                pendingSend = null
                Log.w(TAG, "Automated send never completed — giving up")
                AutomationStatusStore.record(
                    appContext,
                    AutomationStatusStore.Outcome.FAILED,
                    if (stalled.mode == AutomationConfig.MODE_GROUP) {
                        "השליחה לקבוצה \"${stalled.groupName}\" לא הושלמה — ודאו שהשם תואם בדיוק לשם הצ'אט ב-WhatsApp"
                    } else {
                        "השליחה לא הושלמה בתוך WhatsApp"
                    }
                )
            }
            watchdog = runnable
            mainHandler.postDelayed(runnable, PENDING_TIMEOUT_MS + POST_CONFIRM_GRACE_MS)
        }
    }
}
