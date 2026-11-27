package com.faheem.habits.core.data.supabase

import android.content.Context
import com.faheem.habits.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import com.faheem.habits.core.data.sync.MAX_FILE_TRANSFER_BYTES
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import com.faheem.habits.features.vault.VaultCrypto
import com.faheem.habits.core.data.sync.SyncCursor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.time.Instant

class SupabaseRestClient(
    private val context: Context,
    private val http: HttpClient = sharedSupabaseHttpClient
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Runs one request with the current token and retries exactly once after a
     * forced refresh if Supabase reports 401. Refreshes are serialized inside
     * SupabaseAuthApi, so this is safe under concurrent sync calls.
     */
    private suspend fun <T> authenticated(block: suspend (String) -> T): T {
        val token = requireToken()
        return try {
            block(token)
        } catch (failure: SupabaseRequestException) {
            if (failure.statusCode != HttpStatusCode.Unauthorized.value) throw failure
            val refreshed = SupabaseAuthApi.refreshAccessToken(appContext, force = true)
                ?: throw SupabaseRequestException(
                    HttpStatusCode.Unauthorized.value,
                    HttpErrorSanitizer.forHttp(HttpStatusCode.Unauthorized.value)
                )
            block(refreshed)
        }
    }

    suspend fun fetchProfile(userId: String): kotlinx.serialization.json.JsonObject? = authenticated { token ->
        val response = http.get {
            url("${baseUrl()}/rest/v1/profiles")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            url {
                parameters.append("select", "id,display_name,email,avatar_url,phone,bio,member_since,updated_at")
                parameters.append("id", "eq.$userId")
                parameters.append("limit", "1")
            }
        }
        response.requireStatus(setOf(HttpStatusCode.OK), "Profile could not be loaded")
        json.parseToJsonElement(response.bodyAsText()).jsonArray.firstOrNull()?.jsonObject
    }

    suspend fun saveProfile(profile: com.faheem.habits.core.data.ProfileCache): Boolean = authenticated { token ->
        val rows = buildJsonArray {
            add(buildJsonObject {
                put("id", profile.userId)
                put("display_name", profile.displayName)
                put("email", profile.email)
                profile.avatarUri.takeIf { it.startsWith("https://", ignoreCase = true) }
                    ?.let { put("avatar_url", it) }
                put("phone", profile.phone)
                put("bio", profile.bio)
                put("member_since", profile.memberSince)
                put("updated_at", Instant.ofEpochMilli(profile.updatedAt).toString())
            })
        }
        val response = http.postRest("profiles", token) {
            url { parameters.append("on_conflict", "id") }
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            setBody(rows.toString())
        }
        response.requireStatus(setOf(HttpStatusCode.Created, HttpStatusCode.OK), "Profile could not be saved")
        true
    }

    suspend fun requestAccountDeletion() = authenticated { token ->
        val response = http.post {
            url("${baseUrl()}/rest/v1/rpc/delete_my_account")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        response.requireStatus(
            setOf(HttpStatusCode.OK, HttpStatusCode.Created, HttpStatusCode.Accepted, HttpStatusCode.NoContent),
            "Account deletion failed"
        )
    }

    suspend fun upsert(table: String, rows: JsonArray): Int {
        if (rows.isEmpty()) return 0
        return authenticated { token ->
            val response = http.postRest(table, token) {
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                setBody(rows.toString())
            }
            response.requireStatus(setOf(HttpStatusCode.Created, HttpStatusCode.OK), "Supabase push failed")
            rows.size
        }
    }

    suspend fun pull(table: String, cursor: String, limit: Int = 200): List<JsonElement> = authenticated { token ->
        val position = SyncCursor.decode(cursor)
        val response = http.get {
            url("${baseUrl()}/rest/v1/$table")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            url {
                parameters.append("select", "*")
                if (position.id.isBlank()) {
                    parameters.append("updated_at", "gte.${position.updatedAt}")
                } else {
                    parameters.append(
                        "or",
                        "(updated_at.gt.${position.updatedAt},and(updated_at.eq.${position.updatedAt},id.gt.${position.id}))"
                    )
                }
                parameters.append("order", "updated_at.asc,id.asc")
                parameters.append("limit", limit.toString())
            }
        }
        response.requireStatus(setOf(HttpStatusCode.OK), "Supabase pull failed")
        json.parseToJsonElement(response.bodyAsText()).jsonArray
    }

    suspend fun deleteAll(table: String) = authenticated { token ->
        val response = http.delete {
            url("${baseUrl()}/rest/v1/$table")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            url { parameters.append("id", "not.is.null") }
        }
        response.requireStatus(
            setOf(HttpStatusCode.OK, HttpStatusCode.NoContent),
            "Supabase cloud deletion failed"
        )
    }

    /** Deletes cloud records and file objects for every user through the protected Edge Function. */
    suspend fun isCurrentUserAdmin(): Boolean {
        val token = SupabaseAuthApi.getAccessToken(appContext) ?: return false
        val response = http.get {
            url("${baseUrl()}/auth/v1/user")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
        }
        if (response.status != HttpStatusCode.OK) return false

        val user = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }
            .getOrNull() ?: return false
        val appMetadata = user["app_metadata"]?.jsonObject ?: return false
        val singleRole = appMetadata["role"]?.jsonPrimitive?.content
        val roles = appMetadata["roles"]?.let { value ->
            runCatching { value.jsonArray.map { it.jsonPrimitive.content } }.getOrDefault(emptyList())
        }.orEmpty()
        return singleRole == "admin" || "admin" in roles
    }

    suspend fun wipeProjectCloudData() = authenticated { token ->
        val response = http.post {
            url("${baseUrl()}/functions/v1/admin-wipe-cloud")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        response.requireStatus(
            setOf(HttpStatusCode.OK, HttpStatusCode.Accepted, HttpStatusCode.NoContent),
            "Project cloud wipe failed"
        )
    }

    suspend fun projectWipeState(): ProjectWipeState = authenticated { token ->
        val response = http.get {
            url("${baseUrl()}/rest/v1/momentum_wipe_state")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            url {
                parameters.append("select", "generation,updated_at,wipe_in_progress")
                parameters.append("id", "eq.1")
                parameters.append("limit", "1")
            }
        }
        val responseBody = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            if (responseBody.contains("PGRST205", ignoreCase = true) ||
                responseBody.contains("momentum_wipe_state", ignoreCase = true)
            ) {
                error("Supabase is missing the project wipe-state table. Run docs/supabase-schema.sql in this project's SQL Editor, then retry.")
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                throw SupabaseRequestException(
                    response.status.value,
                    HttpErrorSanitizer.forHttp(response.status.value)
                )
            }
            error(HttpErrorSanitizer.forHttp(response.status.value))
        }
        val row = json.parseToJsonElement(responseBody).jsonArray.firstOrNull()?.jsonObject
            ?: error("Project wipe status is missing. Apply the updated Supabase schema.")
        ProjectWipeState(
            generation = row["generation"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: error("Project wipe generation is invalid."),
            updatedAtEpochMillis = row["updated_at"]?.jsonPrimitive?.content?.let {
                runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
            } ?: error("Project wipe timestamp is invalid."),
            wipeInProgress = row["wipe_in_progress"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: error("Project wipe status is invalid. Apply the updated Supabase schema.")
        )
    }

    suspend fun deleteAllVaultKeys() = authenticated { token ->
        val response = http.delete {
            url("${baseUrl()}/rest/v1/momentum_vault_keys")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            url { parameters.append("user_id", "not.is.null") }
        }
        response.requireStatus(
            setOf(HttpStatusCode.OK, HttpStatusCode.NoContent),
            "Supabase Vault-key deletion failed"
        )
    }

    suspend fun deleteUpdatedSince(table: String, timestamp: String) = authenticated { token ->
        val response = http.delete {
            url("${baseUrl()}/rest/v1/$table")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            url { parameters.append("updated_at", "gte.$timestamp") }
        }
        response.requireStatus(
            setOf(HttpStatusCode.OK, HttpStatusCode.NoContent),
            "Supabase record deletion failed"
        )
    }

    suspend fun uploadStorage(bucket: String, path: String, bytes: ByteArray, mime: String) = authenticated { token ->
        val response = http.put {
            url("${baseUrl()}/storage/v1/object/$bucket/$path")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            header("x-upsert", "true")
            contentType(ContentType.parse(mime.ifBlank { "application/octet-stream" }))
            setBody(bytes)
        }
        response.requireStatus(setOf(HttpStatusCode.OK, HttpStatusCode.Created), "Supabase file upload failed")
    }

    /** Streams the source through a hard cap so oversized files are neither buffered nor uploaded. */
    suspend fun uploadStorageStream(
        bucket: String,
        path: String,
        input: InputStream,
        mime: String,
        maxBytes: Int = MAX_FILE_TRANSFER_BYTES
    ) = authenticated { token ->
        val response = http.put {
            url("${baseUrl()}/storage/v1/object/$bucket/$path")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            header("x-upsert", "true")
            contentType(ContentType.parse(mime.ifBlank { "application/octet-stream" }))
            setBody(BoundedInputStream(input, maxBytes))
        }
        response.requireStatus(setOf(HttpStatusCode.OK, HttpStatusCode.Created), "Supabase file upload failed")
    }

    suspend fun downloadStorage(bucket: String, path: String): ByteArray = authenticated { token ->
        val response = http.get {
            url("${baseUrl()}/storage/v1/object/$bucket/$path")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
        }
        response.requireStatus(setOf(HttpStatusCode.OK), "Supabase file download failed")
        response.body()
    }

    /** Writes a bounded download directly to disk instead of buffering the whole object in memory. */
    suspend fun downloadStorageTo(
        bucket: String,
        path: String,
        destination: File,
        maxBytes: Int = MAX_FILE_TRANSFER_BYTES
    ) = authenticated { token ->
        val response = http.get {
            url("${baseUrl()}/storage/v1/object/$bucket/$path")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
        }
        response.requireStatus(setOf(HttpStatusCode.OK), "Supabase file download failed")
        val channel = response.bodyAsChannel()
        val buffer = ByteBuffer.allocate(8192)
        var total = 0L
        FileOutputStream(destination).use { output ->
            while (!channel.isClosedForRead) {
                buffer.clear()
                val count = channel.readAvailable(buffer)
                if (count < 0) break
                if (count == 0) {
                    channel.awaitContent()
                    continue
                }
                total += count
                require(total <= maxBytes) { "Downloaded file exceeds the 10 MB safety limit." }
                output.write(buffer.array(), 0, count)
            }
        }
    }

    /**
     * Deletes known objects. A missing object is treated as already removed;
     * any other failure is surfaced without server text so a caller can retry
     * instead of leaving an orphan behind.
     */
    suspend fun deleteStorageObjects(
        paths: List<Pair<String, String>>,
        strict: Boolean = false
    ): Int = authenticated { token ->
        var removed = 0
        var failures = 0
        paths.forEach { (bucket, path) ->
            val status = runCatching {
                http.delete {
                    url("${baseUrl()}/storage/v1/object/$bucket/$path")
                    header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    header("Authorization", "Bearer $token")
                }.status
            }.getOrNull()
            when (status) {
                HttpStatusCode.OK, HttpStatusCode.NoContent, HttpStatusCode.NotFound -> removed++
                else -> failures++
            }
        }
        if (strict && failures > 0) {
            error("$failures stored file${if (failures == 1) "" else "s"} could not be removed.")
        }
        removed
    }

    suspend fun isReachable(): Boolean {
        if (!SupabaseProvider.isConfigured) return false
        val response = http.get {
            url("${baseUrl()}/auth/v1/health")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        }
        if (response.status != HttpStatusCode.OK) {
            error("Supabase health check failed. ${HttpErrorSanitizer.forHttp(response.status.value)}")
        }
        return true
    }

    suspend fun count(table: String): Int = authenticated { token ->
        val response = http.get {
            url("${baseUrl()}/rest/v1/$table")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            header("Prefer", "count=estimated")
            url {
                parameters.append("select", "id")
                // Deletes are synced as tombstones so other devices can remove the row.
                // They remain physically stored in Supabase and must not inflate the
                // user-facing count of active records.
                parameters.append("deleted_at", "is.null")
                parameters.append("limit", "1")
            }

        }
        response.requireStatus(
            setOf(HttpStatusCode.OK, HttpStatusCode.PartialContent),
            "Supabase record count failed"
        )
        response.headers["Content-Range"]
            ?.substringAfter('/')
            ?.toIntOrNull()
            ?: 0
    }

    suspend fun fetchVaultKey(): ByteArray? = authenticated { token ->
        val response = http.get {
            url("${baseUrl()}/rest/v1/momentum_vault_keys")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            url {
                parameters.append("select", "key_material")
                parameters.append("limit", "1")
            }
        }
        val responseText = response.bodyAsText()
        if (response.status == HttpStatusCode.NotFound ||
            responseText.contains("PGRST205", ignoreCase = true)
        ) {
            error(
                "Vault account lookup failed because `momentum_vault_keys` is missing from Supabase. " +
                    "Run the Vault account-key SQL migration from `docs/supabase-schema.sql` in the Supabase SQL Editor, then retry."
            )
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw SupabaseRequestException(response.status.value, HttpErrorSanitizer.forHttp(response.status.value))
        }
        if (response.status == HttpStatusCode.Forbidden) {
            error(
                "Vault account lookup was denied. Check that the signed-in account is valid " +
                    "and the `momentum_vault_keys` SELECT policy and authenticated role grants are installed."
            )
        }
        if (response.status != HttpStatusCode.OK) {
            throw SupabaseRequestException(response.status.value, HttpErrorSanitizer.forHttp(response.status.value))
        }
        val row = json.parseToJsonElement(responseText).jsonArray.firstOrNull()
            ?: return@authenticated null
        val encoded = row.jsonObject["key_material"]?.jsonPrimitive?.content
            ?: error("The account vault key record is invalid.")
        VaultCrypto.decode(encoded)
    }

    suspend fun saveVaultKeyIfAbsent(key: ByteArray) = authenticated { token ->
        val response = http.postRest("momentum_vault_keys", token) {
            url { parameters.append("on_conflict", "user_id") }
            header("Prefer", "resolution=ignore-duplicates,return=minimal")
            setBody(buildJsonArray {
                add(buildJsonObject { put("key_material", VaultCrypto.encode(key)) })
            }.toString())
        }
        response.requireStatus(
            setOf(HttpStatusCode.Created, HttpStatusCode.OK),
            "Vault account-key save failed"
        )
    }

    private suspend fun requireToken(): String =
        SupabaseAuthApi.getAccessToken(appContext)
            ?: error("Sign in to sync Zylo data.")

    private fun baseUrl(): String = BuildConfig.SUPABASE_URL.trimEnd('/')
}

internal class BoundedInputStream(
    private val source: InputStream,
    private val limit: Int
) : InputStream() {
    private var remaining = limit.toLong()

    private fun ensureAvailable() {
        if (remaining <= 0) throw IOException("File exceeds the 10 MB safety limit.")
    }

    override fun read(): Int {
        ensureAvailable()
        val value = source.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > buffer.size - length) {
            throw IndexOutOfBoundsException()
        }
        if (length == 0) return 0
        ensureAvailable()
        val count = source.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count > 0) remaining -= count
        return count
    }
}

/** Reuse Ktor's connection pool across sync, health checks and file operations. */
private val sharedSupabaseHttpClient: HttpClient = HttpClient(Android)

data class ProjectWipeState(
    val generation: Long,
    val updatedAtEpochMillis: Long,
    val wipeInProgress: Boolean
)

private suspend fun HttpClient.postRest(
    table: String,
    token: String,
    block: io.ktor.client.request.HttpRequestBuilder.() -> Unit
) = post {
    url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/$table")
    header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
    header("Authorization", "Bearer $token")
    contentType(ContentType.Application.Json)
    block()
}

class SupabaseRequestException(val statusCode: Int, message: String) : IllegalStateException(message)

private suspend fun io.ktor.client.statement.HttpResponse.requireStatus(
    accepted: Set<HttpStatusCode>,
    operation: String
) {
    if (status !in accepted) {
        throw SupabaseRequestException(status.value, "$operation. ${HttpErrorSanitizer.forHttp(status.value)}")
    }
}
