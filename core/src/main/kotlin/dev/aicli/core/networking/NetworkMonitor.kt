package dev.aicli.core.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.InetSocketAddress
import java.net.Socket

enum class NetworkState { ONLINE, OFFLINE, CAPTIVE_PORTAL }

/**
 * Real connectivity observation (not a fire-and-forget single check): providers and the
 * installer framework need to know when the network actually comes back so they can offer a
 * retry, not spin forever.
 */
class NetworkMonitor(private val context: Context) {

    fun observe(): Flow<NetworkState> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentState(cm))
            }
            override fun onLost(network: Network) {
                trySend(currentState(cm))
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(currentState(cm))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        trySend(currentState(cm))
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun currentState(cm: ConnectivityManager): NetworkState {
        val network = cm.activeNetwork ?: return NetworkState.OFFLINE
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkState.OFFLINE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkState.OFFLINE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return NetworkState.CAPTIVE_PORTAL
        return NetworkState.ONLINE
    }

    /** A real TCP-level reachability probe, used by diagnostics — not just "is a network interface up." */
    suspend fun canReach(host: String, port: Int = 443, timeoutMs: Int = 4000): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    true
                }
            }.getOrDefault(false)
        }
}
