package com.faheem.habits.features.profile

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faheem.habits.core.data.ProfileCache
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    @Immutable data class SignedOut(
        val email: String = "", val password: String = "", val name: String = "", val createAccount: Boolean = false,
        val busy: Boolean = false, val error: String? = null, val notice: String? = null, val googleAvailable: Boolean = false
    ) : ProfileUiState
    @Immutable data class SignedIn(
        val profile: ProfileCache, val editing: Boolean = false, val busy: Boolean = false,
        val error: String? = null, val notice: String? = null, val pendingChanges: Boolean = false,
        val hasPassword: Boolean = true
    ) : ProfileUiState
}

sealed interface ProfileUiEvent {
    data object SignedIn : ProfileUiEvent
    data object SignedOut : ProfileUiEvent
    data class Message(val text: String) : ProfileUiEvent
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<ProfileUiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ProfileUiEvent> = mutableEvents.asSharedFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val id = profiles.currentUserId()
        if (id.isNullOrBlank()) {
            mutableState.value = ProfileUiState.SignedOut(googleAvailable = auth.googleSignInAvailable, notice = profiles.consumeAuthNotice())
        } else {
            mutableState.value = runCatching { ProfileUiState.SignedIn(profiles.load(id), hasPassword = profiles.hasPassword()) }
                .getOrElse { ProfileUiState.SignedOut(googleAvailable = auth.googleSignInAvailable, error = "Could not load profile. Your saved data is still available.") }
        }
    }

    fun updateAuth(email: String? = null, password: String? = null, name: String? = null, createAccount: Boolean? = null) {
        val old = mutableState.value as? ProfileUiState.SignedOut ?: return
        mutableState.value = old.copy(email = email ?: old.email, password = password ?: old.password, name = name ?: old.name, createAccount = createAccount ?: old.createAccount, error = null, notice = null)
    }

    fun authenticate() = viewModelScope.launch {
        val form = mutableState.value as? ProfileUiState.SignedOut ?: return@launch
        val email = form.email.trim()
        if (!ProfileValidation.isValidEmail(email) || !ProfileValidation.isValidPassword(form.password) || (form.createAccount && form.name.isBlank())) {
            mutableState.value = form.copy(error = "Enter a valid email, your name if creating an account, and a password with at least 8 characters.")
            return@launch
        }
        mutableState.value = form.copy(busy = true, error = null, notice = null)
        runCatching {
            if (form.createAccount) auth.signUp(email, form.password, form.name.trim().take(80)) else auth.signIn(email, form.password)
        }.onSuccess {
            if (form.createAccount) mutableState.value = form.copy(busy = false, createAccount = false, password = "", notice = "Check your email to confirm your account, then sign in.")
            else { mutableEvents.emit(ProfileUiEvent.SignedIn); refresh() }
        }.onFailure { mutableState.value = form.copy(busy = false, error = friendlyError(it)) }
    }

    fun signInWithGoogle(activityContext: Context) = viewModelScope.launch {
        val form = mutableState.value as? ProfileUiState.SignedOut ?: return@launch
        mutableState.value = form.copy(busy = true, error = null, notice = null)
        runCatching { auth.signInWithGoogle(activityContext) }
            .onSuccess { mutableEvents.emit(ProfileUiEvent.SignedIn); refresh() }
            .onFailure { mutableState.value = form.copy(busy = false, error = friendlyError(it)) }
    }

    fun resetPassword() = viewModelScope.launch {
        val form = mutableState.value as? ProfileUiState.SignedOut ?: return@launch
        if (!form.email.contains('@')) { mutableState.value = form.copy(error = "Enter your email address first."); return@launch }
        mutableState.value = form.copy(busy = true, error = null)
        runCatching { auth.resetPassword(form.email.trim()) }
            .onSuccess { mutableState.value = form.copy(busy = false, notice = "If an account exists, a password reset email is on its way.") }
            .onFailure { mutableState.value = form.copy(busy = false, error = friendlyError(it)) }
    }

    fun completePasswordRecovery(accessToken: String, next: String, confirmation: String) = viewModelScope.launch {
        if (!ProfileValidation.isValidPassword(next) || next != confirmation) {
            mutableEvents.emit(ProfileUiEvent.Message("Use at least 8 characters and make both passwords match."))
            return@launch
        }
        runCatching { auth.completePasswordRecovery(accessToken, next) }
            .onSuccess { mutableEvents.emit(ProfileUiEvent.Message("Password reset. Sign in with your new password.")) }
            .onFailure { mutableEvents.emit(ProfileUiEvent.Message(friendlyError(it))) }
    }

    fun editProfile() { (mutableState.value as? ProfileUiState.SignedIn)?.let { mutableState.value = it.copy(editing = true, error = null, notice = null) } }
    fun cancelEdit() { (mutableState.value as? ProfileUiState.SignedIn)?.let { mutableState.value = it.copy(editing = false, error = null, pendingChanges = false) }; refresh() }
    fun updateProfile(profile: ProfileCache) { (mutableState.value as? ProfileUiState.SignedIn)?.let { mutableState.value = it.copy(profile = profile, pendingChanges = true, error = null) } }

    fun saveProfile() = viewModelScope.launch {
        val current = mutableState.value as? ProfileUiState.SignedIn ?: return@launch
        if (current.profile.displayName.isBlank() || current.profile.displayName.length > 80 || current.profile.phone.length > 30 || current.profile.bio.length > 240) {
            mutableState.value = current.copy(error = "Check your name, phone number and bio before saving.")
            return@launch
        }
        mutableState.value = current.copy(busy = true, error = null)
        val synced = profiles.save(current.profile)
        mutableState.value = current.copy(busy = false, editing = false, pendingChanges = !synced,
            notice = if (synced) "Profile saved." else "Saved on this device. Cloud profile setup is needed to sync these details.")
    }

    fun changePassword(current: String, next: String, confirmation: String) = viewModelScope.launch {
        val signedIn = mutableState.value as? ProfileUiState.SignedIn ?: return@launch
        if (!ProfileValidation.isValidPassword(next) || next != confirmation) { mutableState.value = signedIn.copy(error = "Use at least 8 characters and make both new passwords match."); return@launch }
        mutableState.value = signedIn.copy(busy = true, error = null, notice = null)
        runCatching {
            if (signedIn.hasPassword) auth.changePassword(signedIn.profile.email, current, next)
            else auth.setPassword(next)
        }
            .onSuccess { mutableState.value = signedIn.copy(busy = false, notice = "Password changed.") }
            .onFailure { mutableState.value = signedIn.copy(busy = false, error = friendlyError(it)) }
    }

    fun signOut() = viewModelScope.launch { auth.signOut(); mutableEvents.emit(ProfileUiEvent.SignedOut); refresh() }
    fun requestDeleteAccount() = viewModelScope.launch {
        runCatching { profiles.deleteAccount() }
            .onSuccess { signOut() }
            .onFailure { mutableEvents.emit(ProfileUiEvent.Message(friendlyError(it))) }
    }

    private fun friendlyError(error: Throwable): String = when {
        error.message.orEmpty().contains("cancel", true) || error.javaClass.simpleName.contains("Cancellation", true) -> "Google sign-in was cancelled."
        error.javaClass.simpleName.contains("NoCredential", true) -> "No Google account is available. Add an account or use email and password."
        error is IOException || error is HttpRequestTimeoutException || error.message.orEmpty().contains("network", true) || error.message.orEmpty().contains("timeout", true) -> "You appear to be offline. Try again when connected."
        else -> "We could not complete that request. Check your details and try again."
    }
}
