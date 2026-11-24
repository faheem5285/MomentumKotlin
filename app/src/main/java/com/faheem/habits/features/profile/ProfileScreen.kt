@file:Suppress("FunctionNaming", "LongMethod")

package com.faheem.habits.features.profile

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faheem.habits.core.data.ProfileCache
import com.faheem.habits.core.ui.EmptyState
import com.faheem.habits.core.ui.GlassCard
import com.faheem.habits.core.ui.MomentumTextField
import com.faheem.habits.core.ui.PasswordField
import com.faheem.habits.core.ui.PrimaryButton
import com.faheem.habits.core.ui.SettingsGroup
import com.faheem.habits.core.ui.SettingsRow
import com.faheem.habits.core.ui.StrengthMeter
import com.faheem.habits.core.ui.passwordStrength
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(onContinueWithoutAccount: () -> Unit, passwordResetRequestToken: Long? = null) {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var eventMessage by remember { mutableStateOf<String?>(null) }
    var recoveryToken by remember(context, passwordResetRequestToken) { mutableStateOf(recoveryTokenFromIntent(context)) }
    var showSignOut by remember { mutableStateOf(false) }
    var showDeleteFirst by remember { mutableStateOf(false) }
    var showDeleteFinal by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ProfileUiEvent.SignedIn -> eventMessage = "You are signed in."
                ProfileUiEvent.SignedOut -> eventMessage = "Signed out. Your local records remain on this device."
                is ProfileUiEvent.Message -> eventMessage = event.text
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            (state as? ProfileUiState.SignedIn)?.let { viewModel.updateProfile(it.profile.copy(avatarUri = uri.toString())) }
        }
    }

    when (val current = state) {
        ProfileUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is ProfileUiState.SignedOut -> SignedOutProfile(
            state = current,
            message = eventMessage,
            onEmail = { viewModel.updateAuth(email = it) },
            onPassword = { viewModel.updateAuth(password = it) },
            onName = { viewModel.updateAuth(name = it) },
            onToggleCreate = { viewModel.updateAuth(createAccount = !current.createAccount) },
            onSubmit = viewModel::authenticate,
            onReset = viewModel::resetPassword,
            onGoogle = { viewModel.signInWithGoogle(context) },
            onContinue = onContinueWithoutAccount
        )
        is ProfileUiState.SignedIn -> SignedInProfile(
            state = current,
            message = eventMessage,
            onEdit = viewModel::editProfile,
            onChange = viewModel::updateProfile,
            onPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onSave = viewModel::saveProfile,
            onCancel = viewModel::cancelEdit,
            onSignOut = { showSignOut = true },
            onChangePassword = { showPasswordDialog = true },
            onDelete = { showDeleteFirst = true }
        )
    }

    if (showSignOut) AlertDialog(
        onDismissRequest = { showSignOut = false },
        title = { Text("Sign out?") },
        text = { Text("Your local records stay on this device. Cloud sync pauses until you sign in again.") },
        confirmButton = { TextButton(onClick = { showSignOut = false; viewModel.signOut() }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { showSignOut = false }) { Text("Cancel") } }
    )
    if (showDeleteFirst) AlertDialog(
        onDismissRequest = { showDeleteFirst = false },
        title = { Text("Delete your account?") },
        text = { Text("This is permanent. It removes your cloud account and cloud records if the server supports deletion. Your local records remain on this device.") },
        confirmButton = { TextButton(onClick = { showDeleteFirst = false; showDeleteFinal = true }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { showDeleteFirst = false }) { Text("Cancel") } }
    )
    if (showDeleteFinal) AlertDialog(
        onDismissRequest = { showDeleteFinal = false },
        title = { Text("Confirm account deletion") },
        text = { Text("The server must have a delete_my_account RPC installed. Without it, nothing will be deleted.") },
        confirmButton = { TextButton(onClick = { showDeleteFinal = false; viewModel.requestDeleteAccount() }) { Text("Delete account") } },
        dismissButton = { TextButton(onClick = { showDeleteFinal = false }) { Text("Keep account") } }
    )
    if (showPasswordDialog) {
        val signedIn = state as? ProfileUiState.SignedIn
        if (signedIn != null) ChangePasswordDialog(signedIn, onDismiss = { showPasswordDialog = false }, onSubmit = { old, next, confirm ->
            showPasswordDialog = false
            viewModel.changePassword(old, next, confirm)
        })
    }
    recoveryToken?.let { token ->
        PasswordRecoveryDialog(
            onDismiss = { recoveryToken = null },
            onSubmit = { next, confirm ->
                viewModel.completePasswordRecovery(token, next, confirm)
                recoveryToken = null
            }
        )
    }
}

private fun recoveryTokenFromIntent(context: Context): String? {
    var current = context
    while (current is ContextWrapper) {
        if (current is android.app.Activity) {
            val fragment = current.intent?.data?.fragment ?: return null
            return fragment.split('&').firstNotNullOfOrNull { part ->
                val keyValue = part.split('=', limit = 2)
                keyValue.takeIf { it.size == 2 && it[0] == "access_token" }?.get(1)?.let { android.net.Uri.decode(it) }
            }
        }
        current = current.baseContext
    }
    return null
}

