package com.faheem.habits.features.profile

import android.content.Context
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.ProfileCache
import com.faheem.habits.core.data.supabase.SupabaseAuthApi
import com.faheem.habits.core.data.supabase.SupabaseRestClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Singleton
class ProfileRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val database get() = AppDatabase.get(context)
    private val client by lazy { SupabaseRestClient(context) }

    fun observe(userId: String): Flow<ProfileCache?> = database.profileCacheDao().observe(userId)

    suspend fun load(userId: String): ProfileCache {
        val prefs = context.getSharedPreferences("account_profile", Context.MODE_PRIVATE)
        val cached = database.profileCacheDao().observe(userId).first()
        val remote = runCatching { client.fetchProfile(userId) }.getOrNull()
        val googleAvatar = prefs.getString("google_avatar_uri_$userId", "").orEmpty()
        val googleName = prefs.getString("google_display_name_$userId", "").orEmpty()
        val accountEmail = prefs.getString("email", "").orEmpty()
        val profile = if (remote == null) {
            cached?.let {
                it.copy(
                    displayName = it.displayName.ifBlank {
                        googleName.ifBlank { prefs.getString("display_name", "").orEmpty() }
                    },
                    email = it.email.ifBlank { accountEmail },
                    avatarUri = it.avatarUri.ifBlank { googleAvatar }
                )
            } ?: ProfileCache(
                userId = userId,
                displayName = googleName.ifBlank { prefs.getString("display_name", "").orEmpty() },
                email = accountEmail,
                avatarUri = googleAvatar,
                memberSince = prefs.getLong("member_since", System.currentTimeMillis())
            )
        } else {
            ProfileCache(
                userId = userId,
                displayName = remote["display_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    .ifBlank {
                        cached?.displayName.orEmpty().ifBlank {
                            googleName.ifBlank { prefs.getString("display_name", "").orEmpty() }
                        }
                    },
                email = cached?.email?.ifBlank { accountEmail } ?: accountEmail,
                phone = remote["phone"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                bio = remote["bio"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                avatarUri = cached?.avatarUri?.takeIf { it.isNotBlank() }
                    ?: remote["avatar_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: googleAvatar,
                memberSince = prefs.getLong("member_since", cached?.memberSince ?: System.currentTimeMillis()),
                updatedAt = System.currentTimeMillis()
            )
        }
        database.profileCacheDao().upsert(profile)
        return profile
    }

    suspend fun save(profile: ProfileCache): Boolean {
        val updated = profile.copy(updatedAt = System.currentTimeMillis())
        database.profileCacheDao().upsert(updated)
        return runCatching { client.saveProfile(updated) }.getOrDefault(false)
    }

    suspend fun deleteAccount() = client.requestAccountDeletion()

    fun currentUserId(): String? = SupabaseAuthApi.currentUserId(context)
    fun hasPassword(): Boolean = context.getSharedPreferences("account_profile", Context.MODE_PRIVATE)
        .getString("auth_provider", "email") != "google"

    fun consumeAuthNotice(): String? {
        val prefs = context.getSharedPreferences("account_profile", Context.MODE_PRIVATE)
        return prefs.getString("auth_notice", null)?.also { prefs.edit().remove("auth_notice").apply() }
    }
}
