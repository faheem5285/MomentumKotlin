@file:Suppress("FunctionNaming")

package com.faheem.habits.features.profile

import com.faheem.habits.core.data.supabase.SignUpOutcome
import java.io.IOException
import io.ktor.client.plugins.HttpRequestTimeoutException

/** Pure models for the M5 auth state matrix and destructive-action wording. */

data class SignUpMessage(
    val notice: String? = null,
    val error: String? = null,
    val canResendConfirmation: Boolean = false
)

fun signUpMessage(outcome: SignUpOutcome): SignUpMessage = when (outcome) {
    SignUpOutcome.CONFIRMATION_REQUIRED -> SignUpMessage(
        notice = "Check your email to confirm your account, then sign in.",
        canResendConfirmation = true
    )
    SignUpOutcome.SESSION_STARTED -> SignUpMessage(notice = "Account created. You are signed in.")
    SignUpOutcome.ACCOUNT_EXISTS -> SignUpMessage(
        error = "An account may already exist for that address. Sign in with your existing method, or reset your password. If you previously used Google, sign in with Google and set a password in Profile."
    )
    SignUpOutcome.RATE_LIMITED -> SignUpMessage(error = "Too many requests. Wait a moment and try again.")
    SignUpOutcome.SERVICE_UNAVAILABLE -> SignUpMessage(error = "The cloud service is temporarily unavailable. Try again shortly.")
    SignUpOutcome.NETWORK_UNAVAILABLE -> SignUpMessage(error = "You appear to be offline. Try again when connected.")
    SignUpOutcome.REJECTED -> SignUpMessage(error = "We could not create that account. Check your details and try again.")
}

data class AccountLinkPrompt(val required: Boolean, val message: String)

object AccountLinkCopy {
    const val LOCAL_DATA = "You have records on this device. Signing in will link them to your account and may sync them to the cloud. Zylo saves a snapshot before linking them."
    const val SWITCHING = "You are switching accounts. The current account's local records stay with it, a snapshot is saved, and the new account's records are loaded."
    const val CONFIRM = "Link and continue"
    const val CANCEL = "Not now"
    const val LINKED_LOCAL = "Your local records were linked to this account. A snapshot was saved before linking."
    const val LOADED_ACCOUNT = "This account's saved records were loaded. A snapshot of the previous local records was kept."
}

fun accountLinkPrompt(hasLocalRecords: Boolean, switchingAccount: Boolean = false): AccountLinkPrompt =
    if (!hasLocalRecords) {
        AccountLinkPrompt(false, "")
    } else if (switchingAccount) {
        AccountLinkPrompt(true, AccountLinkCopy.SWITCHING)
    } else {
        AccountLinkPrompt(true, AccountLinkCopy.LOCAL_DATA)
    }

data class AccountLinkResult(val snapshotTaken: Boolean, val targetAccountExisted: Boolean) {
    /** Deterministic post-link copy; never claims cloud merge when an existing account was loaded. */
    val notice: String?
        get() = when {
            !snapshotTaken -> null
            targetAccountExisted -> AccountLinkCopy.LOADED_ACCOUNT
            else -> AccountLinkCopy.LINKED_LOCAL
        }
}

fun passwordChangeError(
    current: String,
    next: String,
    confirmation: String,
    hasPassword: Boolean
): String? = when {
    hasPassword && current.isBlank() -> "Enter your current password."
    !ProfileValidation.isValidPassword(next) || next != confirmation ->
        "Use at least 8 characters and make both new passwords match."
    hasPassword && current == next -> "Choose a new password that is different from the current one."
    else -> null
}

object DeletionCopy {
    const val FIRST = "This permanently deletes your cloud account. Known cloud file uploads from this device are removed first; cloud records require the delete_my_account RPC. Local records stay on this device and need a separate app reset to erase."
    const val FINAL = "The server must have a delete_my_account RPC installed. Without it, the cloud account and cloud records are not deleted, although known file uploads may already have been removed. Files known only to another device require server-side cleanup."
    const val SUCCESS = "Cloud account deletion requested. Local records remain on this device until you reset the app data."
    const val RPC_UNAVAILABLE = "The server could not delete the account. Cloud records remain. Ask the administrator to install the delete_my_account RPC."
}

/** Maps infrastructure failures to fixed, sanitized copy; no exception text reaches the UI. */
fun authErrorMessage(error: Throwable): String = when (error.message) {
    "The current password was not accepted." -> "The current password was not accepted."
    "Sign in again before changing your password.", "Sign in again before setting a password." ->
        "Your cloud session expired. Sign in again to use cloud sync; local records remain available."
    else -> when {
        error is IOException || error is HttpRequestTimeoutException ||
        error.message.orEmpty().contains("network", ignoreCase = true) ||
        error.message.orEmpty().contains("timeout", ignoreCase = true) ->
            "You appear to be offline. Try again when connected."
        error.message.orEmpty().contains("cancel", ignoreCase = true) ||
            error.javaClass.simpleName.contains("Cancellation", ignoreCase = true) ->
            "Google sign-in was cancelled."
        error.javaClass.simpleName.contains("NoCredential", ignoreCase = true) ->
            "No Google account is available. Add an account or use email and password."
        else -> "We could not complete that request. Check your details and try again."
    }
}
