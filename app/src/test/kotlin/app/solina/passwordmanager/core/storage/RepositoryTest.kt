package app.solina.passwordmanager.core.storage

import app.solina.passwordmanager.core.Base32
import app.solina.passwordmanager.core.crypto.randomBytes
import app.solina.passwordmanager.core.security.VaultSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeStore : ItemStore {
    val items = LinkedHashMap<String, StoredItem>()
    val hist = ArrayList<StoredHistory>()
    override suspend fun all() = items.values.toList()
    override suspend fun get(id: String) = items[id]
    override suspend fun save(item: StoredItem, archivedPrevious: StoredHistory?, keepHistory: Int) {
        if (archivedPrevious != null) {
            hist.add(archivedPrevious)
            val mine = hist.filter { it.itemId == item.id }.sortedByDescending { it.rev }
            hist.removeAll(mine.drop(keepHistory).toSet())
        }
        items[item.id] = item
    }
    override suspend fun delete(id: String, deletedAt: Long, newRev: Long) {
        hist.removeAll { it.itemId == id }
        items[id] = StoredItem(id, newRev, deletedAt, deletedAt, ByteArray(0), ByteArray(0))
    }
    override suspend fun history(itemId: String) = hist.filter { it.itemId == itemId }.sortedByDescending { it.rev }
    override suspend fun wipeAll() { items.clear(); hist.clear() }
}

class RepositoryTest {
    private var t = 1_000L
    private fun unlockedSession() = VaultSession().also { it.unlock(randomBytes(32)) }
    private fun repo(store: FakeStore = FakeStore(), session: VaultSession = unlockedSession()): VaultRepository {
        var n = 0
        return VaultRepository(store, session, clock = { t += 10; t }, newId = { "id${++n}" })
    }
    private fun login(title: String, pw: String = "Tr0ub4dor&3-horse-battery") =
        ItemPayload(title = title, username = "u", password = pw)

    @Test fun addAndReload() = runBlocking {
        val store = FakeStore(); val session = unlockedSession()
        repo(store, session).add(login("GitHub"))
        val r2 = repo(store, session)
        r2.load()
        assertEquals("GitHub", r2.items.value.single().payload.title)
        assertEquals("u", r2.items.value.single().payload.username)
    }

    @Test fun nothingPlaintextInStore() = runBlocking {
        val store = FakeStore(); val r = repo(store)
        r.add(login("SecretTitleXYZ", "SuperSecretPw123!"))
        val row = store.items.values.single()
        val raw = String(row.blob, Charsets.ISO_8859_1) + String(row.wrappedItemKey, Charsets.ISO_8859_1)
        assertFalse(raw.contains("SecretTitleXYZ")); assertFalse(raw.contains("SuperSecretPw123"))
    }

    @Test fun lockedThrows() = runBlocking {
        val session = unlockedSession(); val r = repo(session = session)
        session.lock()
        try { r.add(login("x")); fail() } catch (e: VaultLockedException) { }
        try { r.load(); fail() } catch (e: VaultLockedException) { }
    }

    @Test fun wrongKeyCountsUnreadable() = runBlocking {
        val store = FakeStore()
        repo(store).add(login("a"))
        val other = repo(store, unlockedSession())
        other.load()
        assertTrue(other.items.value.isEmpty()); assertEquals(1, other.unreadable.value)
    }

    @Test fun tamperedRowIsSkipped() = runBlocking {
        val store = FakeStore(); val session = unlockedSession()
        val r = repo(store, session); r.add(login("a")); r.add(login("b"))
        val row = store.items["id1"]!!
        row.blob[row.blob.size - 1] = (row.blob.last().toInt() xor 1).toByte()
        val r2 = repo(store, session); r2.load()
        assertEquals(1, r2.items.value.size); assertEquals(1, r2.unreadable.value)
    }

    @Test fun swappingBlobsBetweenItemsFails() = runBlocking {
        val store = FakeStore(); val session = unlockedSession()
        val r = repo(store, session); r.add(login("a")); r.add(login("b"))
        val a = store.items["id1"]!!; val b = store.items["id2"]!!
        store.items["id1"] = StoredItem("id1", a.rev, a.updatedAt, null, b.wrappedItemKey, b.blob)
        val r2 = repo(store, session); r2.load()
        assertEquals(1, r2.unreadable.value)
    }

