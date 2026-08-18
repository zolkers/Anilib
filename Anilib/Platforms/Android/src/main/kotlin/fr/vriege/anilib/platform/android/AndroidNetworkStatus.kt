package fr.vriege.anilib.platform.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import fr.vriege.anilib.feature.network.NetworkStatus

class AndroidNetworkStatus(context: Context) : NetworkStatus {
    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    override fun allowsLargeTransfers(): Boolean {
        val active = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(active) ?: return false
        val connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val localUnmetered = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return connected && localUnmetered
    }
}
