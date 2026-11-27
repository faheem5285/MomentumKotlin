package com.faheem.habits.features.profile

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.faheem.habits.BuildConfig
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.supabase.SignUpOutcome
import com.faheem.habits.core.data.supabase.SupabaseAuthApi
import com.faheem.habits.core.data.sync.SupabaseSyncScheduler
import com.faheem.habits.core.data.sync.ProjectWipeManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SignUpAttempt(
    val outcome: SignUpOutcome,
    val accountLink: AccountLinkResult? = null
)

@Singleton
class AuthRepository @Inject constructor(@ApplicationContext private val appContext: Context) {
    val googleSignInAvailable: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    fun hasLocalRecords(): Boolean = AppDatabase.hasLocalRecords(appContext)
    fun hasActiveAccount(): Boolean = AppDatabase.activeUserId(appContext) != null

    /**
     * Every path that activates an account first snapshots the database that is
     * currently open. The callback runs before any Room file is replaced, so a
     * failed snapshot prevents a partial account link.
     */
    private class ActivationHook(
        val callback: suspend (String) -> Unit,
        private val snapshotTaken: BooleanArray,
        private val accountExisted: BooleanArray
    ) {
        val result: AccountLinkResult
            get() = AccountLinkResult(snapshotTaken[0], accountExisted[0])
    }

    private fun activationHook(): ActivationHook {
        val snapshotTaken = booleanArrayOf(false)
        val accountExisted = booleanArrayOf(false)
        return ActivationHook(
            callback = { userId ->
                snapshotTaken[0] = AppDatabase.snapshotForAccountLink(appContext)
                accountExisted[0] = AppDatabase.accountDatabaseExists(appContext, userId)
            },
            snapshotTaken = snapshotTaken,
            accountExisted = accountExisted
        )
    }

    suspend fun signIn(email: String, password: String): AccountLinkResult {
        val hook = activationHook()
        SupabaseAuthApi.signIn(appContext, email, password, hook.callback)
        scheduleSyncAfterWipeCheck()
        return hook.result
    }

    suspend fun signUp(email: String, password: String, name: String): SignUpAttempt {
        val hook = activationHook()
        val outcome = SupabaseAuthApi.signUp(appContext, email, password, name, hook.callback)
        if (outcome == SignUpOutcome.SESSION_STARTED) scheduleSyncAfterWipeCheck()
        return SignUpAttempt(
            outcome = outcome,
            accountLink = if (outcome == SignUpOutcome.SESSION_STARTED) hook.result else null
        )
    }

    suspend fun resendConfirmation(email: String) = SupabaseAuthApi.resendConfirmation(email)
    suspend fun resetPassword(email: String) = SupabaseAuthApi.sendPasswordReset(email)

    suspend fun signOut(keepLocalData: Boolean = true) {
        // Stop queued/running cloud work before removing the session so no
        // pending upload can run with an identity that is being cleared.
        runCatching { SupabaseSyncScheduler.cancel(appContext) }
        SupabaseAuthApi.signOut(appContext, keepLocalData)
    }

    suspend fun changePassword(email: String, current: String, next: String) =
        SupabaseAuthApi.changePassword(appContext, email, current, next)

    suspend fun setPassword(next: String) = SupabaseAuthApi.setPassword(appContext, next)
    suspend fun completePasswordRecovery(accessToken: String, next: String) =
        SupabaseAuthApi.completePasswordRecovery(accessToken, next)

    suspend fun signInWithGoogle(context: Context): AccountLinkResult {
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
        val hook = activationHook()
        SupabaseAuthApi.signInWithGoogle(
            appContext, token, nonce, photoUrl, googleCredential.displayName, hook.callback
        )
        scheduleSyncAfterWipeCheck()
        return hook.result
    }

    private suspend fun scheduleSyncAfterWipeCheck() {
        val result = runCatching { ProjectWipeManager.applyIfNeeded(appContext) }.getOrNull()
        if (result == null || result == ProjectWipeManager.CheckResult.UNCHANGED) SupabaseSyncScheduler.enqueue(appContext)
    }
}
