package app.solina.passwordmanager.core.sync

import app.solina.passwordmanager.core.crypto.randomBytes
import app.solina.passwordmanager.core.storage.StoredItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Random

class SyncRecordFuzzTest {
    private val key = randomBytes(32)

    @Test fun roundTripAndNaming() {
        val item = StoredItem("abc-123", 7, 99, null, byteArrayOf(1, 2, 3), byteArrayOf(9, 9, 9, 9))
        val bytes = SyncRecord.encode(item, key)
        val dec = SyncRecord.decode(bytes, key)!!
        assertEquals("abc-123", dec.id); assertEquals(7L, dec.rev); assertEquals(null, dec.deletedAt)
        val name = SyncRecord.fileName(item.id, item.rev, bytes)
        val parsed = SyncRecord.parseName(name)!!
        assertEquals("abc-123", parsed.id); assertEquals(7L, parsed.rev)
        assertEquals(parsed.id, SyncRecord.parseName("$name.bin")!!.id)
    }

    @Test fun tombstoneMustBeEmpty() {
        val t = StoredItem("x", 2, 5, 5, ByteArray(0), ByteArray(0))
        assertEquals(5L, SyncRecord.decode(SyncRecord.encode(t, key), key)!!.deletedAt)
        val bad = StoredItem("x", 2, 5, 5, ByteArray(0), byteArrayOf(1))
        assertNull(SyncRecord.decode(SyncRecord.encode(bad, key), key))
    }

    @Test fun wrongKeyAndMutationsRejectedWithoutCrash() {
        val rnd = Random(7)
        val bytes = SyncRecord.encode(StoredItem("id1", 3, 10, null, ByteArray(40), ByteArray(120)), key)
        assertNull(SyncRecord.decode(bytes, randomBytes(32)))
        repeat(3000) {
            val m = bytes.copyOf()
            repeat(1 + rnd.nextInt(3)) { m[rnd.nextInt(m.size)] = rnd.nextInt(256).toByte() }
            val input = if (it % 7 == 0) m.copyOf(rnd.nextInt(m.size)) else m
            // با HMAC درست، هر جهشی رد می‌شود (یا بی‌اثر بوده)
            val r = SyncRecord.decode(input, key)
            if (r != null) assertEquals("id1", r.id)
        }
        repeat(2000) { SyncRecord.decode(ByteArray(rnd.nextInt(200)).also(rnd::nextBytes), key) }
        repeat(2000) { SyncRecord.parseName(String(CharArray(rnd.nextInt(60)) { "i-ab-.0123slnbf/\\%".random() })) }
    }
}
