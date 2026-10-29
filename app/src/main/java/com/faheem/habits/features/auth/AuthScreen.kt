package com.faheem.habits.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.KeyboardType
import androidx.compose.ui.unit.dp
import com.faheem.habits.core.data.supabase.SupabaseProvider
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    val client = remember { SupabaseProvider.createClientOrNull() }
    val scope = rememberCoroutineScope()
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Momentum", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (isSignUp) "Create your account to get started" else "Sign in to continue",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(28.dp))

        if (client == null) {
            Text(
                "Supabase is not configured. Check SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in your local Gradle properties.",
                color = MaterialTheme.colorScheme.error
            )
        } else {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; message = null },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; message = null },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (email.isBlank() || password.length < 6) {
                        isError = true
                        message = "Enter a valid email and a password with at least 6 characters."
                    } else {
                        scope.launch {
                            busy = true
                            message = null
                            try {
                                if (isSignUp) {
                                    client.gotrue.signUpWith(Email) {
                                        this.email = email.trim()
                                        this.password = password
                                    }
                                    isError = false
                                    message = "Account created. Check your email to confirm it, then sign in."
                                    isSignUp = false
                                } else {
                                    client.gotrue.loginWith(Email) {
                                        this.email = email.trim()
                                        this.password = password
                                    }
                                    onAuthenticated()
                                }
                            } catch (e: Exception) {
                                isError = true
                                message = e.message ?: "Authentication failed. Please try again."
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (isSignUp) "Create account" else "Sign in")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    isSignUp = !isSignUp
                    message = null
                },
                enabled = !busy
            ) {
                Text(if (isSignUp) "Already have an account? Sign in" else "New to Momentum? Create account")
            }
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
