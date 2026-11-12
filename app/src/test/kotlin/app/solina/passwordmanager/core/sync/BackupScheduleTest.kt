package app.solina.passwordmanager.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupScheduleTest {
    private val day = 24L * 3600_000
    private val now = 1_000_000_000_000L

    @Test fun manualNeverRunsAutomatically() {
        for (t in BackupTrigger.entries) assertFalse(shouldRunAutomatically(BackupSchedule.Manual, 0, now, t))
    }

    @Test fun onChangeAlwaysRuns() {
        for (t in BackupTrigger.entries) assertTrue(shouldRunAutomatically(BackupSchedule.OnChange, now, now, t))
    }

    @Test fun periodicRunsOnlyWhenDue() {
        assertFalse(shouldRunAutomatically(BackupSchedule.Daily, now - day + 1000, now, BackupTrigger.Change))
        assertTrue(shouldRunAutomatically(BackupSchedule.Daily, now - day, now, BackupTrigger.Unlock))
        assertFalse(shouldRunAutomatically(BackupSchedule.Weekly, now - 6 * day, now, BackupTrigger.Unlock))
        assertTrue(shouldRunAutomatically(BackupSchedule.Weekly, now - 7 * day, now, BackupTrigger.Change))
        assertFalse(shouldRunAutomatically(BackupSchedule.Monthly, now - 29 * day, now, BackupTrigger.Unlock))
        assertTrue(shouldRunAutomatically(BackupSchedule.Monthly, now - 30 * day, now, BackupTrigger.Unlock))
    }

    @Test fun neverBackedUpRunsImmediately() {
        assertTrue(shouldRunAutomatically(BackupSchedule.Monthly, 0, now, BackupTrigger.Unlock))
    }

    @Test fun parseFallsBackToOnChange() {
        assertEquals(BackupSchedule.Weekly, BackupSchedule.parse("Weekly"))
        assertEquals(BackupSchedule.OnChange, BackupSchedule.parse("???"))
        assertEquals(BackupSchedule.OnChange, BackupSchedule.parse(null))
    }
}
