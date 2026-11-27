package com.faheem.habits.core.data.sync

/** Cloud profile objects are referenced by a sentinel until their local file is restored. */
internal const val CLOUD_PROFILE_PREFIX = "cloud:"

private val CLOUD_PATH_PREFIXES = listOf("contacts/", "loan-people/")

internal fun profilePathToStoredUri(path: String): String =
    path.trim().takeIf(String::isNotBlank)?.let { CLOUD_PROFILE_PREFIX + it.trim('/') }.orEmpty()

internal fun cloudProfilePath(uri: String): String? = when {
    uri.startsWith(CLOUD_PROFILE_PREFIX, ignoreCase = true) ->
        uri.substring(CLOUD_PROFILE_PREFIX.length).takeIf(String::isNotBlank)
    CLOUD_PATH_PREFIXES.any { uri.startsWith(it, ignoreCase = true) } -> uri
    else -> null
}

internal fun shouldUploadProfileImage(uri: String): Boolean =
    uri.isNotBlank() && cloudProfilePath(uri) == null
