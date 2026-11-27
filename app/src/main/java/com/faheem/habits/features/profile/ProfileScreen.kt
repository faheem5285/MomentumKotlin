@file:Suppress("FunctionNaming", "LongMethod")

package com.faheem.habits.features.profile

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import com.faheem.habits.R
import com.faheem.habits.core.data.ProfileCache
import com.faheem.habits.core.ui.GlassSheet
import com.faheem.habits.core.ui.MomentumTextField
import com.faheem.habits.core.ui.PremiumCard
import com.faheem.habits.core.ui.PrimaryButton
import com.faheem.habits.core.ui.ScreenBackground
import com.faheem.habits.core.ui.SecondaryButton
import com.faheem.habits.core.ui.StatusPill
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    initialCreateAccount: Boolean = false,
    onAccountChanged: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    onMessage: (String) -> Unit = {},
    onContinueWithoutAccount: () -> Unit = {},
    passwordResetRequestToken: Long? = null
) {
    val context = LocalContext.current
    val vm: ProfileViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf(false) }
    var recoveryToken by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(passwordResetRequestToken) {
        when (val result = consumeRecoveryLink(context)) {
            is RecoveryLinkResult.Valid -> recoveryToken = result.accessToken
            is RecoveryLinkResult.Invalid -> {
                message = result.message
                onMessage(result.message)
            }
            RecoveryLinkResult.Absent -> Unit
        }
    }

    LaunchedEffect(initialCreateAccount, state) {
        if (initialCreateAccount) {
            val signedOut = state as? ProfileUiState.SignedOut
            if (signedOut != null && !signedOut.createAccount) vm.updateAuth(createAccount = true)
        }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ProfileUiEvent.Message -> {
                    message = event.text
                    onMessage(event.text)
                }
                ProfileUiEvent.SignedIn -> onAccountChanged()
                ProfileUiEvent.SignedOut -> onSignedOut()
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            (state as? ProfileUiState.SignedIn)?.let { vm.updateProfile(it.profile.copy(avatarUri = uri.toString())) }
        }
    }

    ScreenBackground {
        when (val s = state) {
            ProfileUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is ProfileUiState.SignedOut -> SignedOutProfile(
                state = s,
                message = message,
                onEmail = { vm.updateAuth(email = it) },
                onPassword = { vm.updateAuth(password = it) },
                onName = { vm.updateAuth(name = it) },
                onToggleCreate = { vm.updateAuth(createAccount = !s.createAccount) },
                onSubmit = vm::authenticate,
                onReset = vm::resetPassword,
                onResendConfirmation = vm::resendConfirmation,
                onGoogle = { vm.signInWithGoogle(context) },
                onConfirmLink = vm::confirmAccountLink,
                onCancelLink = vm::cancelAccountLink,
                onAccountChanged = onAccountChanged
            )

            is ProfileUiState.SignedIn -> SignedInProfile(
                state = s,
                message = message,
                onEdit = vm::editProfile,
                onCancel = vm::cancelEdit,
                onSave = vm::saveProfile,
                onChange = vm::updateProfile,
                onPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onChangePassword = { passwordDialog = true },
                onSignOut = { vm.signOut(); onAccountChanged() },
                onDelete = { deleteConfirm = true }
            )
        }
    }

    if (deleteConfirm) {
        DeleteAccountDialog(
            onDismiss = { deleteConfirm = false },
            onConfirm = {
                deleteConfirm = false
                vm.requestDeleteAccount()
            }
        )
    }

    if (passwordDialog) {
        val signedIn = state as? ProfileUiState.SignedIn
        if (signedIn != null) {
            ChangePasswordDialog(
                hasPassword = signedIn.hasPassword,
                onDismiss = { passwordDialog = false },
                onConfirm = { current, next, confirm ->
                    passwordDialog = false
                    vm.changePassword(current, next, confirm)
                }
            )
        }
    }

    recoveryToken?.let { token ->
        PasswordRecoveryDialog(
            onDismiss = { recoveryToken = null },
            onConfirm = { next, confirm ->
                vm.completePasswordRecovery(token, next, confirm)
                recoveryToken = null
            }
        )
    }
}

