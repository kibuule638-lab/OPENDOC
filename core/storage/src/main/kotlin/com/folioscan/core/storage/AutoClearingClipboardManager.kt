package com.folioscan.core.storage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies sensitive identity/OCR fields to clipboard and automatically clears them
 * after the configured timeout (default 30s). Marks ClipData as sensitive on API 33+.
 */
@Singleton
class AutoClearingClipboardManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clearJob: Job? = null

    fun copySensitive(label: String, text: String, autoClearSeconds: Int = 30) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, text).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
        }
        manager.setPrimaryClip(clip)

        clearJob?.cancel()
        if (autoClearSeconds > 0) {
            clearJob = scope.launch {
                delay(autoClearSeconds * 1000L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    manager.clearPrimaryClip()
                } else {
                    manager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }
}
