package com.sharepark.platform.notification

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible trampoline: the notification's "שתף" action launches this activity (the same
 * system-driven launch path as the notification's content tap, which ColorOS does allow),
 * and once we're briefly foreground we can legally open the share chooser, then finish.
 */
class ShareTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shareText = intent.getStringExtra(EXTRA_SHARE_TEXT)
        if (shareText != null) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            try {
                startActivity(Intent.createChooser(sendIntent, null))
            } catch (e: Exception) {
                Toast.makeText(this, "לא ניתן לפתוח את השיתוף", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_SHARE_TEXT = "share_text"
    }
}
