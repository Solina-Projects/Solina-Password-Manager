package app.solina.passwordmanager.hardening

import app.solina.passwordmanager.core.Base32
import app.solina.passwordmanager.core.backup.Backup
import app.solina.passwordmanager.core.backup.BackupFormatException
import app.solina.passwordmanager.core.crypto.AuthenticationFailedException
import app.solina.passwordmanager.core.crypto.CorruptVaultException
import app.solina.passwordmanager.core.crypto.KdfParams
import app.solina.passwordmanager.core.crypto.VaultCrypto
import app.solina.passwordmanager.core.crypto.VaultHeader
import app.solina.passwordmanager.core.importer.ImportException
import app.solina.passwordmanager.core.importer.Importers
import app.solina.passwordmanager.core.otp.OtpImport
import app.solina.passwordmanager.core.storage.ItemPayload
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random

/**
 * فازینگ سادهٔ قطعی (seed ثابت) روی همهٔ پارسرهای ورودی نامطمئن. معیار: هیچ استثنای غیرمنتظره،
 * StackOverflow، تعلیق یا مصرف بی‌رویهٔ حافظه نباشد.
 */
class FuzzTest {
    private val rnd = Random(20261112)

    private fun mutate(src: ByteArray, from: Int = 0): ByteArray {
        val b = src.copyOf()
        repeat(1 + rnd.nextInt(4)) {
            when (rnd.nextInt(3)) {
                0 -> if (b.size > from) b[from + rnd.nextInt(b.size - from)] = rnd.nextInt(256).toByte()
                1 -> if (b.size > from) { val i = from + rnd.nextInt(b.size - from); b[i] = (b[i].toInt() xor (1 shl rnd.nextInt(8))).toByte() }
                else -> {}
            }
        }
        return if (rnd.nextInt(5) == 0 && b.size > from + 1) b.copyOf(from + rnd.nextInt(b.size - from)) else b
    }

    private fun mutateText(s: String): String {
        val chars = "{}[]\",:\\\n\r\u0000%+=&/?#é۱"
        val sb = StringBuilder(s)
        repeat(1 + rnd.nextInt(5)) {
            if (sb.isEmpty()) return@repeat
            val i = rnd.nextInt(sb.length)
            when (rnd.nextInt(3)) {
                0 -> sb.setCharAt(i, chars[rnd.nextInt(chars.length)])
                1 -> sb.insert(i, chars[rnd.nextInt(chars.length)])
                else -> sb.deleteCharAt(i)
            }
        }
        return sb.toString()
    }

    private fun randomText(len: Int, alphabet: String) = String(CharArray(len) { alphabet[rnd.nextInt(alphabet.length)] })

    @Test fun vaultHeaderDecodeOnlyThrowsCorrupt() {
        val valid = VaultCrypto.create("pw".toCharArray(), KdfParams(64, 1, 1)).header.encode()
        repeat(3000) {
            val input = if (it % 4 == 0) ByteArray(rnd.nextInt(300)).also(rnd::nextBytes) else mutate(valid)
            try { VaultHeader.decode(input) } catch (e: CorruptVaultException) { }
        }
    }

    @Test fun backupBodyMutationsAreRejectedCleanly() {
        val fast = KdfParams(64, 1, 1)
        val valid = Backup.encode(listOf(ItemPayload(title = "a", password = "p")), "right-password".toCharArray(), 1, fast)
        repeat(300) {
            // فقط salt و بدنه جهش می‌یابد (پارامترهای KDF ثابت می‌مانند تا آزمون کند نشود)
            val input = mutate(valid, from = 17)
            try {
                Backup.decode(input, "right-password".toCharArray())
                // جهش بی‌اثر (مثلاً بایت یکسان) مجاز است
            } catch (e: AuthenticationFailedException) {
            } catch (e: BackupFormatException) {
            }
        }
    }

    @Test fun importersOnlyThrowImportException() {
        val samples = listOf(
            """{"encrypted":false,"folders":[],"items":[{"type":1,"name":"G","login":{"username":"u","password":"p","totp":"JBSWY3DPEHPK3PXP","uris":[{"uri":"x"}]}},{"type":3,"name":"C","card":{"number":"1","expMonth":"3"}}]}""",
            """{"version":1,"header":{"slots":null},"db":{"entries":[{"type":"totp","name":"n","issuer":"i","info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":6,"period":30}}]}}""",
            "name,url,username,password,note\nA,https://a.com,u,\"p,\"\"q\"\"\",\"l1\nl2\"\n",
            "otpauth://totp/A:b?secret=JBSWY3DPEHPK3PXP&digits=8\notpauth://totp/C?secret=MZXW6YTBOI",
        )
        repeat(4000) {
            val text = if (it % 5 == 0) randomText(rnd.nextInt(400), "{}[]\",:\n a1/?=&%\u0000é") else mutateText(samples[rnd.nextInt(samples.size)])
            try { Importers.parse(text) } catch (e: ImportException) { }
        }
    }

    @Test fun deeplyNestedJsonIsRejectedNotCrashing() {
        val depth = 200_000
        val evil = "{\"items\":" + "[".repeat(depth) + "]".repeat(depth) + ",\"folders\":[]}"
        try { Importers.parse(evil); fail("must reject") } catch (e: ImportException) { }
        val evil2 = "{\"db\":" + "{\"a\":".repeat(depth) + "1" + "}".repeat(depth) + ",\"header\":{}}"
        try { Importers.parse(evil2); fail("must reject") } catch (e: ImportException) { }
    }

    @Test fun otpImportNeverThrows() {
        val b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=%-_"
        repeat(6000) {
            val text = when (it % 3) {
                0 -> "otpauth://totp/" + randomText(rnd.nextInt(80), "ab:@%20?&=cdSECRETsecret234567 \u0000é/")
                1 -> "otpauth-migration://offline?data=" + randomText(rnd.nextInt(200), b64)
                else -> mutateText("otpauth://totp/Ex:al?secret=JBSWY3DPEHPK3PXP&issuer=Ex&period=60&digits=8&algorithm=SHA256")
            }
            OtpImport.parse(text)
        }
    }

    @Test fun csvAndBase32NeverThrow() {
        repeat(3000) {
            Importers.csvRows(randomText(rnd.nextInt(300), "ab,\"\r\n \u0000é"))
            Base32.decode(randomText(rnd.nextInt(60), "ABCabc234567=- 0189!"))
        }
        assertTrue(true)
    }
}
