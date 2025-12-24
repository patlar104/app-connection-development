package dev.appconnect.data.local.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.appconnect.data.local.database.AppDatabase
import dev.appconnect.data.local.database.entity.ClipboardItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardItemDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var clipboardItemDao: ClipboardItemDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
        clipboardItemDao = database.clipboardItemDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveClipboardItem() = runTest {
        val clipboardItem = ClipboardItemEntity(
            id = "test-id",
            content = "encrypted-content",
            contentType = "TEXT",
            timestamp = System.currentTimeMillis(),
            ttl = 24 * 60 * 60 * 1000L,
            synced = false,
            sourceDeviceId = null,
            hash = "test-hash"
        )

        clipboardItemDao.insertItem(clipboardItem)

        val retrieved = clipboardItemDao.getItemById("test-id")
        assertEquals(clipboardItem.id, retrieved?.id)
        assertEquals(clipboardItem.content, retrieved?.content)
    }

    @Test
    fun getAllItemsReturnsAllInsertedItems() = runTest {
        val item1 = ClipboardItemEntity(
            id = "id-1",
            content = "content-1",
            contentType = "TEXT",
            timestamp = System.currentTimeMillis(),
            ttl = 24 * 60 * 60 * 1000L,
            synced = false,
            sourceDeviceId = null,
            hash = "hash-1"
        )
        val item2 = ClipboardItemEntity(
            id = "id-2",
            content = "content-2",
            contentType = "TEXT",
            timestamp = System.currentTimeMillis(),
            ttl = 24 * 60 * 60 * 1000L,
            synced = false,
            sourceDeviceId = null,
            hash = "hash-2"
        )

        clipboardItemDao.insertItem(item1)
        clipboardItemDao.insertItem(item2)

        val allItems = clipboardItemDao.getAllItems().first()
        assertEquals(2, allItems.size)
    }

    @Test
    fun markAsSyncedUpdatesItem() = runTest {
        val clipboardItem = ClipboardItemEntity(
            id = "test-id",
            content = "encrypted-content",
            contentType = "TEXT",
            timestamp = System.currentTimeMillis(),
            ttl = 24 * 60 * 60 * 1000L,
            synced = false,
            sourceDeviceId = null,
            hash = "test-hash"
        )

        clipboardItemDao.insertItem(clipboardItem)
        clipboardItemDao.markAsSynced("test-id")

        val updated = clipboardItemDao.getItemById("test-id")
        assertEquals(true, updated?.synced)
    }

    @Test
    fun deleteItemRemovesFromDatabase() = runTest {
        val clipboardItem = ClipboardItemEntity(
            id = "test-id",
            content = "encrypted-content",
            contentType = "TEXT",
            timestamp = System.currentTimeMillis(),
            ttl = 24 * 60 * 60 * 1000L,
            synced = false,
            sourceDeviceId = null,
            hash = "test-hash"
        )

        clipboardItemDao.insertItem(clipboardItem)
        clipboardItemDao.deleteItem("test-id")

        val deleted = clipboardItemDao.getItemById("test-id")
        assertNull(deleted)
    }
}