@Composable
private fun SignedOutProfile(
    state: ProfileUiState.SignedOut,
    message: String?,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onName: (String) -> Unit,
    onToggleCreate: () -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
    onGoogle: () -> Unit,
    onContinue: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Text("Your profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Sign in to back up and sync. You can use Zylo offline without an account.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (state.createAccount) MomentumTextField(state.name, onName, label = { Text("Name") }, leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            MomentumTextField(state.email, onEmail, label = { Text("Email") }, leadingIcon = { Icon(Icons.Rounded.Email, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
            Spacer(Modifier.height(8.dp))
            PasswordField(state.password, onPassword, label = "Password")
            if (state.createAccount && state.password.isNotEmpty()) {
                Spacer(Modifier.height(6.dp)); StrengthMeter(state.password)
                Text("Password strength ${passwordStrength(state.password)} of 4", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(if (state.createAccount) "Create account" else "Sign in", onSubmit, enabled = !state.busy, loading = state.busy, icon = Icons.Rounded.Lock)
            if (!state.createAccount) TextButton(onClick = onReset, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Forgot password?") }
            TextButton(onClick = onToggleCreate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.createAccount) "Already have an account? Sign in" else "Create an account") }
            if (state.googleAvailable) TextButton(onClick = onGoogle, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
            if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            (state.notice ?: message)?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        }
        TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue without an account") }
    }
}

@Composable
private fun SignedInProfile(
    state: ProfileUiState.SignedIn,
    message: String?,
    onEdit: () -> Unit,
    onChange: (ProfileCache) -> Unit,
    onPhoto: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onSignOut: () -> Unit,
    onChangePassword: () -> Unit,
    onDelete: () -> Unit
) {
    val profile = state.profile
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(profile, 72.dp)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName.ifBlank { "Your profile" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(profile.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!state.editing) TextButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, null); Text("Edit") }
            }
            if (state.editing) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onPhoto) { Icon(Icons.Rounded.PhotoCamera, null); Text("Choose profile photo") }
                MomentumTextField(profile.displayName, { onChange(profile.copy(displayName = it.take(80))) }, label = { Text("Display name") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                MomentumTextField(profile.phone, { onChange(profile.copy(phone = it.take(30))) }, label = { Text("Phone (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                Spacer(Modifier.height(8.dp))
                MomentumTextField(profile.bio, { onChange(profile.copy(bio = it.take(240))) }, label = { Text("Short bio (optional)") }, minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCancel, enabled = !state.busy) { Text("Cancel") }
                    PrimaryButton("Save profile", onSave, enabled = !state.busy, loading = state.busy)
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(profile.bio.ifBlank { "Add a short bio to personalize your profile." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Member since ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(profile.memberSince))}", style = MaterialTheme.typography.labelMedium)
                if (state.pendingChanges) Text("Some details are saved only on this device until profile sync is configured.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
            }
        }
        SettingsGroup("Account security") {
            SettingsRow(if (state.hasPassword) "Change password" else "Set a password", if (state.hasPassword) "Verify your current password first" else "Add email and password sign-in", Icons.Rounded.Lock, onClick = onChangePassword)
            SettingsRow("Sign out", "Keep all records stored on this device", Icons.Rounded.Logout, onClick = onSignOut)
            SettingsRow("Delete account", "Requires the optional server-side delete function", Icons.Rounded.Lock, onClick = onDelete)
        }
        (state.error ?: state.notice ?: message)?.let { GlassCard { Text(it, color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }
    }
}

@Composable
private fun ProfileAvatar(profile: ProfileCache, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, profile.avatarUri) {
        value = if (profile.avatarUri.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(android.net.Uri.parse(profile.avatarUri))?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        }
    }
    if (image != null) Image(image!!, "Profile photo", Modifier.size(size).clip(CircleShape))
    else Box(Modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        Text(profile.displayName.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("").ifBlank { "?" }, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun ChangePasswordDialog(state: ProfileUiState.SignedIn, onDismiss: () -> Unit, onSubmit: (String, String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (state.hasPassword) "Change password" else "Set a password") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.hasPassword) PasswordField(current, { current = it }, label = "Current password")
            PasswordField(next, { next = it }, label = "New password")
            if (next.isNotEmpty()) StrengthMeter(next)
            PasswordField(confirmation, { confirmation = it }, label = "Confirm new password")
            if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { onSubmit(current, next, confirmation) }, enabled = !state.busy) { Text("Save password") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun PasswordRecoveryDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(next, { next = it }, label = "New password")
                if (next.isNotEmpty()) StrengthMeter(next)
                PasswordField(confirm, { confirm = it }, label = "Confirm password")
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(next, confirm) }) { Text("Save password") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
