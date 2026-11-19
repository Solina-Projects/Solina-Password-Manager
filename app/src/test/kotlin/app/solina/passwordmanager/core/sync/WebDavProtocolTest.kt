package app.solina.passwordmanager.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavProtocolTest {
    @Test fun requiresHttpsAndNormalizesTrailingSlash() {
        assertEquals(
            "https://cloud.example.com/remote.php/dav/files/alice/Solina/",
            WebDavProtocol.normalizeHttpsBaseUrl("https://cloud.example.com/remote.php/dav/files/alice/Solina"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            WebDavProtocol.normalizeHttpsBaseUrl("http://cloud.example.com/dav")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebDavProtocol.normalizeHttpsBaseUrl("file:///etc/passwd")
        }
    }

    @Test fun rejectsPathTraversalFileNames() {
        assertTrue(WebDavProtocol.isSafeFileName("vault.hdr"))
        assertTrue(WebDavProtocol.isSafeFileName("item_01-abc.bin"))
        assertFalse(WebDavProtocol.isSafeFileName("../secret.txt"))
        assertFalse(WebDavProtocol.isSafeFileName("a/b"))
        assertFalse(WebDavProtocol.isSafeFileName(".."))
        assertFalse(WebDavProtocol.isSafeFileName(""))
    }

    @Test fun parsesPropfindXmlWithNamespacesAndSkipsCollectionSelf() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>/dav/Solina-Sync/</d:href></d:response>
              <d:response><d:href>/dav/Solina-Sync/vault.hdr</d:href></d:response>
              <d:response><D:href>https://cloud.example.com/dav/Solina-Sync/item-1-r1.slns</D:href></d:response>
              <d:response><d:href>/dav/Solina-Sync/..%2Fescape</d:href></d:response>
            </d:multistatus>
        """.trimIndent()
        val files = WebDavProtocol.parsePropfindFileNames(xml, "/dav/Solina-Sync/")
        assertEquals(listOf("vault.hdr", "item-1-r1.slns"), files)
    }
}