/** Reads and clears only the deep-link data; notification extras stay intact. */
private fun consumeRecoveryLink(context: Context): RecoveryLinkResult {
    var current = context
    while (current is ContextWrapper) {
        if (current is android.app.Activity) {
            val intent = current.intent ?: return RecoveryLinkResult.Absent
            val link = intent.data?.toString() ?: return RecoveryLinkResult.Absent
            val result = RecoveryLinkParser.parse(link, System.currentTimeMillis())
            current.intent = Intent(intent).apply { data = null }
            return result
        }
        current = current.baseContext
    }
    return RecoveryLinkResult.Absent
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
    onResendConfirmation: () -> Unit,
    onGoogle: () -> Unit,
    onConfirmLink: () -> Unit,
    onCancelLink: () -> Unit,
    onAccountChanged: () -> Unit
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val title = if (state.createAccount) "Create your account" else "Welcome back"
    val subtitle = if (state.createAccount) {
        "Sync your habits, tasks, and progress across devices."
    } else {
        "Sign in to access your profile and synced data."
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AuthHero(title = title, subtitle = subtitle, createAccount = state.createAccount)

        PremiumCard(contentPadding = PaddingValues(20.dp)) {
            AuthModeToggle(createAccount = state.createAccount, onToggle = onToggleCreate)
            Spacer(Modifier.height(18.dp))

            if (state.createAccount) {
                MomentumTextField(
                    value = state.name,
                    onValueChange = onName,
                    label = { Text("Display name") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }

            MomentumTextField(
                value = state.email,
                onValueChange = onEmail,
                label = { Text("Email address") },
                leadingIcon = { Icon(Icons.Rounded.AlternateEmail, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            MomentumTextField(
                value = state.password,
                onValueChange = onPassword,
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )

            if (!state.createAccount) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onReset, enabled = !state.busy) { Text("Forgot password?") }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }

            PrimaryButton(
                text = if (state.createAccount) "Create account" else "Sign in",
                onClick = { onSubmit(); onAccountChanged() },
                enabled = !state.busy,
                loading = state.busy,
                icon = if (state.createAccount) Icons.Rounded.PersonAdd else Icons.AutoMirrored.Rounded.Login
            )

            if (state.googleAvailable) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "Continue with Google",
                    onClick = onGoogle,
                    icon = Icons.Rounded.AccountCircle
                )
            }
        }

        AuthFeedbackCard(
            error = state.error ?: message,
            notice = state.notice,
            canResendConfirmation = state.canResendConfirmation,
            busy = state.busy,
            onResendConfirmation = onResendConfirmation
        )

        state.pendingLinkMessage?.let { notice ->
            PremiumCard(accent = MaterialTheme.colorScheme.secondary, contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Text(notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Cancel", onCancelLink, modifier = Modifier.weight(1f))
                        PrimaryButton("Continue", onConfirmLink, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Text(
            "You can use Zylo without an account. Signing in only enables sync and backup.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun AuthHero(title: String, subtitle: String, createAccount: Boolean) {
    val accent = if (createAccount) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    PremiumCard(accent = accent, contentPadding = PaddingValues(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0.06f))))
                    .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.zylo_launcher),
                    contentDescription = "Zylo",
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AuthModeToggle(createAccount: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AuthModeChip("Sign in", !createAccount, Modifier.weight(1f), onClick = { if (createAccount) onToggle() })
        AuthModeChip("Create account", createAccount, Modifier.weight(1f), onClick = { if (!createAccount) onToggle() })
    }
}

@Composable
private fun AuthModeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AuthFeedbackCard(
    error: String?,
    notice: String?,
    canResendConfirmation: Boolean,
    busy: Boolean,
    onResendConfirmation: () -> Unit
) {
    val text = error ?: notice ?: return
    val isError = error != null
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    PremiumCard(accent = accent, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (isError) Icons.Rounded.Info else Icons.Rounded.MarkEmailRead,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(top = 1.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (canResendConfirmation) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onResendConfirmation, enabled = !busy, contentPadding = PaddingValues(0.dp)) {
                        Text("Resend confirmation email")
                    }
                }
            }
        }
    }
}

@Composable
private fun SignedInProfile(
    state: ProfileUiState.SignedIn,
    message: String?,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onChange: (ProfileCache) -> Unit,
    onPhoto: () -> Unit,
    onChangePassword: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit
) {
    val profile = state.profile

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PremiumCard(contentPadding = PaddingValues(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(84.dp)) {
                    ProfileAvatar(profile = profile, editing = state.editing, onPhoto = onPhoto)
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable(onClick = onPhoto),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, "Change photo", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    StatusPill(
                        text = if (state.pendingChanges) "Saved locally" else "Synced",
                        color = if (state.pendingChanges) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        icon = if (state.pendingChanges) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        profile.displayName.ifBlank { "Your profile" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (profile.email.isNotBlank()) {
                        Text(
                            profile.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!state.editing) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit profile")
                    }
                }
            }

            if (state.editing) {
                Spacer(Modifier.height(20.dp))
                MomentumTextField(
                    value = profile.displayName,
                    onValueChange = { onChange(profile.copy(displayName = it.take(80))) },
                    label = { Text("Display name") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                MomentumTextField(
                    value = profile.phone,
                    onValueChange = { onChange(profile.copy(phone = it.take(30))) },
                    label = { Text("Phone (optional)") },
                    leadingIcon = { Icon(Icons.Rounded.Phone, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                MomentumTextField(
                    value = profile.bio,
                    onValueChange = { onChange(profile.copy(bio = it.take(240))) },
                    label = { Text("Bio (optional)") },
                    minLines = 3,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Cancel", onCancel, modifier = Modifier.weight(1f))
                    PrimaryButton(
                        "Save",
                        onSave,
                        enabled = !state.busy,
                        loading = state.busy,
                        icon = Icons.Rounded.Save,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileInfoChip(
                        icon = Icons.Rounded.Badge,
                        label = "Member since",
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
            PremiumCard(contentPadding = PaddingValues(20.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    profile.bio.ifBlank { "Add a short bio to personalize your profile." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (profile.bio.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (profile.phone.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Phone, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(profile.phone, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        PremiumCard(contentPadding = PaddingValues(8.dp)) {
            Text(
                "Account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
            ProfileActionRow(
                icon = Icons.Rounded.Shield,
                title = if (state.hasPassword) "Change password" else "Set a password",
                subtitle = if (state.hasPassword) "Update your sign-in password" else "Add a password for email sign-in",
                tint = MaterialTheme.colorScheme.primary,
                onClick = onChangePassword
            )
            HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ProfileActionRow(
                icon = Icons.AutoMirrored.Rounded.Logout,
                title = "Sign out",
                subtitle = "Keep your local data on this device",
                tint = MaterialTheme.colorScheme.secondary,
                onClick = onSignOut
            )
            HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ProfileActionRow(
                icon = Icons.Rounded.DeleteForever,
                title = "Delete account",
                subtitle = "Permanently remove your cloud account",
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
        }

        ProfileFeedback(message = state.error ?: state.notice ?: message, isError = state.error != null)
    }
}

@Composable
private fun ProfileAvatar(profile: ProfileCache, editing: Boolean, onPhoto: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = profile.avatarUri) {
        value = if (profile.avatarUri.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val uri = android.net.Uri.parse(profile.avatarUri)
                val bitmap = when (uri.scheme?.lowercase()) {
                    "https", "http" -> URL(profile.avatarUri).openConnection().apply {
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

    val shape = CircleShape
    Box(
        Modifier
            .size(84.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onPhoto),
        contentAlignment = Alignment.Center
    ) {
        val initials = profile.displayName.trim().take(1).ifBlank { "?" }.uppercase()
        if (image != null) {
            Image(
                bitmap = image!!,
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                initials,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ProfileInfoChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 0.dp
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileFeedback(message: String?, isError: Boolean) {
    if (message == null) return
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    PremiumCard(accent = accent, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isError) Icons.Rounded.Info else Icons.Rounded.CloudDone,
                contentDescription = null,
                tint = accent
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) accent else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete account?") },
        text = {
            Text(
                "This permanently removes your cloud account and synced data. Local data on this device may remain until you remove it."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ChangePasswordDialog(
    hasPassword: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    val visual = if (show) VisualTransformation.None else PasswordVisualTransformation()

    GlassSheet(if (hasPassword) "Change password" else "Set a password", onDismiss) {
        if (hasPassword) {
            MomentumTextField(
                value = current,
                onValueChange = { current = it },
                label = { Text("Current password") },
                visualTransformation = visual,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }
        MomentumTextField(
            value = next,
            onValueChange = { next = it },
            label = { Text("New password") },
            visualTransformation = visual,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        MomentumTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm new password") },
            visualTransformation = visual,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { show = !show }) {
            Text(if (show) "Hide passwords" else "Show passwords")
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = if (hasPassword) "Update password" else "Set password",
            onClick = { onConfirm(current, next, confirm) },
            icon = Icons.Rounded.Key
        )
    }
}

@Composable
private fun PasswordRecoveryDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var next by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    val visual = if (show) VisualTransformation.None else PasswordVisualTransformation()

    GlassSheet("Reset password", onDismiss) {
        Text(
            "Choose a new password for your account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        MomentumTextField(
            value = next,
            onValueChange = { next = it },
            label = { Text("New password") },
            visualTransformation = visual,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        MomentumTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm new password") },
            visualTransformation = visual,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { show = !show }) {
            Text(if (show) "Hide passwords" else "Show passwords")
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Reset password", onClick = { onConfirm(next, confirm) }, icon = Icons.Rounded.Key)
    }
}
