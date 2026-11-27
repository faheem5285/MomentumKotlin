package com.faheem.habits.features.profile

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faheem.habits.core.data.ProfileCache
import dagger.hilt.android.lifecycle.HiltViewModel
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
        val busy: Boolean = false, val error: String? = null, val notice: String? = null, val googleAvailable: Boolean = false,
        val canResendConfirmation: Boolean = false,
        val pendingLinkMessage: String? = null
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
    private val mutableEvents = MutableSharedFlow<ProfileUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ProfileUiEvent> = mutableEvents.asSharedFlow()

    private var linkApproved = false
    private var pendingGoogleContext: Context? = null

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val id = profiles.currentUserId()
        if (id.isNullOrBlank()) {
            mutableState.value = ProfileUiState.SignedOut(
                googleAvailable = auth.googleSignInAvailable,
                notice = profiles.consumeAuthNotice()
            )
        } else {
            mutableState.value = runCatching { ProfileUiState.SignedIn(profiles.load(id), hasPassword = profiles.hasPassword()) }
                .getOrElse { ProfileUiState.SignedOut(googleAvailable = auth.googleSignInAvailable, error = "Could not load profile. Your saved data is still available.") }
        }
    }

    fun updateAuth(email: String? = null, password: String? = null, name: String? = null, createAccount: Boolean? = null) {
        val old = mutableState.value as? ProfileUiState.SignedOut ?: return
        mutableState.value = old.copy(
            email = email ?: old.email,
            password = password ?: old.password,
            name = name ?: old.name,
            createAccount = createAccount ?: old.createAccount,
            error = null,
            notice = null,
            canResendConfirmation = false,
            pendingLinkMessage = null
        )
    }

    /** Explicit local-data gate; no network or database activation happens before confirmation. */
    private fun requestLinkApproval(form: ProfileUiState.SignedOut): Boolean {
        if (linkApproved || !auth.hasLocalRecords()) return false
        pendingGoogleContext = null
        mutableState.value = form.copy(
            busy = false,
            pendingLinkMessage = accountLinkPrompt(true, auth.hasActiveAccount()).message
        )
        return true
    }

    fun confirmAccountLink() {
        val context = pendingGoogleContext
        pendingGoogleContext = null
        linkApproved = true
        if (context != null) {
            viewModelScope.launch { performGoogleSignIn(context) }
        } else {
            authenticate()
        }
    }

    fun cancelAccountLink() {
        linkApproved = false
        pendingGoogleContext = null
        (mutableState.value as? ProfileUiState.SignedOut)?.let {
            mutableState.value = it.copy(pendingLinkMessage = null, busy = false)
        }
    }

    private sealed interface AuthAttempt {
        data class SignUp(val attempt: SignUpAttempt) : AuthAttempt
        data class SignIn(val link: AccountLinkResult) : AuthAttempt
    }

    fun authenticate() = viewModelScope.launch {
        val form = mutableState.value as? ProfileUiState.SignedOut ?: return@launch
        if (requestLinkApproval(form)) return@launch
        val email = form.email.trim()
        if (!ProfileValidation.isValidEmail(email) || !ProfileValidation.isValidPassword(form.password) ||
            (form.createAccount && form.name.isBlank())
        ) {
            mutableState.value = form.copy(
                error = "Enter a valid email, your name if creating an account, and a password with at least 8 characters.",
                pendingLinkMessage = null
            )
            return@launch
        }
        mutableState.value = form.copy(busy = true, error = null, notice = null, pendingLinkMessage = null)
        runCatching {
            if (form.createAccount) {
                AuthAttempt.SignUp(auth.signUp(email, form.password, form.name.trim().take(80)))
            } else {
                AuthAttempt.SignIn(auth.signIn(email, form.password))
            }
        }.onSuccess { result ->
            linkApproved = false
            when (result) {
                is AuthAttempt.SignUp -> {
                    val attempt = result.attempt
                    val message = signUpMessage(attempt.outcome)
                    if (attempt.outcome == com.faheem.habits.core.data.supabase.SignUpOutcome.SESSION_STARTED) {
                        attempt.accountLink?.notice?.let { mutableEvents.emit(ProfileUiEvent.Message(it)) }
                        mutableEvents.emit(ProfileUiEvent.SignedIn)
                        refresh()
                    } else {
                        mutableState.value = form.copy(
                            busy = false,
                            createAccount = false,
                            password = "",
                            notice = message.notice,
                            error = message.error,
                            canResendConfirmation = message.canResendConfirmation
                        )
                    }
                }
                is AuthAttempt.SignIn -> {
                    result.link.notice?.let { mutableEvents.emit(ProfileUiEvent.Message(it)) }
                    mutableEvents.emit(ProfileUiEvent.SignedIn)
                    refresh()
                }
            }
        }.onFailure {
            linkApproved = false
            mutableState.value = form.copy(busy = false, error = authErrorMessage(it), pendingLinkMessage = null)
        }
    }

    fun resendConfirmation() = viewModelScope.launch {
        val form = mutableState.value as? ProfileUiState.SignedOut ?: return@launch
        val email = form.email.trim()
        if (!ProfileValidation.isValidEmail(email)) {
            mutableState.value = form.copy(error = "Enter your email address first.")
            return@launch
        }
        mutableState.value = form.copy(busy = true, error = null)
        runCatching { auth.resendConfirmation(email) }
            .onSuccess {
                mutableState.value = form.copy(
                    busy = false,
                    notice = "If confirmation is still needed, a new email is on its way.",
                    canResendConfirmation = true
                )
            }
            .onFailure {
                mutableState.value = form.copy(
                    busy = false,
                    error = authErrorMessage(it),
                    canResendConfirmation = true
                )
            }
    }

    fun signInWithGoogle(activityContext: Context) = viewModelScope.launch {
        val form = mutableState.value as? ProfileUiState.SignedOut ?: return@launch
        if (requestLinkApproval(form)) {
            pendingGoogleContext = activityContext.applicationContext
            return@launch
        }
        performGoogleSignIn(activityContext)
    }

    private suspend fun performGoogleSignIn(context: Context) {
        val form = mutableState.value as? ProfileUiState.SignedOut ?: return
        mutableState.value = form.copy(busy = true, error = null, notice = null, pendingLinkMessage = null)
        runCatching { auth.signInWithGoogle(context) }
            .onSuccess { link ->
                linkApproved = false
                link.notice?.let { mutableEvents.emit(ProfileUiEvent.Message(it)) }
                mutableEvents.emit(ProfileUiEvent.SignedIn)
                refresh()
            }
            .onFailure {
                linkApproved = false
                mutableState.value = form.copy(busy = false, error = authErrorMessage(it), pendingLinkMessage = null)
            }
    }

    fun resetPassword() = viewModelScope.launch {
        val form = mutableState.value as? ProfileUiState.SignedOut ?: return@launch
        val email = form.email.trim()
        if (!ProfileValidation.isValidEmail(email)) {
            mutableState.value = form.copy(error = "Enter your email address first.")
            return@launch
        }
        mutableState.value = form.copy(busy = true, error = null)
        runCatching { auth.resetPassword(email) }
            .onSuccess {
                mutableState.value = form.copy(
                    busy = false,
                    notice = "If an account exists, a password reset email is on its way."
                )
            }
            .onFailure { mutableState.value = form.copy(busy = false, error = authErrorMessage(it)) }
    }

    fun completePasswordRecovery(accessToken: String, next: String, confirmation: String) = viewModelScope.launch {
        val error = passwordChangeError("", next, confirmation, hasPassword = false)
        if (error != null) {
            mutableEvents.emit(ProfileUiEvent.Message(error))
            return@launch
        }
        runCatching { auth.completePasswordRecovery(accessToken, next) }
            .onSuccess { mutableEvents.emit(ProfileUiEvent.Message("Password reset. Sign in with your new password.")) }
            .onFailure { mutableEvents.emit(ProfileUiEvent.Message(authErrorMessage(it))) }
    }

    fun editProfile() { (mutableState.value as? ProfileUiState.SignedIn)?.let { mutableState.value = it.copy(editing = true, error = null, notice = null) } }
    fun cancelEdit() { (mutableState.value as? ProfileUiState.SignedIn)?.let { mutableState.value = it.copy(editing = false, error = null, pendingChanges = false) }; refresh() }
    fun updateProfile(profile: ProfileCache) { (mutableState.value as? ProfileUiState.SignedIn)?.let { mutableState.value = it.copy(profile = profile, pendingChanges = true, error = null) } }

    fun saveProfile() = viewModelScope.launch {
        val current = mutableState.value as? ProfileUiState.SignedIn ?: return@launch
        if (current.profile.displayName.isBlank() || current.profile.displayName.length > 80 ||
            current.profile.phone.length > 30 || current.profile.bio.length > 240
        ) {
            mutableState.value = current.copy(error = "Check your name, phone number and bio before saving.")
            return@launch
        }
        mutableState.value = current.copy(busy = true, error = null)
        val synced = profiles.save(current.profile)
        mutableState.value = current.copy(
            busy = false, editing = false, pendingChanges = !synced,
            notice = if (synced) "Profile saved." else "Saved on this device. Cloud profile setup is needed to sync these details."
        )
    }

    fun changePassword(current: String, next: String, confirmation: String) = viewModelScope.launch {
        val signedIn = mutableState.value as? ProfileUiState.SignedIn ?: return@launch
        val error = passwordChangeError(current, next, confirmation, signedIn.hasPassword)
        if (error != null) {
            mutableState.value = signedIn.copy(error = error)
            return@launch
        }
        mutableState.value = signedIn.copy(busy = true, error = null, notice = null)
        runCatching {
            if (signedIn.hasPassword) auth.changePassword(signedIn.profile.email, current, next)
            else auth.setPassword(next)
        }
            .onSuccess { mutableState.value = signedIn.copy(busy = false, notice = "Password changed.") }
            .onFailure { mutableState.value = signedIn.copy(busy = false, error = authErrorMessage(it)) }
    }

    fun signOut(keepLocalData: Boolean = true) = viewModelScope.launch {
        auth.signOut(keepLocalData)
        mutableEvents.emit(ProfileUiEvent.SignedOut)
        refresh()
    }

    fun requestDeleteAccount() = viewModelScope.launch {
        runCatching { profiles.deleteAccount() }
            .onSuccess { mutableEvents.emit(ProfileUiEvent.Message(DeletionCopy.SUCCESS)) }
            .onFailure { mutableEvents.emit(ProfileUiEvent.Message(authErrorMessage(it))) }
    }
}