    @Test fun rollbackToOldRevFails() = runBlocking {
        val store = FakeStore(); val session = unlockedSession()
        val r = repo(store, session)
        val item = r.add(login("a")); r.update(item.id, login("a2"))
        val old = store.hist.single()
        val cur = store.items[item.id]!!
        store.items[item.id] = StoredItem(item.id, cur.rev, cur.updatedAt, null, old.wrappedItemKey, old.blob)
        val r2 = repo(store, session); r2.load()
        assertEquals(1, r2.unreadable.value)
    }

    @Test fun updateBumpsRevAndKeepsHistory() = runBlocking {
        val store = FakeStore(); val r = repo(store)
        val item = r.add(login("a", "old-password-1!"))
        val u = r.update(item.id, login("a", "new-password-2!"))
        assertEquals(2L, u.rev)
        val h = r.history(item.id)
        assertEquals(1, h.size); assertEquals("old-password-1!", h[0].payload.password)
        assertEquals(item.payload.createdAt, u.payload.createdAt)
    }

    @Test fun unchangedUpdateIsNoop() = runBlocking {
        val store = FakeStore(); val r = repo(store)
        val item = r.add(login("a"))
        val u = r.update(item.id, item.payload)
        assertEquals(1L, u.rev); assertTrue(store.hist.isEmpty())
    }

    @Test fun historyCappedAndRestore() = runBlocking {
        val store = FakeStore(); val r = repo(store)
        val item = r.add(login("a", "pw-0-xxxxxxxx"))
        for (i in 1..15) r.update(item.id, login("a", "pw-$i-xxxxxxxx"))
        val h = r.history(item.id)
        assertEquals(VaultRepository.MAX_HISTORY, h.size)
        assertEquals("pw-14-xxxxxxxx", h.first().payload.password)
        val restored = r.restore(item.id, h.last())
        assertEquals(h.last().payload.password, restored.payload.password)
        assertEquals(17L, restored.rev)
    }

    @Test fun deleteLeavesTombstoneWithoutData() = runBlocking {
        val store = FakeStore(); val session = unlockedSession(); val r = repo(store, session)
        val item = r.add(login("a")); r.update(item.id, login("b"))
        r.delete(item.id)
        assertTrue(r.items.value.isEmpty())
        val row = store.items[item.id]!!
        assertTrue(row.deletedAt != null); assertEquals(0, row.blob.size); assertTrue(store.hist.isEmpty())
        val r2 = repo(store, session); r2.load()
        assertEquals(0, r2.unreadable.value)
    }

    @Test fun clearEmptiesMemory() = runBlocking {
        val r = repo(); r.add(login("a")); r.clear()
        assertTrue(r.items.value.isEmpty())
    }

    @Test fun payloadRoundTripIncludingUnicode() {
        val p = ItemPayload(ItemKind.Card, "کارت \"شخصی\"", "علی", "pw\n\u0000x", "x.ir", "یادداشت\nدو", "JBSWY3DP", true, "A", "6037", "12/30", "123", 5, 6)
        assertEquals(p, ItemPayload.fromBytes(p.toBytes()))
    }

    @Test fun analyzerFlags() {
        fun vi(id: String, pw: String, totp: String = "") = VaultItem(id, 1, ItemPayload(title = id, password = pw, totpSecret = totp))
        val strong = "kPq7vM#xR42wL!z9Hd"
        val rep = SecurityAnalyzer.analyze(listOf(vi("a", "123456"), vi("b", strong, "JBSWY3DP"), vi("c", strong), vi("d", "Zx9!qLm2#Wp8Rt5vB")))
        assertTrue(Flag.Weak in rep.flags["a"]!!)
        assertTrue(Flag.Reused in rep.flags["b"]!! && Flag.Reused in rep.flags["c"]!!)
        assertFalse(Flag.NoTwoFa in rep.flags["b"]!!)
        assertEquals(1, rep.weak); assertEquals(2, rep.reused)
        assertEquals(25, rep.score)
        assertEquals(100, SecurityAnalyzer.analyze(emptyList()).score)
    }

    @Test fun base32() {
        assertArrayEquals("foobar".toByteArray(), Base32.decode("MZXW6YTBOI======"))
        assertArrayEquals("foob".toByteArray(), Base32.decode("mzxw 6yq-"))
        assertNull(Base32.decode("")); assertNull(Base32.decode("1!"))
    }
}
