package com.faheem.habits.core.data.supabase

import com.faheem.habits.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Small email/password wrapper around Supabase Auth's public HTTP endpoints.
 * Only the project's publishable key is sent; never use a service-role key here.
 */
object SupabaseAuthApi {
    private val http = HttpClient(Android)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Credentials(val email: String, val password: String)

    suspend fun signUp(email: String, password: String) {
        request("signup", email, password)
    }

    suspend fun signIn(email: String, password: String) {
        request("token?grant_type=password", email, password)
    }

    private suspend fun request(endpoint: String, email: String, password: String) {
        check(SupabaseProvider.isConfigured) {
            "Supabase is not configured. Check your local Gradle properties."
        }

        val response = http.post("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/$endpoint") {
            header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(Credentials(email, password)))
        }

        if (response.status.value !in 200..299) {
            val detail = response.bodyAsText().take(500)
            throw IllegalStateException("Supabase authentication failed (${response.status.value}): $detail")
        }
    }
}
