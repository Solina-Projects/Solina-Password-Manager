package app.solina.passwordmanager.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockTest {
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
}
