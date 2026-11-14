package com.faheem.habits.features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
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
import com.faheem.habits.R
import com.faheem.habits.core.data.supabase.SupabaseAuthApi
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
                .padding(horizontal = MomentumTokens.screenPadding, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AuthBrandHeader(isSignUp = isSignUp)
            Spacer(Modifier.height(28.dp))

            if (!isConfigured) {
                InfoBanner(
                    icon = Icons.Rounded.Lock,
                    text = "Supabase is not configured. Check SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in your local Gradle properties.",
                    tint = MaterialTheme.colorScheme.error
                )
            } else {
                PremiumCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isSignUp) "Create account" else "Welcome back",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (isSignUp) "A few details and your space is ready." else "Sign in to continue to your space.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(BrandGradient.first().copy(alpha = 0.13f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    if (isSignUp) {
                        MomentumTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(80); message = null },
                            label = { Text("Full name") },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                            singleLine = true,
                            enabled = !busy
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    MomentumTextField(
                        value = email,
                        onValueChange = { email = it; message = null },
                        label = { Text("Email address") },
                        leadingIcon = { Icon(Icons.Rounded.Email, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !busy
                    )
                    Spacer(Modifier.height(14.dp))
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
                    Spacer(Modifier.height(8.dp))

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
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Forgot password?")
                        }
                    } else {
                        Text(
                            "Use at least 6 characters for your password.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = if (isSignUp) "Create account" else "Sign in",
                        onClick = {
                            if (!isAuthInputValid(email, password, isSignUp, displayName)) {
                                isError = true
                                message = if (isSignUp) {
                                    "Enter your name, a valid email address, and a password with at least 6 characters."
                                } else {
                                    "Enter a valid email address and a password with at least 6 characters."
                                }
                            } else {
                                val enteredEmail = email.trim()
                                val enteredPassword = password
                                scope.launch {
                                    busy = true
                                    message = null
                                    try {
                                        if (isSignUp) {
                                            SupabaseAuthApi.signUp(
                                                enteredEmail,
                                                enteredPassword,
                                                displayName.trim().take(80)
                                            )
                                            isError = false
                                            message = "Account created. Check your email to confirm it, then sign in."
                                            isSignUp = false
                                            password = ""
                                            passwordVisible = false
                                        } else {
                                            SupabaseAuthApi.signIn(
                                                context.applicationContext,
                                                enteredEmail,
                                                enteredPassword
                                            )
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

                    message?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = it,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    (if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                        .copy(alpha = 0.09f)
                                )
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isSignUp) "Already have an account?" else "New to Zylo?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                isSignUp = !isSignUp
                                passwordVisible = false
                                message = null
                            },
                            enabled = !busy
                        ) {
                            Text(
                                if (isSignUp) "Sign in" else "Create account",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Your records stay private and under your control.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AuthBrandHeader(isSignUp: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .size(72.dp)
                .shadow(
                    24.dp,
                    RoundedCornerShape(22.dp),
                    clip = false,
                    ambientColor = BrandGradient.first().copy(alpha = 0.50f),
                    spotColor = BrandGradient.last().copy(alpha = 0.40f)
                )
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.zylo_logo),
                contentDescription = "Zylo logo",
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Zylo",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(5.dp))
        Text(
            if (isSignUp) "Build a calmer, more organized day." else "Your day, organized in one place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
