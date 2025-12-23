package dev.appconnect.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.appconnect.data.repository.ClipboardRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun clipboardRepository(): ClipboardRepository
}
