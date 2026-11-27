package com.faheem.habits.core.data.sync

/** Captures the local tombstone state for one known cloud object path. */
internal data class StorageTombstoneCandidate(
    val bucket: String,
    val path: String,
    val deleted: Boolean,
    val dirty: Boolean
)

/**
 * Only clean, deleted records may remove a cloud object. A dirty tombstone is
 * kept until its row has been pushed, so a failed sync never orphans an object
 * whose server-side record still exists.
 */
internal fun planStorageTombstoneDeletion(
    candidates: Collection<StorageTombstoneCandidate>,
    maxPaths: Int = 1_000
): List<Pair<String, String>> =
    candidates.asSequence()
        .filter { it.deleted && !it.dirty && it.path.isNotBlank() }
        .map { it.bucket to it.path }
        .distinct()
        .take(maxPaths.coerceAtLeast(0))
        .toList()
