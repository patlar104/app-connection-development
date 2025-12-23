package dev.appconnect.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.appconnect.data.local.database.entity.ClipboardItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardItemDao {
    @Query("SELECT * FROM clipboard_items ORDER BY timestamp DESC")
    fun getAllItems(): Flow<List<ClipboardItemEntity>>

    @Query("SELECT * FROM clipboard_items WHERE id = :id")
    suspend fun getItemById(id: String): ClipboardItemEntity?

    @Query("SELECT * FROM clipboard_items WHERE synced = 0 ORDER BY timestamp ASC")
    fun getUnsyncedItems(): Flow<List<ClipboardItemEntity>>

    @Query("SELECT * FROM clipboard_items WHERE timestamp + ttl < :now")
    suspend fun getExpiredItems(now: Long): List<ClipboardItemEntity>

    @Query("DELETE FROM clipboard_items WHERE timestamp + ttl < :now")
    suspend fun deleteExpiredItems(now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ClipboardItemEntity)

    @Update
    suspend fun updateItem(item: ClipboardItemEntity)

    @Query("DELETE FROM clipboard_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("UPDATE clipboard_items SET synced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
}

