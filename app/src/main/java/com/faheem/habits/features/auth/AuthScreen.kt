package com.faheem.habits.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faheem.habits.core.data.supabase.SupabaseAuthApi
import com.faheem.habits.R
import com.faheem.habits.core.data.supabase.SupabaseProvider
import com.faheem.habits.core.ui.BrandGradient
import com.faheem.habits.core.ui.InfoBanner
import com.faheem.habits.core.ui.MomentumTextField
import com.faheem.habits.core.ui.MomentumTokens
import com.faheem.habits.core.ui.PremiumCard
import com.faheem.habits.core.ui.PrimaryButton
import com.faheem.habits.core.ui.ScreenBackground
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(initialMessage: String? = null, onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val isConfigured = remember { SupabaseProvider.isConfigured }
    val scope = rememberCoroutineScope()
    var isSignUp by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(initialMessage) }
    var isError by remember { mutableStateOf(initialMessage != null) }

    ScreenBackground(tint = BrandGradient.first()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MomentumTokens.screenPadding, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .shadow(
                        26.dp,
                        RoundedCornerShape(24.dp),
                        clip = false,
                        ambientColor = BrandGradient.first().copy(alpha = 0.65f),
                        spotColor = BrandGradient.last().copy(alpha = 0.55f)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.zylo_logo),
                    contentDescription = "Zylo logo",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Zylo",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.9).sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (isSignUp) "Create your account to get started" else "Welcome back. Sign in to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            if (!isConfigured) {
                InfoBanner(
                    icon = Icons.Rounded.Lock,
                    text = "Supabase is not configured. Check SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in your local Gradle properties.",
                    tint = MaterialTheme.colorScheme.error
                )
            } else {
                PremiumCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                    Text(
                        if (isSignUp) "New account" else "Sign in",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    if (isSignUp) {
                        MomentumTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(80); message = null },
                            label = { Text("Your name") },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                            singleLine = true,
                            enabled = !busy
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    MomentumTextField(
                        value = email,
                        onValueChange = { email = it; message = null },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Rounded.Email, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !busy
                    )
                    Spacer(Modifier.height(10.dp))
                    MomentumTextField(
                        value = password,
                        onValueChange = { password = it; message = null },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !busy
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        enabled = !busy
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = if (isSignUp) "Create account" else "Sign in",
                        onClick = {
                            if (!isAuthInputValid(email, password, isSignUp, displayName)) {
                                isError = true
                                message = if (isSignUp) "Enter your name, a valid email address, and a password with at least 6 characters."
                                else "Enter a valid email address and a password with at least 6 characters."
                            } else {
                                val enteredEmail = email.trim()
                                val enteredPassword = password
                                scope.launch {
                                    busy = true
                                    message = null
                                    try {
                                        if (isSignUp) {
                                            SupabaseAuthApi.signUp(enteredEmail, enteredPassword, displayName.trim().take(80))
                                            isError = false
                                            message = "Account created. Check your email to confirm it, then sign in."
                                            isSignUp = false
                                            passwordVisible = false
                                        } else {
                                            SupabaseAuthApi.signIn(context.applicationContext, enteredEmail, enteredPassword)
                                            onAuthenticated()
                                        }
                                    } catch (e: Exception) {
                                        isError = true
                                        message = userFacingAuthError(e)
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        loading = busy,
                        icon = Icons.AutoMirrored.Rounded.ArrowForward
                    )
                    if (!isSignUp) {
                        TextButton(
                            onClick = {
                                val resetEmail = email.trim()
                                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(resetEmail).matches()) {
                                    isError = true
                                    message = "Enter a valid email address first."
                                } else scope.launch {
                                    busy = true
                                    message = null
                                    try {
                                        SupabaseAuthApi.sendPasswordReset(resetEmail)
                                        isError = false
                                        message = "If an account exists for that email, a password reset link has been sent."
                                    } catch (e: Exception) {
                                        isError = true
                                        message = userFacingAuthError(e)
                                    } finally { busy = false }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Forgot password? Email me a reset link") }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            isSignUp = !isSignUp
                            passwordVisible = false
                            message = null
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSignUp) "Already have an account? Sign in" else "New to Zylo? Create account")
                    }
                    message?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(MomentumTokens.space4))
            Text(
                "Your records stay private and under your control.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}


private fun isAuthInputValid(
    email: String,
    password: String,
    isSignUp: Boolean,
    displayName: String
): Boolean {
    val hasValidEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val hasValidPassword = password.length >= 6
    val hasRequiredName = !isSignUp || displayName.isNotBlank()
    return hasValidEmail && hasValidPassword && hasRequiredName
}
