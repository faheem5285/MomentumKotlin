package com.faheem.habits.features.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

@Immutable
data class NetworkConnectionState(
    val label: String = "Checking connection…",
    val type: String = "Unknown",
    val metered: Boolean? = null,
    val validated: Boolean? = null,
    val linkSpeedMbps: Int? = null,
    val online: Boolean = false
)

@Immutable
data class NetworkTestResult(val succeeded: Boolean, val message: String, val latencyMillis: Long? = null)

@Singleton
class NetworkRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun observeConnection(): Flow<NetworkConnectionState> = callbackFlow {
        fun publish() { trySend(snapshot()) }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish()
            override fun onLost(network: Network) = publish()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = publish()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) = publish()
        }
        connectivity.registerDefaultNetworkCallback(callback)
        publish()
        awaitClose { runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }.conflate()

    private fun snapshot(): NetworkConnectionState {
        val network = connectivity.activeNetwork
            ?: return NetworkConnectionState(label = "Offline", type = "No connection", online = false)
        val caps = connectivity.getNetworkCapabilities(network)
            ?: return NetworkConnectionState(label = "Connecting", type = "Network available", online = false)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val wifiSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (caps.transportInfo as? WifiInfo)?.linkSpeed?.takeIf { it > 0 }
        } else null
        return NetworkConnectionState(
            label = when {
                !validated -> "Connected, no internet"
                type == "Wi-Fi" -> "Wi-Fi connected"
                type == "Mobile" -> "Mobile data connected"
                else -> "Internet connected"
            },
            type = type,
            metered = connectivity.isActiveNetworkMetered,
            validated = validated,
            linkSpeedMbps = wifiSpeed,
            online = validated
        )
    }

    suspend fun testConnection(): NetworkTestResult = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        val connection = try {
            (URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 5_000
                readTimeout = 5_000
                useCaches = false
            }
        } catch (_: Exception) {
            return@withContext NetworkTestResult(false, "Could not start the connection test.")
        }
        try {
            val code = connection.responseCode
            val elapsed = (SystemClock.elapsedRealtime() - start).coerceAtLeast(0L)
            if (code in 200..399) NetworkTestResult(true, "Connection is working", elapsed)
            else NetworkTestResult(false, "The test server returned error $code.", elapsed)
        } catch (_: Exception) {
            NetworkTestResult(false, "The connection test could not reach the test server.")
        } finally {
            connection.disconnect()
        }
    }
}
