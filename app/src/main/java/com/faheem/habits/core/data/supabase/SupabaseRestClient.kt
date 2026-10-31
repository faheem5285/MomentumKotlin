package com.faheem.habits.core.data.supabase

import android.content.Context
import com.faheem.habits.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

class SupabaseRestClient(
    private val context: Context,
    private val http: HttpClient = HttpClient(Android)
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

    private suspend fun requireToken(): String =
        SupabaseAuthApi.getAccessToken(context)
            ?: error("Sign in to sync Momentum data.")

    private fun baseUrl(): String = BuildConfig.SUPABASE_URL.trimEnd('/')
}

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
