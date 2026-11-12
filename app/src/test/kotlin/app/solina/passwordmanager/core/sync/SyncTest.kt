package app.solina.passwordmanager.core.sync

import app.solina.passwordmanager.core.crypto.randomBytes
import app.solina.passwordmanager.core.security.VaultSession
import app.solina.passwordmanager.core.storage.FakeStore
import app.solina.passwordmanager.core.storage.ItemPayload
import app.solina.passwordmanager.core.storage.VaultRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRemote : SyncRemote {
    val files = LinkedHashMap<String, ByteArray>()
    override suspend fun list() = files.keys.toList()
    override suspend fun read(name: String) = files[name]
    override suspend fun write(name: String, bytes: ByteArray) { files[name] = bytes }
    override suspend fun delete(name: String) { files.remove(name) }
}

class SyncTest {
    private val vk = randomBytes(32)
    private var t = 1_000L
    private val remote = MemoryRemote()

    private inner class Device(tag: String, key: ByteArray = vk) {
        val store = FakeStore()
        val session = VaultSession().also { it.unlock(key.copyOf()) }
        var n = 0
        val repo = VaultRepository(store, session, clock = { t += 10; t }, newId = { "$tag-${++n}" })
        val engine = SyncEngine(store, remote, clock = { t += 10; t })
        val key = key
        suspend fun sync(): SyncReport = engine.sync(key).also { repo.load() }
        fun titles() = repo.items.value.map { it.payload.title }.sorted()
    }

    private fun p(title: String, pw: String = "pw-xxxxxxxx-1") = ItemPayload(title = title, password = pw)

    @Test fun addPropagatesAndIsIdempotent() = runBlocking {
        val a = Device("a"); val b = Device("b")
        a.repo.add(p("GitHub")); a.sync()
        val rb = b.sync()
        assertEquals(listOf("GitHub"), b.titles()); assertEquals(1, rb.pulled)
        val again = b.sync()
        assertEquals(0, again.pulled); assertEquals(0, again.pushed); assertEquals(0, again.conflicts)
        assertEquals(0, a.sync().pulled)
    }

    @Test fun editPropagatesAndOldFileIsCleaned() = runBlocking {
        val a = Device("a"); val b = Device("b")
        val item = a.repo.add(p("v1")); a.sync(); b.sync()
        b.repo.update(item.id, p("v2")); b.sync(); a.sync()
        assertEquals(listOf("v2"), a.titles())
        assertEquals(1, remote.files.keys.count { it.startsWith("i-${item.id}-") })   // فقط نسخهٔ جدید مانده
        assertEquals(listOf("v1"), a.repo.history(item.id).map { it.payload.title })
    }

    @Test fun concurrentEditsConvergeWithoutLosingData() = runBlocking {
        val a = Device("a"); val b = Device("b")
        val item = a.repo.add(p("base")); a.sync(); b.sync()
        a.repo.update(item.id, p("from-A")); b.repo.update(item.id, p("from-B"))
        a.sync(); b.sync(); a.sync(); b.sync()
        val winnerA = a.titles().single(); val winnerB = b.titles().single()
        assertEquals(winnerA, winnerB)
        val loser = if (winnerA == "from-A") "from-B" else "from-A"
        for (d in listOf(a, b)) {
            assertTrue("loser در تاریخچه", d.repo.history(item.id).any { it.payload.title == loser })
        }
        // پایدار: همگام‌سازی بیشتر چیزی عوض نمی‌کند و تاریخچه تکراری نمی‌شود
        val hist = a.repo.history(item.id).size
        a.sync(); b.sync(); a.sync()
        assertEquals(hist, a.repo.history(item.id).size)
    }

    @Test fun deletePropagates() = runBlocking {
        val a = Device("a"); val b = Device("b")
        val item = a.repo.add(p("x")); a.sync(); b.sync()
        a.repo.delete(item.id); a.sync(); b.sync()
        assertTrue(b.titles().isEmpty())
        assertEquals(0, b.sync().pulled)
    }

    @Test fun liveBeatsTombstoneAtSameRevision() = runBlocking {
        val a = Device("a"); val b = Device("b")
        val item = a.repo.add(p("keep-me")); a.sync(); b.sync()
        a.repo.delete(item.id); b.repo.update(item.id, p("edited"))
        a.sync(); b.sync(); a.sync(); b.sync()
        assertEquals(listOf("edited"), a.titles()); assertEquals(listOf("edited"), b.titles())
    }

    @Test fun laterDeleteBeatsOlderEdit() = runBlocking {
        val a = Device("a"); val b = Device("b")
        val item = a.repo.add(p("x")); a.sync(); b.sync()
        b.repo.update(item.id, p("y")); b.sync(); a.sync()
        a.repo.delete(item.id); a.sync(); b.sync()
        assertTrue(a.titles().isEmpty() && b.titles().isEmpty())
    }

    @Test fun forgedAndForeignRecordsAreRejected() = runBlocking {
        val a = Device("a"); val b = Device("b")
        val item = a.repo.add(p("real")); a.sync()
        // رکورد با کلید دیگر (مهاجمی که کلید خزانه را ندارد)
        val other = Device("evil", randomBytes(32))
        other.repo.add(p("evil")); other.engine.sync(other.key)   // به همان remote هم می‌نویسد
        // تمام فایل‌های evil را هم دست‌کاری می‌کنیم: یک بایت خراب‌شده از فایل واقعی
        val real = remote.files.keys.first { it.startsWith("i-${item.id}-") }
        val corrupted = remote.files[real]!!.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        remote.files["i-victim-5-00000000.slni"] = corrupted
        remote.files["i-junk-1-deadbeef.slni"] = ByteArray(5)
        val rb = b.sync()
        assertEquals(listOf("real"), b.titles())
        assertTrue(rb.rejected >= 3)
    }

    @Test fun hostileNamesAreIgnored() = runBlocking {
        val b = Device("b")
        remote.files["../../etc/passwd"] = ByteArray(10)
        remote.files["i-..%2f-1-aaaaaaaa.slni"] = ByteArray(10)
        val r = b.sync()
        assertEquals(0, r.pulled); assertTrue(b.titles().isEmpty())
    }

    @Test fun threeDevicesConverge() = runBlocking {
        val a = Device("a"); val b = Device("b"); val c = Device("c")
        a.repo.add(p("one")); b.repo.add(p("two")); c.repo.add(p("three"))
        repeat(2) { a.sync(); b.sync(); c.sync() }
        val expect = listOf("one", "three", "two")
        assertEquals(expect, a.titles()); assertEquals(expect, b.titles()); assertEquals(expect, c.titles())
    }
}
