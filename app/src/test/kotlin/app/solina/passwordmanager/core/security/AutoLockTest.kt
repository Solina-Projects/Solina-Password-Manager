package app.solina.passwordmanager.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockTest {
    @Test fun fifteenMinutesOption() {
        assertEquals(AutoLockOption.FifteenMinutes, AutoLockOption.parse("FifteenMinutes"))
        assertEquals(900_000L, AutoLockOption.FifteenMinutes.millis)
        assertEquals(900_000L, AutoLockOption.parse("FifteenMinutes").millis)
    }

    @Test fun immediateMeansLockOnBackground() {
        // «فوری پس از خروج» همان گزینهٔ «قفل هنگام رفتن به پس‌زمینه» است (مهلت صفر).
        assertEquals(0L, AutoLockOption.Immediate.millis)
        assertEquals(AutoLockOption.Immediate, AutoLockOption.parse("Immediate"))
    }

    @Test fun enforceTimeoutLocksOnlyAfterIdleDeadline() {
        var now = 0L
        val s = VaultSession { now }
        s.unlock(ByteArray(32) { 1 })
        s.enforceTimeout(60_000)               // هنوز در پیش‌زمینه (بدون پس‌زمینه) → قفل نمی‌شود
        assertTrue(s.unlocked.value)
        s.onBackgrounded()
        now = 59_000; s.enforceTimeout(60_000)
        assertTrue(s.unlocked.value)
        now = 60_000; s.enforceTimeout(60_000)
        assertFalse(s.unlocked.value)
        assertTrue(s.keyCopy() == null)
    }

    @Test fun backwardClockJumpLocksImmediately() {
        var now = 10_000L
        val s = VaultSession { now }
        s.unlock(ByteArray(32) { 2 })
        s.onBackgrounded()
        now = 5_000L
        s.enforceTimeout(60_000)
        assertFalse(s.unlocked.value)
        assertTrue(s.keyCopy() == null)
    }

    @Test fun repeatedOnBackgroundedDoesNotResetDeadline() {
        var now = 1_000L
        val s = VaultSession { now }
        s.unlock(ByteArray(32) { 3 })
        s.onBackgrounded()
        now = 50_000L
        s.onBackgrounded()
        now = 62_000L
        s.onForegrounded(60_000)
        assertFalse(s.unlocked.value)
    }
}
