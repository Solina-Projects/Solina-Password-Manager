package app.solina.refuge.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRetentionTest {
    @Test fun snapshotNameShapeAndOrdering() {
        val a = BackupRetention.snapshotName(1_700_000_000_000L)
        val b = BackupRetention.snapshotName(1_700_000_001_000L)
        assertTrue(a.startsWith("solina-backup-"))
        assertTrue(a.endsWith(".slnb"))
        assertTrue(BackupRetention.isSnapshotName(a))
        assertTrue(a < b)
        // نام دستی کاربر (solina-backup-yyyyMMdd-HHmm.slnb) اسنپ‌شات خودکار نیست.
        assertFalse(BackupRetention.isSnapshotName("solina-backup-20261120-1230.slnb"))
        assertFalse(BackupRetention.isSnapshotName("solina-backup-20261120-123045.bin"))
        assertFalse(BackupRetention.isSnapshotName("vault.hdr"))
    }

    @Test fun keepsLastFiveByDefault() {
        val names = (1..7).map { "solina-backup-2026112${it}-120000.slnb" }
        assertEquals(
            listOf("solina-backup-20261121-120000.slnb", "solina-backup-20261122-120000.slnb"),
            BackupRetention.selectForDeletion(names),
        )
    }

    @Test fun fewerThanKeepDeletesNothing() {
        val names = (1..5).map { "solina-backup-2026112${it}-120000.slnb" }
        assertEquals(emptyList<String>(), BackupRetention.selectForDeletion(names))
        assertEquals(emptyList<String>(), BackupRetention.selectForDeletion(emptyList()))
    }

    @Test fun ignoresNonSnapshotFiles() {
        val names = listOf(
            "vault.hdr",
            "manifest.slnm",
            "abc123-1-abcdef12.slni",
            "solina-backup-20261120-120000.slnb",
            "solina-backup-20261121-120000.slnb",
            "solina-backup-20261122-120000.slnb",
            "solina-backup-20261123-120000.slnb",
            "solina-backup-20261124-120000.slnb",
            "solina-backup-20261125-120000.slnb",
            "solina-backup-20261126-120000.slnb",
        )
        assertEquals(
            listOf("solina-backup-20261120-120000.slnb", "solina-backup-20261121-120000.slnb"),
            BackupRetention.selectForDeletion(names),
        )
    }

    @Test fun customKeepCount() {
        val names = (1..4).map { "solina-backup-2026112${it}-120000.slnb" }
        assertEquals(
            listOf("solina-backup-20261121-120000.slnb", "solina-backup-20261122-120000.slnb"),
            BackupRetention.selectForDeletion(names, keep = 2),
        )
    }
}
