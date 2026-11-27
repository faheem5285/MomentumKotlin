package com.faheem.habits.features.language

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal object LanguageTranslator {
    const val AUTO_DETECT = "auto"

    private val translatorCache = ConcurrentHashMap<String, Translator>()
    private const val MAX_TRANSLATION_CACHE_ENTRIES = 64
    private val translationCache = object : LinkedHashMap<String, TranslationResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TranslationResult>?): Boolean =
            size > MAX_TRANSLATION_CACHE_ENTRIES
    }

    suspend fun translate(text: String, source: String, target: String): TranslationResult =
        withContext(Dispatchers.IO) {
            require(text.isNotBlank()) { "Enter text to translate." }
            val normalizedText = text.trim()
            val cacheKey = "$source->$target:$normalizedText"
            synchronized(translationCache) {
                translationCache[cacheKey]?.let { return@withContext it }
            }

            val supportedLanguages = TranslateLanguage.getAllLanguages()
            val targetCode = TranslateLanguage.fromLanguageTag(target) ?: target
            require(targetCode in supportedLanguages) { "Target language is not supported for on-device translation." }

            val sourceCode = if (source == AUTO_DETECT) {
                val detected = detectLanguage(normalizedText)
                TranslateLanguage.fromLanguageTag(detected) ?: detected
            } else {
                TranslateLanguage.fromLanguageTag(source) ?: source
            }

            require(sourceCode in supportedLanguages) {
                "Could not identify the source language. Choose it manually."
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

    private suspend fun detectLanguage(text: String): String {
        val identifier = LanguageIdentification.getClient()
        return try {
            identifier.identifyLanguage(text).await().takeIf { it != "und" }
                ?: error("Could not identify the source language. Select it manually.")
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
