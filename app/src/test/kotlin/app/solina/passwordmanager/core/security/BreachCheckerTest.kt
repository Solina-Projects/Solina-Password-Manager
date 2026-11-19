package app.solina.passwordmanager.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class BreachCheckerTest {
    @Test fun sha1AndKAnonymitySplitMatchStandardVector() {
        val hash = BreachChecker.sha1UpperHex("password")
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", hash)
        assertEquals("5BAA6", BreachChecker.prefix5(hash))
        assertEquals("1E4C9B93F3F0682250B6CF8331B7EE68FD8", BreachChecker.suffix35(hash))
    }

    @Test fun parsesRangeResponseWithPaddingEntries() {
        val suffix = BreachChecker.suffix35(BreachChecker.sha1UpperHex("password"))
        val body = """
            003D68EB55068C33ACE09247EE4C639306B:3
            $suffix:9545824
            FFFFFFF0000000000000000000000000000:0
        """.trimIndent()
        assertEquals(9545824, BreachChecker.parseRangeResponse(body, suffix))
        assertEquals(0, BreachChecker.parseRangeResponse(body, "FFFFFFF0000000000000000000000000000"))
        assertEquals(0, BreachChecker.parseRangeResponse(body, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
    }
}
