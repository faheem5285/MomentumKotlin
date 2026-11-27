package com.faheem.habits.core.data.sync

import android.content.Context
import com.faheem.habits.core.data.AppDatabase
import java.security.MessageDigest

internal const val PENDING_CLOUD_DELETE_PREFS = "momentum_pending_cloud_deletes_v2"
internal const val PENDING_VAULT_KEYS = "__momentum_vault_keys__"

/**
 * Pending cloud deletes are stored under a hash of the immutable account id.
 * The raw account id is never written into preference names or values.
 */
internal fun pendingDeleteAccountKey(accountId: String): String {
    require(accountId.isNotBlank()) { "A signed-in account is required." }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(accountId.trim().lowercase().toByteArray(Charsets.UTF_8))
    return "pending_" + digest.joinToString("") { "%02x".format(it) }
}

internal fun mergePendingDeletes(existing: Set<String>, tables: Collection<String>): Set<String> =
    (existing + tables).toSortedSet()

internal fun withoutPendingDelete(existing: Set<String>, table: String): Set<String> =
    existing.filterNot { it == table }.toSet()

/** Durable queue of cloud deletions that must outlive a local data reset. */
internal class PendingCloudDeleteStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PENDING_CLOUD_DELETE_PREFS, Context.MODE_PRIVATE)
    private val key = pendingDeleteAccountKey(
        AppDatabase.activeUserId(context).takeUnless { it.isNullOrBlank() }
            ?: error("A signed-in account is required.")
    )

    fun pending(): Set<String> =
        preferences.getStringSet(key, emptySet()).orEmpty().toSet()

    fun enqueue(tables: Collection<String>): Set<String> {
        val updated = mergePendingDeletes(pending(), tables)
        check(preferences.edit().putStringSet(key, updated).commit()) {
            "Could not save the pending cloud deletion."
        }
        return updated
    }

    fun complete(table: String): Set<String> {
        val updated = withoutPendingDelete(pending(), table)
        check(preferences.edit().putStringSet(key, updated).commit()) {
            "Could not update the pending cloud deletion."
        }
        return updated
    }
}
