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
        assertFalse(DomainMatcher.hostMatches("co.uk", "evil.co.uk"))
        assertFalse(DomainMatcher.hostMatches("com.ir", "bank.com.ir"))
        assertFalse(DomainMatcher.hostMatches("github.io", "phish.github.io"))
    }

    @Test fun webMatchIsStrict() {
        val items = listOf(item("1", "GitHub", "github.com"), item("2", "Other", "example.org"), item("3", "Note", "github.com", ItemKind.Note))
        assertEquals(listOf("1"), AutofillMatcher.match(items, "github.com", null).map { it.id })
        assertTrue(AutofillMatcher.match(items, "unknown.net", "com.github.android").isEmpty())
    }

    @Test fun nativeAppMatchesExplicitBindingAndNeverLeaksAllLogins() {
        val items = listOf(
            item("1", "Snapp", "snapp.ir"),
            item("2", "Digikala", "digikala.com"),
            item("3", "Bank", "androidapp://ir.bank.app@abcd1234"),
        )
        assertEquals(listOf("1"), AutofillMatcher.match(items, null, "cab.snapp.passenger").map { it.id })
        assertEquals(listOf("3"), AutofillMatcher.match(items, null, "ir.bank.app", "abcd1234").map { it.id })
        assertTrue(AutofillMatcher.match(items, null, "ir.bank.app", "wrongcert").isEmpty())
        assertTrue(AutofillMatcher.match(items, null, "com.unknown.thing").isEmpty())
    }
}
