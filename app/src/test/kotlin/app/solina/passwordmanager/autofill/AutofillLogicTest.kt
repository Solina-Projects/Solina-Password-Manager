package app.solina.passwordmanager.autofill

import android.text.InputType
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.ItemPayload
import app.solina.passwordmanager.core.storage.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillLogicTest {
    private fun item(id: String, title: String, site: String, kind: ItemKind = ItemKind.Login) =
        VaultItem(id, 1, ItemPayload(kind = kind, title = title, website = site, username = "u", password = "p"))

    @Test fun classifiesByHints() {
        assertEquals(FieldKind.Password, FieldClassifier.classify(FieldInfo(hints = listOf("password"))))
        assertEquals(FieldKind.Username, FieldClassifier.classify(FieldInfo(hints = listOf("emailAddress"))))
        assertEquals(FieldKind.Other, FieldClassifier.classify(FieldInfo(hints = listOf("newPassword"))))
    }

    @Test fun classifiesByInputTypeAndHtml() {
        val pw = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertEquals(FieldKind.Password, FieldClassifier.classify(FieldInfo(inputType = pw)))
        assertEquals(FieldKind.Password, FieldClassifier.classify(FieldInfo(htmlType = "password")))
        val mail = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertEquals(FieldKind.Username, FieldClassifier.classify(FieldInfo(inputType = mail)))
        assertEquals(FieldKind.Other, FieldClassifier.classify(FieldInfo(inputType = InputType.TYPE_CLASS_TEXT)))
    }

    @Test fun classifiesByNames() {
        assertEquals(FieldKind.Username, FieldClassifier.classify(FieldInfo(idEntry = "login_username")))
        assertEquals(FieldKind.Password, FieldClassifier.classify(FieldInfo(hintText = "رمز عبور")))
        assertEquals(FieldKind.Other, FieldClassifier.classify(FieldInfo(idEntry = "search_box")))
    }

    @Test fun picksNearestUsernameBeforePassword() {
        val k = listOf(FieldKind.Username, FieldKind.Other, FieldKind.Username, FieldKind.Password, FieldKind.Username)
        assertEquals(2 to 3, FieldClassifier.pickLoginFields(k))
        assertEquals(0 to null, FieldClassifier.pickLoginFields(listOf(FieldKind.Username, FieldKind.Other)))
        assertEquals(null to 1, FieldClassifier.pickLoginFields(listOf(FieldKind.Other, FieldKind.Password)))
        assertNull(FieldClassifier.pickLoginFields(listOf(FieldKind.Other)))
    }

    @Test fun hostNormalizationAndMatching() {
        assertEquals("github.com", DomainMatcher.normalizeHost("https://www.GitHub.com:443/login?x=1"))
        assertTrue(DomainMatcher.hostMatches("github.com", "gist.github.com"))
        assertTrue(DomainMatcher.hostMatches("https://github.com/login", "github.com"))
        assertFalse(DomainMatcher.hostMatches("github.com", "evil-github.com"))
        assertFalse(DomainMatcher.hostMatches("github.com", "github.com.evil.io"))
        assertFalse(DomainMatcher.hostMatches("", "github.com"))
        assertFalse(DomainMatcher.hostMatches("com", "github.com"))
    }

    @Test fun webMatchIsStrict() {
        val items = listOf(item("1", "GitHub", "github.com"), item("2", "Other", "example.org"), item("3", "Note", "github.com", ItemKind.Note))
        assertEquals(listOf("1"), AutofillMatcher.match(items, "github.com", null).map { it.id })
        assertTrue(AutofillMatcher.match(items, "unknown.net", "com.github.android").isEmpty())
    }

    @Test fun nativeAppMatchesByPackageWithoutDumpingAllEntries() {
        val items = listOf(item("1", "Snapp", "snapp.ir"), item("2", "Digikala", "digikala.com"))
        assertEquals(listOf("1"), AutofillMatcher.match(items, null, "cab.snapp.passenger").map { it.id })
        assertTrue(AutofillMatcher.match(items, null, "com.unknown.thing").isEmpty())
    }

    @Test fun publicSuffixesAndCleartextHttpAreRejected() {
        assertFalse(DomainMatcher.hostMatches("co.uk", "bank.co.uk"))
        assertFalse(DomainMatcher.hostMatches("com.ir", "bank.com.ir"))
        assertFalse(DomainMatcher.hostMatches("com.au", "mail.com.au"))
        assertTrue(DomainMatcher.isSafeWebOrigin("https", "github.com"))
        assertTrue(DomainMatcher.isSafeWebOrigin(null, "github.com"))
        assertFalse(DomainMatcher.isSafeWebOrigin("http", "github.com"))
        assertTrue(DomainMatcher.isSafeWebOrigin("http", "localhost"))
        assertTrue(DomainMatcher.isSafeWebOrigin("http", "127.0.0.1"))
    }

    @Test fun favoritesComeFirstInSuggestions() {
        val items = listOf(
            VaultItem("1", 1, ItemPayload(title = "A", website = "github.com", username = "u", password = "p")),
            VaultItem("2", 1, ItemPayload(title = "B", website = "github.com", username = "u", password = "p", favorite = true)),
            VaultItem("3", 1, ItemPayload(title = "C", website = "github.com", username = "u", password = "p")),
        )
        assertEquals(listOf("2", "1", "3"), AutofillMatcher.match(items, "github.com", null).map { it.id })
    }

    @Test fun trustedBrowserAllowlistBlocksSpoofingApps() {
        assertTrue(TrustedBrowsers.isTrusted("com.android.chrome"))
        assertTrue(TrustedBrowsers.isTrusted("org.mozilla.firefox"))
        assertFalse(TrustedBrowsers.isTrusted("com.evil.phishing"))
        assertFalse(TrustedBrowsers.isTrusted(null))
    }

    @Test fun multiStepRemembersUsernameStepForPasswordOnlyStep() {
        val memory = AutofillSessionMemory()
        val github = item("1", "GitHub", "github.com")
        memory.remember(github, "github.com", "com.android.chrome")
        // همان دامنه: رمز پیشنهاد می‌شود.
        assertEquals(github, memory.recallForPasswordOnly("github.com", "com.android.chrome"))
        // یک‌بارمصرف است.
        assertNull(memory.recallForPasswordOnly("github.com", "com.android.chrome"))
    }

    @Test fun multiStepRecallsByPackageWhenHostIsMissing() {
        val memory = AutofillSessionMemory()
        val bank = item("2", "Bank", "bank.ir")
        memory.remember(bank, null, "com.bank.app")
        assertEquals(bank, memory.recallForPasswordOnly(null, "com.bank.app"))
    }

    @Test fun multiStepNeverRecallsForDifferentOrigin() {
        val memory = AutofillSessionMemory()
        val github = item("1", "GitHub", "github.com")
        memory.remember(github, "github.com", "com.android.chrome")
        // دامنهٔ دیگر: هیچی برنمی‌گردد و حافظه سر جایش می‌ماند.
        assertNull(memory.recallForPasswordOnly("evil.com", "com.android.chrome"))
        assertNull(memory.recallForPasswordOnly(null, "com.other.app"))
        assertEquals(github, memory.recallForPasswordOnly("github.com", null))
    }

    @Test fun multiStepClearAndOverwrite() {
        val memory = AutofillSessionMemory()
        val a = item("1", "A", "a.com")
        val b = item("2", "B", "b.com")
        memory.remember(a, "a.com", null)
        memory.remember(b, "b.com", null)
        assertNull(memory.recallForPasswordOnly("a.com", null))
        assertEquals(b, memory.recallForPasswordOnly("b.com", null))
        memory.remember(a, "a.com", null)
        memory.clear()
        assertNull(memory.recallForPasswordOnly("a.com", null))
    }
}
