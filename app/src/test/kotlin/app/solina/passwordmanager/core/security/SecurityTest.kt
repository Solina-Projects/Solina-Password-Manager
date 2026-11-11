package app.solina.passwordmanager.core.security

import app.solina.passwordmanager.core.crypto.CorruptVaultException
import app.solina.passwordmanager.core.crypto.KdfParams
import app.solina.passwordmanager.core.crypto.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SecurityTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val fast = KdfParams(64, 1, 1)

    // ───── VaultFiles ─────

    @Test
    fun headerSurvivesWriteAndRead() {
        val files = VaultFiles(File(tmp.root, "v"))
        assertFalse(files.hasVault())
        val created = VaultCrypto.create("pw".toCharArray(), fast)
        files.writeHeader(created.header)
        assertTrue(files.hasVault())
        val back = VaultCrypto.unlockWithPassword(files.readHeader(), "pw".toCharArray())
        assertArrayEquals(created.vaultKey, back)
    }

    @Test
    fun overwriteKeepsLatestAndLeavesNoTempFile() {
        val dir = File(tmp.root, "v")
        val files = VaultFiles(dir)
        val first = VaultCrypto.create("a".toCharArray(), fast)
        files.writeHeader(first.header)
        val changed = VaultCrypto.changePassword(first.header, first.vaultKey, "b".toCharArray())
        files.writeHeader(changed)
        assertArrayEquals(first.vaultKey, VaultCrypto.unlockWithPassword(files.readHeader(), "b".toCharArray()))
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun corruptHeaderFileIsDetected() {
        val dir = File(tmp.root, "v")
        val files = VaultFiles(dir)
        File(dir, "vault.hdr").writeBytes(ByteArray(40) { 7 })
        assertThrows(CorruptVaultException::class.java) { files.readHeader() }
    }

    @Test
    fun missingVaultThrowsAndBiometricBlobRoundTrips() {
        val files = VaultFiles(File(tmp.root, "v"))
        assertThrows(CorruptVaultException::class.java) { files.readHeader() }
        assertNull(files.readBiometric())
        files.writeBiometric(byteArrayOf(1, 2, 3))
        assertTrue(files.hasBiometric())
        assertArrayEquals(byteArrayOf(1, 2, 3), files.readBiometric())
        files.clearBiometric()
        assertFalse(files.hasBiometric())
    }

    @Test
    fun unlockStatePersistsAndToleratesGarbage() {
        val dir = File(tmp.root, "v")
        val files = VaultFiles(dir)
        assertEquals(UnlockState(0, 0), files.readUnlockState())
        files.writeUnlockState(UnlockState(5, 123_456L))
        assertEquals(UnlockState(5, 123_456L), files.readUnlockState())
        File(dir, "unlock.state").writeText("not a number")
        assertEquals(UnlockState(0, 0), files.readUnlockState())
    }

    @Test
    fun deleteAllRemovesEverything() {
        val files = VaultFiles(File(tmp.root, "v"))
        files.writeHeader(VaultCrypto.create("x".toCharArray(), fast).header)
        files.writeBiometric(byteArrayOf(1))
        files.writeUnlockState(UnlockState(3, 1L))
        files.deleteAll()
        assertFalse(files.hasVault())
        assertFalse(files.hasBiometric())
        assertEquals(UnlockState(0, 0), files.readUnlockState())
    }

    // ───── Backoff ─────

    @Test
    fun backoffGrowsThenCaps() {
        assertEquals(0, UnlockBackoff.delayMillis(0))
        assertEquals(0, UnlockBackoff.delayMillis(3))
        assertEquals(2_000, UnlockBackoff.delayMillis(4))
        assertEquals(4_000, UnlockBackoff.delayMillis(5))
        assertEquals(8_000, UnlockBackoff.delayMillis(6))
        assertEquals(15 * 60_000L, UnlockBackoff.delayMillis(1000))
    }

    // ───── KDF calibration ─────

    @Test
    fun calibratorPicksStrongestWithinBudget() {
        val strongest = KdfCalibrator.CANDIDATES.first()
        assertEquals(strongest, KdfCalibrator.pick { 300 })
        // اولی کند است، دومی در بودجه
        assertEquals(KdfCalibrator.CANDIDATES[1], KdfCalibrator.pick { if (it == strongest) 5_000 else 900 })
    }

    @Test
    fun calibratorFallsBackToMinimumAndNeverBelow() {
        val picked = KdfCalibrator.pick { 60_000 }
        assertEquals(KdfCalibrator.CANDIDATES.last(), picked)
        assertTrue(picked.memoryKiB >= 19 * 1024)
    }

    // ───── Session ─────

    @Test
    fun sessionLocksAfterTimeoutInBackground() {
        var now = 1_000L
        val s = VaultSession { now }
        s.unlock(ByteArray(32) { 1 })
        assertTrue(s.unlocked.value)

        s.onBackgrounded()
        now += 30_000
        s.onForegrounded(60_000)
        assertTrue("قبل از مهلت نباید قفل شود", s.unlocked.value)

        s.onBackgrounded()
        now += 61_000
        s.onForegrounded(60_000)
        assertFalse(s.unlocked.value)
        assertNull(s.keyCopy())
    }

    @Test
    fun zeroTimeoutLocksImmediately() {
        val s = VaultSession { 0L }
        s.unlock(ByteArray(32) { 1 })
        s.onBackgrounded()
        s.onForegrounded(0)
        assertFalse(s.unlocked.value)
    }

    @Test
    fun lockWipesKeyAndUnlockTakesOwnershipOfInput() {
        val s = VaultSession()
        val input = ByteArray(32) { 9 }
        s.unlock(input)
        assertTrue("ورودی بعد از تحویل باید پاک شود", input.all { it == 0.toByte() })
        val copy = s.keyCopy()!!
        assertTrue(copy.all { it == 9.toByte() })
        s.lock()
        assertNull(s.keyCopy())
        assertFalse(s.unlocked.value)
    }
}
