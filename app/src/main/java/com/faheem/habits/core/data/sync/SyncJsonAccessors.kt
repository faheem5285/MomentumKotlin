package com.faheem.habits.core.data.sync

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.format.DateTimeParseException

/** Null-safe primitives for cloud rows: JSON null columns must not throw. */
internal fun JsonElement?.primitiveContentOrNull(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    else -> null
}

internal fun JsonObject.string(name: String): String =
    this[name].primitiveContentOrNull().orEmpty()

internal fun JsonObject.int(name: String, default: Int = 0): Int =
    string(name).toIntOrNull() ?: default

internal fun JsonObject.long(name: String): Long =
    string(name).toLongOrNull() ?: 0L

internal fun JsonObject.nullableLong(name: String): Long? =
    this[name].primitiveContentOrNull()?.toLongOrNull()

internal fun JsonObject.nullableInt(name: String): Int? =
    this[name].primitiveContentOrNull()?.toIntOrNull()

internal fun JsonObject.bool(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.booleanOrNull ?: false

internal fun JsonObject.deleted(): Boolean =
    this["deleted_at"].primitiveContentOrNull() != null

internal fun JsonObject.instantMillis(name: String): Long =
    try {
        Instant.parse(string(name)).toEpochMilli()
    } catch (_: DateTimeParseException) {
        0L
    }

internal fun JsonObject.getBytea(name: String): ByteArray =
    string(name).removePrefix("\\x").chunked(2)
        .mapNotNull { it.toIntOrNull(16)?.toByte() }
        .toByteArray()

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
