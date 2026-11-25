package com.faheem.habits.features.language

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.io.IOException

internal object LanguageProofreader {
    private const val ENDPOINT = "https://api.languagetool.org/v2/check"
    private const val MAX_REQUEST_BYTES = 20 * 1024

    suspend fun proofread(text: String, language: String): ProofreadingResult = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "Enter text to proofread." }
        val form = "text=${URLEncoder.encode(text, "UTF-8")}&language=${URLEncoder.encode(language, "UTF-8")}" 
        val body = form.toByteArray(StandardCharsets.UTF_8)
        require(body.size <= MAX_REQUEST_BYTES) { "Shorten the text before checking it." }

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val responseStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = responseStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                error("Proofreading service is unavailable (HTTP $status). Try again shortly.")
            }
            applySuggestions(text, JSONObject(response))
        } catch (error: SocketTimeoutException) {
            throw IOException(
                "LanguageTool did not respond in time. Check your connection, VPN, or firewall, then try again.",
                error
            )
        } catch (error: UnknownHostException) {
            throw IOException(
                "Could not reach LanguageTool. Check your internet connection, then try again.",
                error
            )
        } catch (error: ConnectException) {
            throw IOException(
                "Could not connect to LanguageTool. Check your connection or network restrictions, then try again.",
                error
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun applySuggestions(original: String, response: JSONObject): ProofreadingResult {
        val matches = response.optJSONArray("matches") ?: return ProofreadingResult(original, 0)
        val suggestions = (0 until matches.length()).mapNotNull { index ->
            val match = matches.optJSONObject(index) ?: return@mapNotNull null
            val replacements = match.optJSONArray("replacements") ?: return@mapNotNull null
            val replacement = replacements.optJSONObject(0)?.optString("value")
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val offset = match.optInt("offset", -1)
            val length = match.optInt("length", -1)
            if (offset < 0 || length < 0 || offset + length > original.length) null
            else TextSuggestion(offset, length, replacement)
        }.sortedByDescending { it.offset }

        var corrected = original
        var lastAppliedStart = original.length + 1
        var appliedCount = 0
        suggestions.forEach { suggestion ->
            if (suggestion.offset + suggestion.length <= lastAppliedStart) {
                val currentText = corrected.substring(suggestion.offset, suggestion.offset + suggestion.length)
                if (currentText != suggestion.replacement) {
                    corrected = corrected.replaceRange(
                        suggestion.offset,
                        suggestion.offset + suggestion.length,
                        suggestion.replacement
                    )
                    appliedCount++
                }
                lastAppliedStart = suggestion.offset
            }
        }
        return ProofreadingResult(corrected, appliedCount)
    }

    private data class TextSuggestion(val offset: Int, val length: Int, val replacement: String)
}

internal data class ProofreadingResult(val correctedText: String, val correctionsApplied: Int)
