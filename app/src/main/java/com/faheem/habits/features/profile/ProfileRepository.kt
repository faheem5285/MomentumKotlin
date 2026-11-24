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
        val profile = if (remote == null) {
            cached ?: ProfileCache(
                userId = userId,
                displayName = prefs.getString("display_name", "").orEmpty(),
                email = prefs.getString("email", "").orEmpty(),
                memberSince = prefs.getLong("member_since", System.currentTimeMillis())
            )
        } else {
            ProfileCache(
                userId = userId,
                displayName = remote["display_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                email = prefs.getString("email", "").orEmpty(),
                phone = remote["phone"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                bio = remote["bio"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                avatarUri = cached?.avatarUri.orEmpty(),
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
