package app.solina.refuge.core.security

import app.solina.refuge.core.storage.Flag
import app.solina.refuge.core.storage.ItemKind
import app.solina.refuge.core.storage.ItemPayload
import app.solina.refuge.core.storage.SecurityAnalyzer
import app.solina.refuge.core.storage.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** فهرست آفلاین رمزهای رایج (فاز ۴): بررسی محلی، پرچم جدا (Common) و اثر روی امتیاز. */
class CommonPasswordsTest {
    @Test fun knownCommonPasswordsDetected() {
        assertTrue(CommonPasswords.isCommon("123456"))
        assertTrue(CommonPasswords.isCommon("password"))
        assertTrue(CommonPasswords.isCommon("qwerty"))
        assertTrue(CommonPasswords.isCommon("iloveyou"))
        assertTrue(CommonPasswords.isCommon("123456789"))
        // بدون حساسیت به حروف: «Password» هم رایج است.
        assertTrue(CommonPasswords.isCommon("Password"))
        assertTrue(CommonPasswords.isCommon("QWERTY"))
    }

    @Test fun strongRandomPasswordNotCommon() {
        assertFalse(CommonPasswords.isCommon("xK9!mQ2\$vLp8#zR4"))
        assertFalse(CommonPasswords.isCommon("correct horse battery staple"))
        assertFalse(CommonPasswords.isCommon(""))
    }

    @Test fun listIsSmallEnoughForApk() {
        // فهرست باید کوچک بماند؛ اگر خیلی بزرگ شد، یعنی باید سبک شود.
        assertTrue(CommonPasswords.WORDS.size in 100..1000)
    }

    @Test fun analyzerFlagsCommonWithDistinctReason() {
        val items = listOf(
            VaultItem("1", 1, ItemPayload(ItemKind.Login, "A", password = "123456")),
            VaultItem("2", 1, ItemPayload(ItemKind.Login, "B", password = "xK9!mQ2\$vLp8#zR4")),
        )
        val report = SecurityAnalyzer.analyze(items)
        assertEquals(1, report.commonPasswords)
        assertTrue(Flag.Common in (report.flags["1"] ?: emptySet()))
        assertFalse(Flag.Common in (report.flags["2"] ?: emptySet()))
        // رمز رایج امتیاز را کم می‌کند (مثل ضعف)، حتی اگر «قوی» به‌نظر برسد.
        assertTrue(report.score < 100)
    }

    @Test fun commonButOtherwiseStrongStillFlagged() {
        // «dragon» فقط ۶ حرف دارد و Weak هم می‌شود؛ «password1» آن‌قدر بلند است که
        // ممکن است Weak نشود، ولی Common بودنش باید جداگانه دیده شود.
        val items = listOf(
            VaultItem("1", 1, ItemPayload(ItemKind.Login, "A", password = "password1")),
        )
        val report = SecurityAnalyzer.analyze(items)
        assertTrue(Flag.Common in (report.flags["1"] ?: emptySet()))
    }
}
