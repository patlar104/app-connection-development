package dev.appconnect.data.repository

import dev.appconnect.core.encryption.EncryptionManager
import dev.appconnect.core.util.EncryptedDataSerializer
import dev.appconnect.data.local.database.dao.ClipboardItemDao
import dev.appconnect.data.local.database.dao.PairedDeviceDao
import dev.appconnect.data.local.database.entity.ClipboardItemEntity
import dev.appconnect.data.local.database.entity.PairedDeviceEntity
import dev.appconnect.domain.model.ClipboardItem
import dev.appconnect.domain.model.ContentType
import dev.appconnect.domain.model.Device
import dev.appconnect.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardRepository @Inject constructor(
    private val clipboardItemDao: ClipboardItemDao,
    private val pairedDeviceDao: PairedDeviceDao,
    private val encryptionManager: EncryptionManager
) {
    fun getAllClipboardItems(): Flow<List<ClipboardItem>> {
        return clipboardItemDao.getAllItems().map { entities ->
            entities.map { it.toDomainModel(encryptionManager) }
        }
    }

    suspend fun getClipboardItem(id: String): ClipboardItem? {
        val entity = clipboardItemDao.getItemById(id) ?: return null
        return entity.toDomainModel(encryptionManager)
    }

    fun getUnsyncedItems(): Flow<List<ClipboardItem>> {
        return clipboardItemDao.getUnsyncedItems().map { entities ->
            entities.map { it.toDomainModel(encryptionManager) }
        }
    }

    suspend fun saveClipboardItem(item: ClipboardItem) {
        val entity = item.toEntity(encryptionManager)
        clipboardItemDao.insertItem(entity)
        Timber.d("Saved clipboard item: ${item.id}")
    }

    suspend fun markAsSynced(id: String) {
        clipboardItemDao.markAsSynced(id)
        Timber.d("Marked clipboard item as synced: $id")
    }

    suspend fun deleteClipboardItem(id: String) {
        clipboardItemDao.deleteItem(id)
        Timber.d("Deleted clipboard item: $id")
    }

    // Paired Devices
    fun getAllPairedDevices(): Flow<List<Device>> {
        return pairedDeviceDao.getAllDevices().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun getPairedDeviceById(id: String): Device? {
        val entity = pairedDeviceDao.getDeviceById(id) ?: return null
        return entity.toDomainModel()
    }

    suspend fun getPairedDevices(): List<Device> {
        return pairedDeviceDao.getTrustedDevices().map { it.toDomainModel() }
    }

    suspend fun savePairedDevice(device: Device) {
        val entity = device.toEntity()
        pairedDeviceDao.insertDevice(entity)
        Timber.d("Saved paired device: ${device.id}")
    }

    suspend fun updatePairedDevice(device: Device) {
        val entity = device.toEntity()
        pairedDeviceDao.updateDevice(entity)
        Timber.d("Updated paired device: ${device.id}")
    }

    suspend fun updateLastSeen(deviceId: String, timestamp: Long) {
        pairedDeviceDao.updateLastSeen(deviceId, timestamp)
    }

    suspend fun deletePairedDevice(id: String) {
        pairedDeviceDao.deleteDevice(id)
        Timber.d("Deleted paired device: $id")
    }
}

// Extension functions for conversions
private fun ClipboardItemEntity.toDomainModel(encryptionManager: EncryptionManager): ClipboardItem {
    val decryptedContent = try {
        val encryptedData = EncryptedDataSerializer.parse(content)
        encryptionManager.decrypt(encryptedData)
    } catch (e: Exception) {
        Timber.e(e, "Failed to decrypt clipboard item: $id")
        // Return placeholder to indicate decryption failure instead of silent empty string
        "[Decryption Failed]"
    }

    return ClipboardItem(
        id = id,
        content = decryptedContent,
        contentType = ContentType.valueOf(contentType),
        timestamp = timestamp,
        ttl = ttl,
        synced = synced,
        sourceDeviceId = sourceDeviceId,
        hash = hash
    )
}

private fun ClipboardItem.toEntity(encryptionManager: EncryptionManager): ClipboardItemEntity {
    val encryptedData = encryptionManager.encrypt(content)
    val encryptedContent = EncryptedDataSerializer.serialize(encryptedData)

    return ClipboardItemEntity(
        id = id,
        content = encryptedContent,
        contentType = contentType.name,
        timestamp = timestamp,
        ttl = ttl,
        synced = synced,
        sourceDeviceId = sourceDeviceId,
        hash = hash
    )
}

private fun PairedDeviceEntity.toDomainModel(): Device {
    return Device(
        id = id,
        name = name,
        publicKey = publicKey,
        certificateFingerprint = certificateFingerprint,
        lastSeen = lastSeen,
        isTrusted = isTrusted,
        cdmAssociationId = cdmAssociationId,
        bluetoothAddress = bluetoothAddress
    )
}

private fun Device.toEntity(): PairedDeviceEntity {
    return PairedDeviceEntity(
        id = id,
        name = name,
        publicKey = publicKey,
        certificateFingerprint = certificateFingerprint,
        lastSeen = lastSeen,
        isTrusted = isTrusted,
        cdmAssociationId = cdmAssociationId,
        bluetoothAddress = bluetoothAddress
    )
}


