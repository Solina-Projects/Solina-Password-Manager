package app.solina.passwordmanager.sync

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncManagerPermissionTest {
    @Test fun missingGrantFlagsOnlyRequestsPermissionsNotAlreadyPersisted() {
        val required = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        assertEquals(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, missingUriPermissionFlags(required, Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertEquals(0, missingUriPermissionFlags(required, required))
        assertEquals(required, missingUriPermissionFlags(required, 0))
    }
}
