package com.faheem.habits.features.profile

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.faheem.habits.BuildConfig
import com.faheem.habits.core.data.supabase.SupabaseAuthApi
import com.faheem.habits.core.data.sync.SupabaseSyncScheduler
import com.faheem.habits.core.data.sync.ProjectWipeManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(@ApplicationContext private val appContext: Context) {
    val googleSignInAvailable: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun signIn(email: String, password: String) {
        SupabaseAuthApi.signIn(appContext, email, password)
        scheduleSyncAfterWipeCheck()
    }
    suspend fun signUp(email: String, password: String, name: String) = SupabaseAuthApi.signUp(email, password, name)
    suspend fun resetPassword(email: String) = SupabaseAuthApi.sendPasswordReset(email)
    suspend fun signOut(keepLocalData: Boolean = true) =
        SupabaseAuthApi.signOut(appContext, keepLocalData)
    suspend fun changePassword(email: String, current: String, next: String) =
        SupabaseAuthApi.changePassword(appContext, email, current, next)
    suspend fun setPassword(next: String) = SupabaseAuthApi.setPassword(appContext, next)
    suspend fun completePasswordRecovery(accessToken: String, next: String) = SupabaseAuthApi.completePasswordRecovery(accessToken, next)

    suspend fun signInWithGoogle(context: Context) {
        check(googleSignInAvailable) { "Google sign-in is not configured for this build." }
        val nonce = ProfileValidation.newNonce()
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setNonce(ProfileValidation.hashNonce(nonce))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(context).getCredential(context, request)
        val credential = result.credential as? CustomCredential
            ?: error("Google did not return a sign-in credential. Try again or use email and password.")
        check(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google returned an unsupported credential. Try email and password instead."
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val token = googleCredential.idToken
        val photoUrl = googleCredential.profilePictureUri
            ?.takeIf { it.scheme.equals("https", ignoreCase = true) }
            ?.toString()
        SupabaseAuthApi.signInWithGoogle(appContext, token, nonce, photoUrl, googleCredential.displayName)
        scheduleSyncAfterWipeCheck()
    }

    private suspend fun scheduleSyncAfterWipeCheck() {
        val result = runCatching { ProjectWipeManager.applyIfNeeded(appContext) }.getOrNull()
        if (result == null || result == ProjectWipeManager.CheckResult.UNCHANGED) SupabaseSyncScheduler.enqueue(appContext)
    }
}
