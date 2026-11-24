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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.HorizontalDivider
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
import com.faheem.habits.core.ui.PremiumCard
import com.faheem.habits.core.ui.SecondaryButton
import com.faheem.habits.core.ui.StatusPill
import com.faheem.habits.core.ui.SettingsGroup
import com.faheem.habits.core.ui.SettingsRow
import com.faheem.habits.core.ui.StrengthMeter
import com.faheem.habits.core.ui.passwordStrength
import java.text.DateFormat
import java.net.URL
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
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PremiumCard(
            accent = MaterialTheme.colorScheme.primary,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileModeChip(
                    selected = !state.createAccount,
                    label = "Sign in",
                    modifier = Modifier.weight(1f),
                    onClick = { if (state.createAccount) onToggleCreate() }
                )
                ProfileModeChip(
                    selected = state.createAccount,
                    label = "Create account",
                    modifier = Modifier.weight(1f),
                    onClick = { if (!state.createAccount) onToggleCreate() }
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (state.createAccount) "Build your Zylo profile" else "Welcome back",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.createAccount)
                    "Create an account to sync your profile and protected data."
                else
                    "Sign in to keep everything connected across devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (state.createAccount) {
                MomentumTextField(
                    state.name,
                    onName,
                    label = { Text("Display name") },
                    leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) },
                    singleLine = true,
                    enabled = !state.busy
                )
                Spacer(Modifier.height(10.dp))
            }
            MomentumTextField(
                state.email,
                onEmail,
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Rounded.Email, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !state.busy
            )
            Spacer(Modifier.height(10.dp))
            PasswordField(
                state.password,
                onPassword,
                label = "Password",
                enabled = !state.busy
            )
            if (state.createAccount && state.password.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                StrengthMeter(state.password)
                Text(
                    "Password strength ${passwordStrength(state.password)} of 4",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                if (state.createAccount) "Create account" else "Sign in",
                onSubmit,
                enabled = !state.busy,
                loading = state.busy,
                icon = Icons.Rounded.Lock
            )
            if (!state.createAccount) {
                TextButton(
                    onClick = onReset,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Forgot password?") }
            }
            if (state.googleAvailable) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text(
                        "  or  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    "Continue with Google",
                    onGoogle,
                    icon = Icons.Rounded.AccountCircle
                )
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            (state.notice ?: message)?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
        PremiumCard(
            onClick = onContinue,
            accent = MaterialTheme.colorScheme.secondary,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Use Zylo offline", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Local data stays on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
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
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PremiumCard(
            accent = if (state.pendingChanges) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    ProfileAvatar(profile, 96.dp)
                    if (state.editing) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                .clickable(onClick = onPhoto),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.PhotoCamera,
                                contentDescription = "Change profile photo",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    StatusPill(
                        if (state.pendingChanges) "Saved locally" else "Synced",
                        if (state.pendingChanges) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        profile.displayName.ifBlank { "Your profile" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    if (profile.email.isNotBlank()) {
                        Text(
                            profile.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (state.editing) {
                Spacer(Modifier.height(16.dp))
                MomentumTextField(
                    profile.displayName,
                    { onChange(profile.copy(displayName = it.take(80))) },
                    label = { Text("Display name") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    singleLine = true,
                    enabled = !state.busy
                )
                Spacer(Modifier.height(8.dp))
                MomentumTextField(
                    profile.phone,
                    { onChange(profile.copy(phone = it.take(30))) },
                    label = { Text("Phone (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    enabled = !state.busy
                )
                Spacer(Modifier.height(8.dp))
                MomentumTextField(
                    profile.bio,
                    { onChange(profile.copy(bio = it.take(240))) },
                    label = { Text("Short bio (optional)") },
                    minLines = 3,
                    enabled = !state.busy
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = onCancel,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }
                    PrimaryButton(
                        "Save profile",
                        onSave,
                        enabled = !state.busy,
                        loading = state.busy,
                        icon = Icons.Rounded.Save,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileInfoChip(
                            icon = Icons.Rounded.Badge,
                            label = "Member",
                            value = if (profile.memberSince > 0L)
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(profile.memberSince))
                            else "Recently",
                            modifier = Modifier.weight(1f)
                        )
                        ProfileInfoChip(
                            icon = if (state.pendingChanges) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone,
                            label = "Cloud",
                            value = if (state.pendingChanges) "Pending" else "Synced",
                            modifier = Modifier.weight(1f)
                        )
                }
            }
        }

        if (!state.editing) {
            PremiumCard(
                    accent = MaterialTheme.colorScheme.secondary,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                    Text(
                        "About",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        profile.bio.ifBlank { "Add a short bio to tell your story." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (profile.bio.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (profile.phone.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(profile.phone, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
            }
        }

        Text(
            "Account",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
        PremiumCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            ProfileActionRow(
                    icon = Icons.Rounded.Shield,
                    title = if (state.hasPassword) "Change password" else "Set a password",
                    subtitle = if (state.hasPassword) "Secure your account" else "Enable email sign-in",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onChangePassword
            )
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ProfileActionRow(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    title = "Sign out",
                    subtitle = "Keep local records on this device",
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = onSignOut
            )
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ProfileActionRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Delete account",
                    subtitle = "Permanently remove cloud account",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
            )
        }

        (state.error ?: state.notice ?: message)?.let {
            PremiumCard(
                    accent = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
            ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (state.error != null) Icons.Rounded.Info else Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
            }
        }
    }
}

@Composable
private fun ProfileModeChip(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outlineVariant,
                shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileInfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProfileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun ProfileAvatar(profile: ProfileCache, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, profile.avatarUri) {
        value = if (profile.avatarUri.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val uri = android.net.Uri.parse(profile.avatarUri)
                val bitmap = when (uri.scheme?.lowercase()) {
                    "https" -> URL(profile.avatarUri).openConnection().apply {
                        connectTimeout = 5_000
                        readTimeout = 5_000
                    }.getInputStream().use { BitmapFactory.decodeStream(it) }
                    "content", "file" -> context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    else -> null
                }
                bitmap?.asImageBitmap()
            }.getOrNull()
        }
    }
    val avatarModifier = Modifier
        .size(size)
        .clip(CircleShape)
        .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f), CircleShape)
        .padding(3.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)

    if (image != null) {
        Image(
            image!!,
            "Profile photo",
            avatarModifier
        )
    } else {
        Box(avatarModifier, contentAlignment = Alignment.Center) {
            Text(
                profile.displayName
                    .split(' ')
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .take(2)
                    .joinToString("")
                    .ifBlank { "?" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
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
