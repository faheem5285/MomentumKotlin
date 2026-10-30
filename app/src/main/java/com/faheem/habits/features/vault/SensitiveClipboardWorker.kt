@file:Suppress("ReturnCount")

package com.faheem.habits.features.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.security.MessageDigest

class SensitiveClipboardWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val expected = inputData.getString(KEY_HASH) ?: return Result.failure()
        val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val current = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(applicationContext)
            ?.toString()
            ?: return Result.success()
        if (MessageDigest.isEqual(hash(current), VaultCrypto.decode(expected))) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        return Result.success()
    }

    private fun hash(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

    companion object {
        const val KEY_HASH = "sensitive_clipboard_hash"
    }
}
