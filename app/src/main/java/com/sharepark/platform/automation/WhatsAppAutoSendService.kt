package com.sharepark.platform.automation

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.sharepark.domain.usecase.WhatsAppLinkBuilder

/**
 * Accessibility-driven WhatsApp automation: when a parking event fires with automation enabled,
 * we open the target contact's chat with the message pre-filled via a wa.me link, and this
 * service presses the send button on the user's behalf.
 *
 * If the phone is locked/screen-off when parking is detected (the common case — phone in
 * pocket), the send is persisted and fires automatically at the next unlock.
 *
 * The user must explicitly enable this service under Android's Accessibility settings —
 * that grant is the "permission" for the whole feature. It only ever acts inside WhatsApp,
 * and only on the send button of a chat it opened itself.
 */
class WhatsAppAutoSendService : AccessibilityService() {

    private data class PendingSend(
        val text: String,
        val createdAt: Long = System.currentTimeMillis(),
        var sendAttempts: Int = 0
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
                phone = stored.phone,
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
                launchNow(this, stored.phone, stored.message)
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

        val pending = pendingSend ?: return
        val root = rootInActiveWindow ?: return

        // Give up quietly if the flow didn't complete within the window (e.g. WhatsApp
        // showed something unexpected) — never keep clicking around indefinitely.
        if (System.currentTimeMillis() - pending.createdAt > PENDING_TIMEOUT_MS) {
            abort("לא נמצא כפתור השליחה בצ'אט בזמן")
            return
        }
        if (System.currentTimeMillis() - lastActionAt < ACTION_DEBOUNCE_MS) return

        // The wa.me link opens the conversation with our text already in the input box, so the
        // only thing left is the send button next to it.
        if (findEntryNode(root) == null) return

        val sendButton = SEND_BUTTON_VIEW_IDS
            .firstNotNullOfOrNull { id -> findNodesByViewId(root, id).firstOrNull { it.isVisibleToUser } }
            ?: findNodeByLabel(root, SEND_LABELS)
            ?: return

        pending.sendAttempts++
        if (clickNodeOrParent(sendButton)) {
            lastActionAt = System.currentTimeMillis()
            complete()
        } else if (pending.sendAttempts >= MAX_SEND_ATTEMPTS) {
            abort("כפתור השליחה ב-WhatsApp לא הגיב")
        }
    }

    private fun complete() {
        pendingSend = null
        cancelWatchdog()
        Log.i(TAG, "Automated WhatsApp parking message sent")
        AutomationStatusStore.record(
            this,
            AutomationStatusStore.Outcome.SENT,
            "ההודעה נשלחה לאיש הקשר"
        )
        // Leave WhatsApp, but not so fast that we cut the send short.
        mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, HOME_DELAY_MS)
    }

    private fun abort(reason: String) {
        pendingSend = null
        cancelWatchdog()
        Log.w(TAG, "Automated send aborted: $reason")
        AutomationStatusStore.record(this, AutomationStatusStore.Outcome.FAILED, reason)
    }

    // ── Node lookup helpers ─────────────────────────────────────────────────

    private fun findEntryNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        ENTRY_VIEW_IDS.firstNotNullOfOrNull { id -> findNodesByViewId(root, id).firstOrNull() }

    /** Looks a node up by id across both the standard and business WhatsApp namespaces. */
    private fun findNodesByViewId(
        root: AccessibilityNodeInfo,
        viewId: String
    ): List<AccessibilityNodeInfo> = WHATSAPP_PACKAGES.flatMap { pkg ->
        root.findAccessibilityNodeInfosByViewId("$pkg:id/$viewId").orEmpty()
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
        private const val HOME_DELAY_MS = 1_200L
        private const val MAX_SEND_ATTEMPTS = 3
        private const val DEFERRED_DEADLINE_MS = 6 * 60 * 60_000L

        private val WHATSAPP_PACKAGES = listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)

        private val ENTRY_VIEW_IDS = listOf("entry")

        private val SEND_BUTTON_VIEW_IDS = listOf("send")

        private val SEND_LABELS = listOf("שלח", "שליחה", "Send", "SEND")

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

        /**
         * Kicks off an automated send. If the phone is locked or the screen is off (parking
         * with the phone in your pocket), the send is persisted and fires at the next unlock —
         * a secure lock screen cannot be driven by automation.
         */
        fun begin(context: Context, phone: String, message: String) {
            if (isScreenUsable(context)) {
                launchNow(context, phone, message)
                return
            }

            // Screen is off — try waking it. If the device isn't securely locked this is enough
            // to proceed immediately; otherwise the send waits for the user to unlock.
            wakeScreen(context)
            if (isScreenUsable(context)) {
                launchNow(context, phone, message)
                return
            }

            PendingAutomationStore.save(
                context,
                PendingAutomationStore.Pending(
                    message = message,
                    phone = phone,
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

        private fun launchNow(context: Context, phone: String, message: String) {
            pendingSend = PendingSend(text = message)
            lastActionAt = 0L

            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(WhatsAppLinkBuilder.buildUrl(phone, message))
            ).apply {
                setPackage(WHATSAPP_PACKAGE)
                // CLEAR_TOP so a WhatsApp already sitting in recents doesn't just resume its
                // old screen instead of the conversation we asked for.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            try {
                // Launching from the connected service instance (when available) keeps the
                // activity start inside a system-bound context, which background restrictions
                // treat leniently.
                (instance ?: context).startActivity(intent)
                Log.i(TAG, "Launched WhatsApp for automated send")
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
                if (pendingSend == null) return@Runnable
                pendingSend = null
                Log.w(TAG, "Automated send never completed — giving up")
                AutomationStatusStore.record(
                    appContext,
                    AutomationStatusStore.Outcome.FAILED,
                    "השליחה לא הושלמה בתוך WhatsApp"
                )
            }
            watchdog = runnable
            mainHandler.postDelayed(runnable, PENDING_TIMEOUT_MS)
        }
    }
}
