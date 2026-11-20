package app.solina.refuge.core.storage

import app.solina.refuge.core.crypto.randomBytes
import app.solina.refuge.core.security.VaultSession
import app.solina.refuge.core.sync.SyncEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** سطل زباله: حذف نرم، بازگردانی و پاک‌سازی خودکار tombstoneهای منقضی. */
class TrashTest {
    private var t = 1_000_000L
    private fun unlockedSession() = VaultSession().also { it.unlock(randomBytes(32)) }
    private fun repo(store: FakeStore = FakeStore()): VaultRepository {
        var n = 0
        return VaultRepository(store, unlockedSession(), clock = { t += 10; t }, newId = { "id${++n}" })
    }
    private fun login(title: String) = ItemPayload(title = title, username = "u", password = "Tr0ub4dor&3-horse-battery")

    @Test fun deleteAppearsInTrashAndRestores() = runBlocking {
        val store = FakeStore(); val r = repo(store)
        val item = r.add(login("GitHub"))
        r.update(item.id, login("GitHub-renamed"))
        r.delete(item.id)
        val trash = r.trashedItems()
        assertEquals(1, trash.size)
        assertEquals("GitHub-renamed", trash[0].payload.title)
        assertTrue(trash[0].deletedAt > 0)
        val restored = r.restoreTrashed(item.id)!!
        assertEquals("GitHub-renamed", restored.payload.title)
        assertEquals(item.rev + 3, restored.rev)
        assertEquals(1, r.items.value.size)
        assertTrue(r.trashedItems().isEmpty())
        // ردیف دوباره زنده است (tombstone نیست) تا همگام‌سازی بازگردانی را منتشر کند.
        assertNull(store.items[item.id]!!.deletedAt)
    }

    @Test fun purgeRemovesRowAndHistory() = runBlocking {
        val store = FakeStore(); val r = repo(store)
        val item = r.add(login("a"))
        r.delete(item.id)
        assertEquals(1, r.trashedItems().size)
        r.purgeTrashed(item.id)
        assertTrue(r.trashedItems().isEmpty())
        assertNull(store.items[item.id])
        assertTrue(store.hist.isEmpty())
    }

    @Test fun purgeExpiredOnlyRemovesOldTombstones() = runBlocking {
        val store = FakeStore(); val r = repo(store)
        val old = r.add(login("old")); r.delete(old.id)
        val fresh = r.add(login("fresh")); r.delete(fresh.id)
        // tombstone قدیمی را ۹۱ روز به عقب می‌بریم.
        val oldRow = store.items[old.id]!!
        store.items[old.id] = StoredItem(oldRow.id, oldRow.rev, oldRow.updatedAt, t - SyncEngine.TOMBSTONE_RETENTION_MILLIS - 1, ByteArray(0), ByteArray(0))
        val purged = r.purgeExpiredTrash(now = t)
        assertEquals(1, purged)
        assertEquals(listOf("fresh"), r.trashedItems().map { it.payload.title })
        assertNull(store.items[old.id])
    }

    @Test fun restoreUnknownIdReturnsNull() = runBlocking {
        val r = repo()
        assertNull(r.restoreTrashed("no-such-id"))
    }
}
