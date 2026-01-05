package com.ble1st.connectias.feature.network.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.ble1st.connectias.feature.network.network.NetworkScanner
import com.ble1st.connectias.feature.network.port.PortScanner
import com.ble1st.connectias.feature.network.ssl.SslScanner
import com.ble1st.connectias.feature.network.wifi.WifiScanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkFeatureModule {

    @Provides
    @Singleton
    fun provideWifiManager(@ApplicationContext context: Context): WifiManager {
        return context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    @Provides
    @Singleton
    fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager {
        return context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Provides
    @Singleton
    fun provideWifiScanner(wifiManager: WifiManager): WifiScanner {
        return WifiScanner(wifiManager)
    }

    @Provides
    @Singleton
    fun provideNetworkScanner(connectivityManager: ConnectivityManager): NetworkScanner {
        return NetworkScanner(connectivityManager)
    }

    @Provides
    @Singleton
    fun providePortScanner(): PortScanner {
        return PortScanner()
    }

    @Provides
    @Singleton
    fun provideSslScanner(): SslScanner {
        return SslScanner()
    }
}
