package dev.appconnect.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.appconnect.data.local.database.entity.PairedDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedDeviceDao {
    @Query("SELECT * FROM paired_devices")
    fun getAllDevices(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM paired_devices WHERE id = :id")
    suspend fun getDeviceById(id: String): PairedDeviceEntity?

    @Query("SELECT * FROM paired_devices WHERE isTrusted = 1")
    suspend fun getTrustedDevices(): List<PairedDeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: PairedDeviceEntity)

    @Update
    suspend fun updateDevice(device: PairedDeviceEntity)

    @Query("DELETE FROM paired_devices WHERE id = :id")
    suspend fun deleteDevice(id: String)

    @Query("UPDATE paired_devices SET lastSeen = :timestamp WHERE id = :id")
    suspend fun updateLastSeen(id: String, timestamp: Long)
}

