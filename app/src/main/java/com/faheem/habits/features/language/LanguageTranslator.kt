package com.faheem.habits.features.language

import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object LanguageTranslator {
    suspend fun translate(text: String, source: String, target: String): TranslationResult {
        val sourceLanguage = if (source == AUTO_DETECT) detectLanguage(text) else source
        require(sourceLanguage in com.google.mlkit.nl.translate.TranslateLanguage.getAllLanguages()) {
            "The source language could not be identified. Choose a language and try again."
        }
        require(sourceLanguage != target) { "Choose two different languages." }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(target)
            .build()
        val translator = Translation.getClient(options)
        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            TranslationResult(
                text = translator.translate(text).await(),
                sourceLanguage = sourceLanguage
            )
        } finally {
            translator.close()
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

    const val AUTO_DETECT = "auto"
}

internal data class TranslationResult(
    val text: String,
    val sourceLanguage: String
)
