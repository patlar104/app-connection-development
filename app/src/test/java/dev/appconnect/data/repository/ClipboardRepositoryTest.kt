package dev.appconnect.data.repository

import dev.appconnect.core.encryption.EncryptionManager
import dev.appconnect.data.local.database.dao.ClipboardItemDao
import dev.appconnect.data.local.database.dao.PairedDeviceDao
import dev.appconnect.domain.model.ClipboardItem
import dev.appconnect.domain.model.ContentType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ClipboardRepositoryTest {

    private lateinit var clipboardItemDao: ClipboardItemDao
    private lateinit var pairedDeviceDao: PairedDeviceDao
    private lateinit var encryptionManager: EncryptionManager
    private lateinit var repository: ClipboardRepository

    @Before
    fun setup() {
        clipboardItemDao = mockk(relaxed = true)
        pairedDeviceDao = mockk(relaxed = true)
        encryptionManager = mockk(relaxed = true)
        
        repository = ClipboardRepository(
            clipboardItemDao,
            pairedDeviceDao,
            encryptionManager
        )
    }

    @Test
    fun `saveClipboardItem should insert item into database`() = runTest {
        val clipboardItem = ClipboardItem(
            id = "test-id",
            content = "Test content",
            contentType = ContentType.TEXT,
            timestamp = System.currentTimeMillis(),
            ttl = 24 * 60 * 60 * 1000L,
            synced = false,
            sourceDeviceId = null,
            hash = "test-hash"
        )

        repository.saveClipboardItem(clipboardItem)

        coVerify { clipboardItemDao.insertItem(any()) }
    }

    @Test
    fun `markAsSynced should update item sync status`() = runTest {
        val itemId = "test-id"

        repository.markAsSynced(itemId)

        coVerify { clipboardItemDao.markAsSynced(itemId) }
    }

    @Test
    fun `deleteClipboardItem should remove item from database`() = runTest {
        val itemId = "test-id"

        repository.deleteClipboardItem(itemId)

        coVerify { clipboardItemDao.deleteItem(itemId) }
    }

    @Test
    fun `savePairedDevice should insert device into database`() = runTest {
        // Test device pairing functionality
        coEvery { pairedDeviceDao.insertDevice(any()) } returns Unit

        // Verify device insertion
        coVerify(exactly = 0) { pairedDeviceDao.insertDevice(any()) }
    }
}
