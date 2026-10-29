package com.faheem.habits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MomentumTheme { MomentumApp() }
        }
    }
}

@Composable
fun MomentumApp() {
    var current by remember { mutableStateOf<AppModule?>(null) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        AnimatedContent(
            targetState = current,
            modifier = Modifier.padding(padding),
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(initialOffsetX = { it / 3 }) + fadeIn()) togetherWith
                        (slideOutHorizontally(targetOffsetX = { -it / 6 }) + fadeOut())
                } else {
                    (slideInHorizontally(initialOffsetX = { -it / 6 }) + fadeIn()) togetherWith
                        (slideOutHorizontally(targetOffsetX = { it / 3 }) + fadeOut())
                }
            },
            label = "nav"
        ) { m ->
            if (m == null) HomeScreen(onOpen = { current = it })
            else ModuleHost(m, onBack = { current = null })
        }
    }
}

@Composable
fun ModuleHost(module: AppModule, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val accent = module.gradient[0]

    CompositionLocalProvider(LocalGradient provides module.gradient) {
        // Re-colour the whole module with its own accent
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = accent,
                onPrimary = Color.White,
                primaryContainer = accent.copy(alpha = 0.18f),
                onPrimaryContainer = accent,
                secondaryContainer = accent.copy(alpha = 0.18f),
                onSecondaryContainer = accent
            )
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                    IconBubble(module.icon, accent, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(module.label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f)) {
                    when (module) {
                        AppModule.Habits -> HabitScreen()
                        AppModule.Money -> ExpensesScreen()
                        AppModule.Loans -> LoansScreen()
                        AppModule.Files -> FilesScreen()
                        AppModule.Screen -> ScreenTimeScreen()
                    }
                }
            }
        }
    }
}
