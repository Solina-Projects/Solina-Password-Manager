package app.solina.passwordmanager.core.sync

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class StagedFileWriterTest {
    private class MemoryIo : StagedFileIo {
        val files = linkedMapOf<String, ByteArray>()
        var failWrite = false
        var failRenameFrom: String? = null
        override fun read(name: String) = files[name]?.copyOf()
        override fun create(name: String): Boolean {
            if (name in files) return false
            files[name] = ByteArray(0)
            return true
        }
        override fun write(name: String, bytes: ByteArray) {
            if (failWrite) throw IOException("injected write failure")
            files[name] = bytes.copyOf()
        }
        override fun rename(from: String, to: String): Boolean {
            if (failRenameFrom == from || from !in files || to in files) return false
            files[to] = files.remove(from)!!
            return true
        }
        override fun delete(name: String): Boolean { files.remove(name); return true }
    }

    private fun writer(io: MemoryIo) = StagedFileWriter(io, 1024, newToken = { "test-token" })

    @Test fun stagesVerifiesAndCommitsOnlyFinalName() {
        val io = MemoryIo()
        writer(io).write("i-id-1-12345678.slni", byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), io.files["i-id-1-12345678.slni"])
        assertTrue(io.files.keys.none { it.startsWith("solina-pending-") })
    }

    @Test fun failedWriteLeavesNoFinalFileAndCleansTemporaryFile() {
        val io = MemoryIo().also { it.failWrite = true }
        try {
            writer(io).write("i-id-1-12345678.slni", byteArrayOf(1, 2, 3))
            fail("expected write failure")
        } catch (_: IOException) {}
        assertFalse("final filename must not be exposed", "i-id-1-12345678.slni" in io.files)
        assertTrue(io.files.isEmpty())
    }

    @Test fun failedReplacementRestoresPreviousFile() {
        val io = MemoryIo()
        io.files["vault.hdr"] = byteArrayOf(1, 1)
        io.failRenameFrom = "solina-pending-test-token"
        try {
            writer(io).write("vault.hdr", byteArrayOf(2, 2))
            fail("expected commit failure")
        } catch (_: IOException) {}
        assertArrayEquals(byteArrayOf(1, 1), io.files["vault.hdr"])
        assertTrue(io.files.keys.none { it.startsWith("solina-backup-") })
    }

    @Test fun interruptedReplacementIsRecoveredBeforeNextWrite() {
        val io = MemoryIo()
        io.files["solina-backup-vault.hdr"] = byteArrayOf(1, 1)
        writer(io).write("vault.hdr", byteArrayOf(1, 1))
        assertArrayEquals(byteArrayOf(1, 1), io.files["vault.hdr"])
        assertFalse("solina-backup-vault.hdr" in io.files)
    }

    @Test fun interruptedPartialFinalIsReplacedByRecoveredBackup() {
        val io = MemoryIo()
        io.files["vault.hdr"] = byteArrayOf(9)
        io.files["solina-backup-vault.hdr"] = byteArrayOf(1, 1)
        writer(io).write("vault.hdr", byteArrayOf(2, 2))
        assertArrayEquals(byteArrayOf(2, 2), io.files["vault.hdr"])
        assertFalse("solina-backup-vault.hdr" in io.files)
    }

    @Test fun oversizedAndUnsafeNamesAreRejected() {
        val io = MemoryIo()
        try { writer(io).write("../escape", byteArrayOf(1)); fail("expected unsafe name") } catch (_: IOException) {}
        try { StagedFileWriter(io, 2).write("ok.bin", byteArrayOf(1, 2, 3)); fail("expected size rejection") } catch (_: IOException) {}
        assertTrue(io.files.isEmpty())
    }
}
