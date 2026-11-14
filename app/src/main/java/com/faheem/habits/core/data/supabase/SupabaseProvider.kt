package com.faheem.habits.core.data.supabase

import com.faheem.habits.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Central entry point for Supabase.
 *
 * Credentials are supplied through local Gradle properties or environment variables,
 * never committed to the repository. The client remains unavailable until both values
 * are configured, so existing offline/local features continue to work during setup.
 */
object SupabaseProvider {

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isValidSupabaseUrl() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    fun createClientOrNull(): SupabaseClient? {
        if (!isConfigured) return null

        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Postgrest)
        }
    }

    private fun String.isValidSupabaseUrl(): Boolean =
        startsWith("https://") && contains(".supabase.co")
}
