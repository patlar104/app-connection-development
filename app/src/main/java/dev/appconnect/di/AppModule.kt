package dev.appconnect.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        // WorkManager.getInstance() will use the Configuration.Provider from Application
        // This is safe to call here because the Application class implements Configuration.Provider
        // and it's queried automatically when WorkManager is initialized
        return WorkManager.getInstance(context)
    }
}

