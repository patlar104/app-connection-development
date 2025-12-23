package dev.appconnect.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.appconnect.data.local.database.dao.PairedDeviceDao
import dev.appconnect.network.PairedDeviceTrustManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun providePairedDeviceTrustManager(
        pairedDeviceDao: PairedDeviceDao
    ): PairedDeviceTrustManager {
        return PairedDeviceTrustManager(pairedDeviceDao)
    }
}

