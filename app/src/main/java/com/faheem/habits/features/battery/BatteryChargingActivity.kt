package com.faheem.habits.features.battery
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class BatteryChargingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        setContent { MomentumTheme { BatteryChargingAnimationScreen(preview, onClose = { finish() }) } }
    }

    companion object { const val EXTRA_PREVIEW = "preview_animation" }
}

@Composable
private fun BatteryChargingAnimationScreen(preview: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    var reading by remember { mutableStateOf(BatteryReading(0, false, "Charging")) }
    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        fun update(intent: Intent?) { reading = batteryReading(intent) }
        update(context.registerReceiver(null, filter))
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) { update(intent) }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val style = BatteryAnimationPreferences.style(context)
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF101A35), Color(0xFF123C43), Color(0xFF0B1224)))
        ).padding(24.dp)
    ) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(if (preview) "ANIMATION PREVIEW" else "POWER CONNECTED", color = Color(0xFFBDF9D3), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(32.dp))
            BatteryOrb(reading.percent, preview || reading.charging, style, Modifier.size(210.dp))
            Spacer(Modifier.height(22.dp))
            Text("${reading.percent}%", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Bold)
            Text(reading.source, color = Color.White.copy(alpha = .82f), fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))
            Text("$style animation${if (preview) " · change it in Battery" else ""}", color = Color(0xFFBDF9D3), fontSize = 14.sp)
        }
    }
}
