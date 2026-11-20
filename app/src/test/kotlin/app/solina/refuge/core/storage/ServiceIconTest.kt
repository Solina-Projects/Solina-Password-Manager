package app.solina.refuge.core.storage

import app.solina.refuge.core.security.ListDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** آیکون سرویس (فاز ۴): استخراج دامنه، تطابق عنوان، و تراکم فهرست — همه آفلاین و خالص. */
class ServiceIconTest {
    @Test fun domainKeyStripsSchemeWwwAndPath() {
        assertEquals("google", ServiceIcon.domainKey("https://mail.google.com/inbox"))
        assertEquals("google", ServiceIcon.domainKey("www.google.com"))
        assertEquals("github", ServiceIcon.domainKey("github.com:443/owner/repo?x=1"))
        assertEquals("google", ServiceIcon.domainKey("https://www.google.co.uk/search"))
        assertNull(ServiceIcon.domainKey(""))
        assertNull(ServiceIcon.domainKey("localhost"))
        assertNull(ServiceIcon.domainKey("not-a-domain"))
    }

    @Test fun knownServicesResolveFromWebsite() {
        val g = ServiceIcon.resolve("https://github.com/refuge", "My repo")
        assertNotNull(g)
        assertEquals("G", g!!.letter)
        val s = ServiceIcon.resolve("https://steamcommunity.com/id/x", "Steam")
        assertNotNull(s)
        assertEquals("S", s!!.letter)
        val t = ServiceIcon.resolve("https://web.telegram.org", "")
        assertNotNull(t)
        assertEquals("T", t!!.letter)
    }

    @Test fun knownServicesResolveFromTitleWhenNoWebsite() {
        val g = ServiceIcon.resolve("", "GitHub")
        assertNotNull(g)
        assertEquals("G", g!!.letter)
        val n = ServiceIcon.resolve("", "Netflix")
        assertNotNull(n)
        assertEquals("N", n!!.letter)
    }

    @Test fun unknownServiceReturnsNull() {
        assertNull(ServiceIcon.resolve("https://my-local-shop.example.com", "My Shop"))
        assertNull(ServiceIcon.resolve("", "بانک محلی من"))
        assertNull(ServiceIcon.resolve("", ""))
    }

    @Test fun sameServiceAlwaysSameColor() {
        val a = ServiceIcon.resolve("https://github.com/a", "")!!
        val b = ServiceIcon.resolve("https://www.github.com/b", "GitHub")!!
        assertEquals(a.colorIndex, b.colorIndex)
        assertTrue(a.colorIndex in 0 until ServiceIcon.PALETTE_SIZE)
    }

    @Test fun listDensityParsesAndDefaults() {
        assertEquals(ListDensity.Compact, ListDensity.parse("Compact"))
        assertEquals(ListDensity.Tiles, ListDensity.parse("Tiles"))
        assertEquals(ListDensity.Normal, ListDensity.parse(null))
        assertEquals(ListDensity.Normal, ListDensity.parse("bogus"))
        assertEquals(3, ListDensity.entries.size)
    }
}
