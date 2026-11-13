package app.solina.passwordmanager.core.storage

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomItemStoreTransactionTest {

    private fun row(id: String) = StoredItem(
        id = id,
        rev = 1,
        updatedAt = 100L,
        deletedAt = null,
        wrappedItemKey = byteArrayOf(1, 2, 3),
        blob = byteArrayOf(4, 5, 6),
    )

    private fun openDatabase(): SolinaDatabase {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, SolinaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun saveAllRollsBackEveryRowWhenMiddleInsertFails() = runBlocking {
        val db = openDatabase()
        try {
            val store = RoomItemStore(db)
            store.save(row("existing"), null, 10)

            db.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER fail_selected_item
                BEFORE INSERT ON items
                WHEN NEW.id = 'fail'
                BEGIN
                    SELECT RAISE(ABORT, 'injected Room batch failure');
                END
                """.trimIndent(),
            )

            var failed = false
            try {
                store.saveAll(listOf(row("first"), row("fail"), row("last")))
            } catch (_: Exception) {
                failed = true
            }

            assertTrue("The injected SQLite trigger must fail the batch", failed)
            assertNotNull("Pre-existing data must survive", store.get("existing"))
            assertNull("First row must roll back with the transaction", store.get("first"))
            assertNull("Failing row must not exist", store.get("fail"))
            assertNull("Rows after the failure must not be written", store.get("last"))
            assertEquals(listOf("existing"), store.all().map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun saveAllPersistsEveryRowWhenTransactionSucceeds() = runBlocking {
        val db = openDatabase()
        try {
            val store = RoomItemStore(db)
            store.saveAll(listOf(row("one"), row("two")))

            assertEquals(setOf("one", "two"), store.all().map { it.id }.toSet())
            assertFalse(store.all().any { it.deletedAt != null })
        } finally {
            db.close()
        }
    }
}
