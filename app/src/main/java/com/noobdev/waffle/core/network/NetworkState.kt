package com.noobdev.waffle.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Small connectivity helper used to decide whether a cached offline basemap is needed. */
object NetworkState {

    fun isOnline(context: Context): Boolean {
        val manager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                        ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isUnmetered(context: Context): Boolean {
        val manager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                        ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
