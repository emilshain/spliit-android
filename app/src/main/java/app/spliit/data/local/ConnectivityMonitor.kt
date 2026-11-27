package app.spliit.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Whether the phone has a network at all, as a flow of changes.
 *
 * The app does not ask this before a request — a request that fails is the real answer, and a
 * captive portal or a server that is down both look online. It is here for the one thing a
 * failure cannot tell the app: that a network has come back, and whatever was waiting to be sent
 * can be tried again without the person reopening the screen.
 *
 * A phone with no `ConnectivityManager` (or no permission to ask) reports online, so the absence
 * of an answer never stops a request.
 */
class ConnectivityMonitor(context: Context) {

    private val manager: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    /**
     * The current answer, for a caller that wants one reading rather than a subscription.
     *
     * A platform that refuses the question (no `ACCESS_NETWORK_STATE`, a restricted profile)
     * reports online rather than throwing: the app must never crash for want of a hint, and a
     * request that fails is the real answer anyway.
     */
    val isOnline: Boolean
        get() = runCatching {
            val current = manager ?: return true
            val network = current.activeNetwork ?: return false
            val capabilities = current.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)

    /** Every change of that answer, starting with the current one. */
    val online: Flow<Boolean> = callbackFlow {
        val current = manager
        if (current == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }
        if (!runCatching { current.activeNetwork }.isSuccess) {
            // No permission to watch the networks; stay quiet instead of crashing on the edge.
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isOnline)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        runCatching { current.registerDefaultNetworkCallback(callback) }
            .onFailure { trySend(true) }
        trySend(isOnline)
        awaitClose { runCatching { current.unregisterNetworkCallback(callback) } }
    }.conflate()
}
