package dev.appconnect.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.appconnect.data.local.database.AppDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "appconnect_database"
        ).build()
    }

    @Provides
    fun provideClipboardItemDao(database: AppDatabase) = database.clipboardItemDao()

    @Provides
    fun providePairedDeviceDao(database: AppDatabase) = database.pairedDeviceDao()
}

