package com.trailerly.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * NetworkMonitor monitors real-time network connectivity and can be used to show offline banners
 * or prevent API calls when offline. It uses ConnectivityManager to detect connectivity changes
 * and validates that the network has internet capability.
 */
class NetworkMonitor(private val context: Context) {

    companion object {
        private lateinit var instance: NetworkMonitor

        fun initialize(context: Context) {
            instance = NetworkMonitor(context.applicationContext)
        }

        fun getInstance(): NetworkMonitor {
            return instance
        }
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Flow that emits true when connected to internet, false when disconnected.
     * Uses callbackFlow to create a hot flow that survives configuration changes.
     */
    val connectivityFlow: Flow<Boolean> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Check if network has internet capability
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(hasInternet)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(hasInternet)
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Emit initial state
        val currentNetwork = connectivityManager.activeNetwork
        val currentCapabilities = currentNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val initialState = currentCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                currentCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        trySend(initialState)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    /**
     * Synchronous check for current connectivity state.
     * @return true if connected to internet, false otherwise
     */
    fun isConnected(): Boolean {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
