package app.solina.refuge.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRetentionTest {
    @Test fun snapshotNameShapeAndOrdering() {
        val a = BackupRetention.snapshotName(1_700_000_000_000L)
        val b = BackupRetention.snapshotName(1_700_000_001_000L)
        assertTrue(a.startsWith("refuge-backup-"))
        assertTrue(a.endsWith(".rfgb"))
        assertTrue(BackupRetention.isSnapshotName(a))
        assertTrue(a < b)
        // نام دستی کاربر (refuge-backup-yyyyMMdd-HHmm.rfgb) اسنپ‌شات خودکار نیست.
        assertFalse(BackupRetention.isSnapshotName("refuge-backup-20261120-1230.rfgb"))
        assertFalse(BackupRetention.isSnapshotName("refuge-backup-20261120-123045.bin"))
        assertFalse(BackupRetention.isSnapshotName("vault.hdr"))
    }

    @Test fun keepsLastFiveByDefault() {
        val names = (1..7).map { "refuge-backup-2026112${it}-120000.rfgb" }
        assertEquals(
            listOf("refuge-backup-20261121-120000.rfgb", "refuge-backup-20261122-120000.rfgb"),
            BackupRetention.selectForDeletion(names),
        )
    }

    @Test fun fewerThanKeepDeletesNothing() {
        val names = (1..5).map { "refuge-backup-2026112${it}-120000.rfgb" }
        assertEquals(emptyList<String>(), BackupRetention.selectForDeletion(names))
        assertEquals(emptyList<String>(), BackupRetention.selectForDeletion(emptyList()))
    }

    @Test fun ignoresNonSnapshotFiles() {
        val names = listOf(
            "vault.hdr",
            "manifest.rfgm",
            "abc123-1-abcdef12.rfgi",
            "refuge-backup-20261120-120000.rfgb",
            "refuge-backup-20261121-120000.rfgb",
            "refuge-backup-20261122-120000.rfgb",
            "refuge-backup-20261123-120000.rfgb",
            "refuge-backup-20261124-120000.rfgb",
            "refuge-backup-20261125-120000.rfgb",
            "refuge-backup-20261126-120000.rfgb",
        )
        assertEquals(
            listOf("refuge-backup-20261120-120000.rfgb", "refuge-backup-20261121-120000.rfgb"),
            BackupRetention.selectForDeletion(names),
        )
    }

    @Test fun customKeepCount() {
        val names = (1..4).map { "refuge-backup-2026112${it}-120000.rfgb" }
        assertEquals(
            listOf("refuge-backup-20261121-120000.rfgb", "refuge-backup-20261122-120000.rfgb"),
            BackupRetention.selectForDeletion(names, keep = 2),
        )
    }
}
