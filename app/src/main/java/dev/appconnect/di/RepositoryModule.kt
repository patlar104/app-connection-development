package dev.appconnect.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.appconnect.core.encryption.EncryptionManager
import dev.appconnect.data.local.database.dao.ClipboardItemDao
import dev.appconnect.data.local.database.dao.PairedDeviceDao
import dev.appconnect.data.repository.ClipboardRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideClipboardRepository(
        clipboardItemDao: ClipboardItemDao,
        pairedDeviceDao: PairedDeviceDao,
        encryptionManager: EncryptionManager
    ): ClipboardRepository {
        return ClipboardRepository(
            clipboardItemDao,
            pairedDeviceDao,
            encryptionManager
        )
    }
}

