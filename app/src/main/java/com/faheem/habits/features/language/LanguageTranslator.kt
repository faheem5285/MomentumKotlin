package com.faheem.habits.features.language

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.LinkedHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object LanguageTranslator {
    const val AUTO_DETECT = "auto"
    private const val TAG = "LanguageTranslator"

    // High-performance translator client cache to prevent constant model reloading
    private val translatorCache = ConcurrentHashMap<String, Translator>()
    private const val MAX_TRANSLATION_CACHE_ENTRIES = 64
    private val translationCache = object : LinkedHashMap<String, TranslationResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TranslationResult>?): Boolean =
            size > MAX_TRANSLATION_CACHE_ENTRIES
    }

    suspend fun translate(text: String, source: String, target: String): TranslationResult = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "Enter text to translate." }
        val normalizedText = text.trim()
        val cacheKey = "$source->$target:$normalizedText"
        synchronized(translationCache) {
            translationCache[cacheKey]?.let { return@withContext it }
        }

        // 1. First attempt ultrafast Cloud Engine (supports instant paragraph translation, dictionary definitions & multiple meanings)
        try {
            val cloudResult = fetchCloudTranslation(normalizedText, source, target)
            if (cloudResult != null && cloudResult.text.isNotBlank()) {
                synchronized(translationCache) { translationCache[cacheKey] = cloudResult }
                return@withContext cloudResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fast cloud translation unavailable, falling back to on-device engine: ${e.message}")
        }

        // 2. On-Device Fallback (ML Kit)
        val supportedLanguages = TranslateLanguage.getAllLanguages()
        val targetCode = TranslateLanguage.fromLanguageTag(target) ?: target
        require(targetCode in supportedLanguages) { "Selected target language ($target) is not supported for on-device translation." }

        val sourceCode = if (source == AUTO_DETECT) {
            val detected = detectLanguage(normalizedText)
            TranslateLanguage.fromLanguageTag(detected) ?: detected
        } else {
            TranslateLanguage.fromLanguageTag(source) ?: source
        }

        require(sourceCode in supportedLanguages) {
            "Could not detect or verify the source language ($sourceCode). Please choose manually."
        }
        require(sourceCode != targetCode) { "Source and target languages must be different." }

        val pairCacheKey = "$sourceCode->$targetCode"
        val translator = translatorCache.getOrPut(pairCacheKey) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetCode)
                .build()
            Translation.getClient(options)
        }

        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        val translated = translator.translate(normalizedText).await()

        TranslationResult(
            text = translated,
            sourceLanguage = sourceCode,
            multipleMeanings = emptyList()
        ).also { result -> synchronized(translationCache) { translationCache[cacheKey] = result } }
    }

    private fun fetchCloudTranslation(text: String, source: String, target: String): TranslationResult? {
        val sl = if (source == AUTO_DETECT) "auto" else source
        val tl = target
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val endpoint = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&dt=bd&q=$encodedText"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 1800
            readTimeout = 2500
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            setRequestProperty("Accept", "*/*")
        }

        return try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                parseGtxResponse(response, source)
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseGtxResponse(jsonStr: String, requestedSource: String): TranslationResult? {
        return try {
            val root = JSONArray(jsonStr)

            // Parse main translation (handles entire sentences, multi-line text and long paragraphs)
            val sentencesArray = root.optJSONArray(0)
            val translatedBuilder = StringBuilder()
            if (sentencesArray != null) {
                for (i in 0 until sentencesArray.length()) {
                    val sentenceItem = sentencesArray.optJSONArray(i)
                    val piece = sentenceItem?.optString(0)
                    if (!piece.isNullOrEmpty()) {
                        translatedBuilder.append(piece)
                    }
                }
            }

            val translatedText = translatedBuilder.toString().trim()
            if (translatedText.isEmpty()) return null

            // Detect actual detected source language if AUTO_DETECT was used
            val detectedSource = root.optString(2).takeIf { it.isNotBlank() && it != "null" }
                ?: if (requestedSource != AUTO_DETECT) requestedSource else "en"

            // Parse multiple meanings / dictionary definitions if available (present for single words or short phrases)
            val meaningsList = mutableListOf<WordMeaningGroup>()
            val dictArray = root.optJSONArray(1)
            if (dictArray != null) {
                for (i in 0 until dictArray.length()) {
                    val group = dictArray.optJSONArray(i) ?: continue
                    val partOfSpeech = group.optString(0, "")
                    val meaningsArr = group.optJSONArray(1)
                    val definitions = mutableListOf<String>()
                    if (meaningsArr != null) {
                        for (j in 0 until meaningsArr.length()) {
                            val meaning = meaningsArr.optString(j)
                            if (!meaning.isNullOrBlank()) {
                                definitions.add(meaning)
                            }
                        }
                    }
                    if (definitions.isNotEmpty()) {
                        meaningsList.add(
                            WordMeaningGroup(
                                partOfSpeech = partOfSpeech.replaceFirstChar { it.uppercase() },
                                definitions = definitions
                            )
                        )
                    }
                }
            }

            TranslationResult(
                text = translatedText,
                sourceLanguage = detectedSource,
                multipleMeanings = meaningsList
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing translation response: ${e.message}", e)
            null
        }
    }

    private suspend fun detectLanguage(text: String): String {
        val identifier = LanguageIdentification.getClient()
        return try {
            identifier.identifyLanguage(text).await().takeIf { it != "und" }
                ?: error("The language could not be identified. Select the source language manually.")
        } finally {
            identifier.close()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            if (continuation.isActive) continuation.cancel()
        }
    }
}

internal data class TranslationResult(
    val text: String,
    val sourceLanguage: String,
    val multipleMeanings: List<WordMeaningGroup> = emptyList()
)

internal data class WordMeaningGroup(
    val partOfSpeech: String,
    val definitions: List<String>
)
