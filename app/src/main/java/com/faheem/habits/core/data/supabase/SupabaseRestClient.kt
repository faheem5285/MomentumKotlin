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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import com.faheem.habits.features.vault.VaultCrypto
import java.time.Instant

class SupabaseRestClient(
    private val context: Context,
    private val http: HttpClient = sharedSupabaseHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun upsert(table: String, rows: JsonArray): Int {
        if (rows.isEmpty()) return 0
        val token = requireToken()
        val response = http.postRest(table, token) {
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            setBody(rows.toString())
        }
        check(response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
            "Supabase push failed (${response.status.value}): ${response.bodyAsText().take(500)}"
        }
        return rows.size
    }

    suspend fun pull(table: String, cursor: String, limit: Int = 200): List<JsonElement> {
        val token = requireToken()
        val response = http.get {
            url("${baseUrl()}/rest/v1/$table")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            url {
                parameters.append("select", "*")
                parameters.append("updated_at", "gt.$cursor")
                parameters.append("order", "updated_at.asc")
                parameters.append("limit", limit.toString())
            }
        }
        check(response.status == HttpStatusCode.OK) {
            "Supabase pull failed (${response.status.value}): ${response.bodyAsText().take(500)}"
        }
        return json.parseToJsonElement(response.bodyAsText()).jsonArray
    }

    suspend fun deleteAll(table: String) {
        val token = requireToken()
        val response = http.delete {
            url("${baseUrl()}/rest/v1/$table")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            url { parameters.append("id", "not.is.null") }
        }
        check(response.status == HttpStatusCode.NoContent) {
            "Supabase cloud deletion failed (${response.status.value})."
        }
    }

    /** Deletes cloud records and file objects for every user through the protected Edge Function. */
    suspend fun wipeProjectCloudData() {
        val token = requireToken()
        val response = http.post {
            url("${baseUrl()}/functions/v1/admin-wipe-cloud")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        check(response.status == HttpStatusCode.OK) {
            "Project cloud wipe failed (${response.status.value}): ${response.bodyAsText().take(500)}"
        }
    }

    suspend fun projectWipeState(): ProjectWipeState {
        val token = requireToken()
        val response = http.get {
            url("${baseUrl()}/rest/v1/momentum_wipe_state")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            url {
                parameters.append("select", "generation,updated_at")
                parameters.append("id", "eq.1")
                parameters.append("limit", "1")
            }
        }
        val responseBody = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            if (responseBody.contains("PGRST205", ignoreCase = true) ||
                responseBody.contains("momentum_wipe_state", ignoreCase = true)
            ) {
                error("Supabase is missing the project wipe-state table. Run docs/supabase-wipe-state.sql in this project's SQL Editor, then retry. Details: ${responseBody.take(350)}")
            }
            error("Project wipe status failed (${response.status.value}): ${responseBody.take(500)}")
        }
        val row = json.parseToJsonElement(responseBody).jsonArray.firstOrNull()?.jsonObject
            ?: error("Project wipe status is missing. Apply the updated Supabase schema.")
        return ProjectWipeState(
            generation = row["generation"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: error("Project wipe generation is invalid."),
            updatedAtEpochMillis = row["updated_at"]?.jsonPrimitive?.content?.let {
                runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
            } ?: 0L
        )
    }

    suspend fun deleteAllVaultKeys() {
        val token = requireToken()
        val response = http.delete {
            url("${baseUrl()}/rest/v1/momentum_vault_keys")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            url { parameters.append("user_id", "not.is.null") }
        }
        check(response.status == HttpStatusCode.NoContent) {
            "Supabase Vault-key deletion failed (${response.status.value}): ${response.bodyAsText().take(500)}"
        }
    }

    suspend fun deleteUpdatedSince(table: String, timestamp: String) {
        val token = requireToken()
        val response = http.delete {
            url("${baseUrl()}/rest/v1/$table")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            url { parameters.append("updated_at", "gte.$timestamp") }
        }
        check(response.status == HttpStatusCode.NoContent) {
            "Supabase record deletion failed (${response.status.value}): ${response.bodyAsText().take(500)}"
        }
    }

    suspend fun uploadStorage(bucket: String, path: String, bytes: ByteArray, mime: String) {
        val token = requireToken()
        val response = http.put {
            url("${baseUrl()}/storage/v1/object/$bucket/$path")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            header("x-upsert", "true")
            contentType(ContentType.parse(mime.ifBlank { "application/octet-stream" }))
            setBody(bytes)
        }
        check(response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Created) {
            "Supabase file upload failed (${response.status.value}): ${response.bodyAsText().take(500)}"
        }
    }

    suspend fun downloadStorage(bucket: String, path: String): ByteArray {
        val token = requireToken()
        val response = http.get {
            url("${baseUrl()}/storage/v1/object/$bucket/$path")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
        }
        check(response.status == HttpStatusCode.OK) {
            "Supabase file download failed (${response.status.value}): ${response.bodyAsText().take(500)}"
        }
        return response.body()
    }

    suspend fun isReachable(): Boolean {
        if (!SupabaseProvider.isConfigured) return false
        val response = http.get {
            url("${baseUrl()}/auth/v1/health")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        }
        if (response.status != HttpStatusCode.OK) {
            error("Supabase health check failed (${response.status.value}): ${response.bodyAsText().take(300)}")
        }
        return true
    }

    suspend fun count(table: String): Int {
        val token = requireToken()
        val response = http.get {
            url("${baseUrl()}/rest/v1/$table")
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            header("Authorization", "Bearer $token")
            header("Prefer", "count=exact")
            url {
                parameters.append("select", "id")
                parameters.append("limit", "1")
            }

        }
        check(response.status == HttpStatusCode.OK || response.status == HttpStatusCode.PartialContent) {
            "Supabase record count failed (${response.status.value}): ${response.bodyAsText().take(500)}"
        }
        return response.headers["Content-Range"]
            ?.substringAfter('/')
            ?.toIntOrNull()
            ?: 0
    }

    suspend fun fetchVaultKey(): ByteArray? {
        val token = requireToken()
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
        if (response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.Unauthorized) {
            error(
                "Vault account lookup was denied (${response.status.value}). Check that the signed-in account is valid " +
                    "and the `momentum_vault_keys` SELECT policy and authenticated role grants are installed. " +
                    responseText.take(300)
            )
        }
        check(response.status == HttpStatusCode.OK) {
            "Vault account-key lookup failed (${response.status.value}): ${responseText.take(500)}"
        }
        val row = json.parseToJsonElement(responseText).jsonArray.firstOrNull()
            ?: return null
        val encoded = row.jsonObject["key_material"]?.jsonPrimitive?.content
            ?: error("The account vault key record is invalid.")
        return VaultCrypto.decode(encoded)
    }

    suspend fun saveVaultKeyIfAbsent(key: ByteArray) {
        val token = requireToken()
        val response = http.postRest("momentum_vault_keys", token) {
            url { parameters.append("on_conflict", "user_id") }
            header("Prefer", "resolution=ignore-duplicates,return=minimal")
            setBody(buildJsonArray {
                add(buildJsonObject { put("key_material", VaultCrypto.encode(key)) })
            }.toString())
        }
        check(response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
            "Vault account-key save failed (${response.status.value}): ${response.bodyAsText().take(500)}"
        }
    }

    suspend fun storageUsage(bucket: String): StorageUsage {
        val token = requireToken()
        var offset = 0
        var objectCount = 0
        var bytes = 0L
        do {
            val response = http.post {
                url("${baseUrl()}/storage/v1/object/list/$bucket")
                header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("prefix", "")
                    put("limit", 1000)
                    put("offset", offset)
                    put("sortBy", buildJsonObject {
                        put("column", "name")
                        put("order", "asc")
                    })
                }.toString())
            }
            check(response.status == HttpStatusCode.OK) {
                "Supabase storage usage failed (${response.status.value}): ${response.bodyAsText().take(500)}"
            }
            val objects = json.parseToJsonElement(response.bodyAsText()).jsonArray
            objects.forEach { element ->
                val objectJson = element.jsonObject
                if (objectJson["id"] != null) {
                    objectCount++
                    bytes += objectJson["metadata"]?.jsonObject?.get("size")
                        ?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                }
            }
            offset += objects.size
        } while (objects.size == 1000)
        return StorageUsage(objectCount, bytes)
    }

    private suspend fun requireToken(): String =
        SupabaseAuthApi.getAccessToken(context)
            ?: error("Sign in to sync Zylo data.")

    private fun baseUrl(): String = BuildConfig.SUPABASE_URL.trimEnd('/')
}

/** Reuse Ktor's connection pool across sync, health checks and file operations. */
private val sharedSupabaseHttpClient: HttpClient = HttpClient(Android)

data class StorageUsage(val objectCount: Int, val bytes: Long)

data class ProjectWipeState(val generation: Long, val updatedAtEpochMillis: Long)

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
