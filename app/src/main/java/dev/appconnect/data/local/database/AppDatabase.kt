package dev.appconnect.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.appconnect.data.local.database.dao.ClipboardItemDao
import dev.appconnect.data.local.database.dao.PairedDeviceDao
import dev.appconnect.data.local.database.entity.ClipboardItemEntity
import dev.appconnect.data.local.database.entity.PairedDeviceEntity

@Database(
    entities = [ClipboardItemEntity::class, PairedDeviceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clipboardItemDao(): ClipboardItemDao
    abstract fun pairedDeviceDao(): PairedDeviceDao
}

