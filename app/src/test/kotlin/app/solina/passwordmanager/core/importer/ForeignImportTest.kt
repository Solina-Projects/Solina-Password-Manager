package app.solina.passwordmanager.core.importer

import app.solina.passwordmanager.core.OtpType
import app.solina.passwordmanager.core.storage.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** فایل‌های رمزشدهٔ آزمایشی و بردارهای رسمی هر برنامه را می‌سازد و با ایمپورت برمی‌گرداند. */
class ForeignImportTest {
    private val rnd = SecureRandom()
    private fun rand(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun gcm(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad != null) c.updateAAD(aad)
        return c.doFinal(plain)
    }
    private val secret = "JBSWY3DPEHPK3PXP"

    // ───── Aegis ─────
    private fun aegisEncrypted(
        password: String,
        repaired: Boolean = true,
        isBackup: Boolean = false,
        legacyPadMult: Int? = null,
        wrapDbMime: Boolean = false,
        corruptDb: Boolean = false,
    ): String {
        val db = """{"version":3,"entries":[{"type":"totp","name":"me@x.com","issuer":"GitHub","note":null,"info":{"secret":"$secret","algo":"SHA1","digits":6,"period":30}},{"type":"hotp","name":"h","issuer":"H","info":{"secret":"$secret","algo":"SHA-256","digits":6,"period":0,"counter":1}}],"groups":[]}"""
        val master = rand(32)
        val salt = rand(32); val n = 1024; val r = 8; val p = 1
        val wrapper = if (legacyPadMult != null) {
            val exact = password.toByteArray(Charsets.UTF_8)
            val padded = exact.copyOf(password.length * legacyPadMult)
            ForeignCrypto.scryptBytes(padded, salt, n, r, p)
        } else {
            ForeignCrypto.scrypt(password.toCharArray(), salt, n, r, p)
        }
        val keyNonce = rand(12)
        val wrapped = gcm(wrapper, keyNonce, master)
        val dbNonce = rand(12)
        val dbEnc = gcm(master, dbNonce, db.toByteArray())
        val ct = dbEnc.copyOfRange(0, dbEnc.size - 16)
        if (corruptDb && ct.isNotEmpty()) ct[0] = (ct[0].toInt() xor 0xff).toByte()
        val tag = dbEnc.copyOfRange(dbEnc.size - 16, dbEnc.size)
        val key = wrapped.copyOfRange(0, 32); val keyTag = wrapped.copyOfRange(32, 48)
        val dbB64 = if (wrapDbMime) b64(ct).chunked(76).joinToString("\\n") else b64(ct)
        return """{"version":1,"header":{"slots":[{"type":1,"uuid":"u","key":"${hex(key)}","key_params":{"nonce":"${hex(keyNonce)}","tag":"${hex(keyTag)}"},"n":$n,"r":$r,"p":$p,"salt":"${hex(salt)}","repaired":$repaired,"is_backup":$isBackup}],"params":{"nonce":"${hex(dbNonce)}","tag":"${hex(tag)}"}},"db":"$dbB64"}"""
    }

    @Test fun aegisEncryptedRoundTrip() {
        val r = Importers.parse(aegisEncrypted("correct horse"), "correct horse".toCharArray())
        assertEquals("Aegis", r.source); assertEquals(2, r.items.size); assertEquals(0, r.skipped)
        assertEquals("GitHub", r.items[0].title); assertEquals(secret, r.items[0].totpSecret)
        assertEquals("", r.items[0].note)
        assertEquals(OtpType.HOTP, r.items[1].otpType); assertEquals(1L, r.items[1].totpCounter)
        assertEquals("SHA256", r.items[1].totpAlgorithm)
    }

    @Test fun aegisOfficialVectorDecrypts() {
        // بردار رسمی از مخزن beemdevelopment/Aegis (N=32768, r=8, p=1, رمز "test")
        val official = """{"version":1,"header":{"slots":[{"type":1,"uuid":"a8325752-c1be-458a-9b3e-5e0a8154d9ec","key":"491d44550430ba248986b904b8cffd3a6c5755d176ac877bd11b82c934225017","key_params":{"nonce":"e9705513ba4951fa7a0608d2","tag":"931237af257b83c693ddb8f9a7eddaf0"},"n":32768,"r":8,"p":1,"salt":"27ea9ae53fa2f08a8dcd201615a8229422647b3058f9f36b08f9457e62888be1","repaired":true}],"params":{"nonce":"095fd13dee336fa56b4634ff","tag":"5db2470edf2d12f82a89ae7f48ccd50c"}},"db":"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"}"""
        val r = Importers.parse(official, "test".toCharArray())
        assertEquals("Aegis", r.source)
        assertTrue("expected real Aegis entries", r.items.size >= 4)
        assertThrows(WrongImportPasswordException::class.java) { Importers.parse(official, "wrong".toCharArray()) }
    }

    @Test fun aegisLegacyIssue95UnrepairedSlotDecrypts() {
        // رمز ۲۴ کاراکتری روی اسلات ترمیم‌نشده (repaired=false) با بافر ۴× (اندروید ICU) و ۳× (OpenJDK)
        val longPw = "Abcdefgh12345678!@#\$%^&*"
        val f4 = aegisEncrypted(longPw, repaired = false, legacyPadMult = 4)
        val r4 = Importers.parse(f4, longPw.toCharArray())
        assertEquals(2, r4.items.size)
        val f3 = aegisEncrypted(longPw, repaired = false, legacyPadMult = 3)
        val r3 = Importers.parse(f3, longPw.toCharArray())
        assertEquals(2, r3.items.size)
    }

    @Test fun aegisMultilineMimeBase64AndPersianDigitsAndBidiFallback() {
        val f = aegisEncrypted("Pass1234", wrapDbMime = true)
        assertEquals(2, Importers.parse(f, "Pass1234".toCharArray()).items.size)
        // ارقام فارسی صفحه‌کلید (۱۲۳۴) و کاراکتر پنهان RLM
        assertEquals(2, Importers.parse(f, "Pass۱۲۳۴".toCharArray()).items.size)
        assertEquals(2, Importers.parse(f, "\u200FPass1234\n".toCharArray()).items.size)
    }

    @Test fun aegisBackupSlotHintAndBiometricOnlyAndEmptySlotsPlain() {
        val backupFile = aegisEncrypted("bk-pass", isBackup = true)
        val req = assertThrows(ImportPasswordRequired::class.java) { Importers.parse(backupFile) }
        assertEquals("Aegis", req.source)
        assertNotNull(req.hint)
        assertTrue(req.hint!!.contains("رمز بکاپ"))

        val bioOnly = """{"version":1,"header":{"slots":[{"type":2,"uuid":"b","key":"00","key_params":{"nonce":"00","tag":"00"}}],"params":{"nonce":"00","tag":"00"}},"db":"AA=="}"""
        val bioErr = assertThrows(ImportException::class.java) { Importers.parse(bioOnly) }
        assertTrue(bioErr.message!!.contains("اثرانگشت"))

        // فایل سادهٔ Aegis با slots: [] نباید رمز بخواهد
        val plainWithEmptySlots = """{"version":1,"header":{"slots":[],"params":null},"db":{"version":3,"entries":[{"type":"totp","name":"u","issuer":"App","info":{"secret":"$secret","algo":"SHA1","digits":6,"period":30}}]}}"""
        val r = Importers.parse(plainWithEmptySlots)
        assertEquals("Aegis", r.source); assertEquals(1, r.items.size)
    }

    @Test fun aegisCorruptDbDoesNotBlamePassword() {
        val f = aegisEncrypted("right-pw", corruptDb = true)
        val err = assertThrows(ImportException::class.java) { Importers.parse(f, "right-pw".toCharArray()) }
        assertTrue(err.message!!.contains("رمز درست بود"))
    }

    @Test fun aegisWrongOrMissingPassword() {
        val f = aegisEncrypted("correct horse")
        assertThrows(WrongImportPasswordException::class.java) { Importers.parse(f, "wrong".toCharArray()) }
        assertThrows(ImportPasswordRequired::class.java) { Importers.parse(f) }
    }

    @Test fun aegisRejectsHugeScryptParams() {
        val f = aegisEncrypted("pw").replace("\"n\":1024", "\"n\":1073741824")
        assertThrows(ImportException::class.java) { Importers.parse(f, "pw".toCharArray()) }
    }

    // ───── 2FAS ─────
    private fun twoFasEncrypted(password: String, emptyServicesArray: Boolean = true): String {
        val services = """[{"name":"Google","secret":"$secret","groupId":"g1","otp":{"account":"a@b.c","issuer":"Google","digits":6,"period":30,"algorithm":"SHA1","tokenType":"TOTP"}},{"name":"H","secret":"$secret","otp":{"account":"h","tokenType":"HOTP","counter":7,"digits":6,"algorithm":"SHA1"}},{"name":"Steam","secret":"$secret","otp":{"tokenType":"STEAM"}}]"""
        val salt = rand(256); val iv = rand(12)
        val key = ForeignCrypto.pbkdf2Sha256(password.toCharArray(), salt, 10_000)
        val enc = gcm(key, iv, services.toByteArray())
        val svcField = if (emptyServicesArray) "[]" else "null"
        return """{"services":$svcField,"groups":[{"id":"g1","name":"Work"}],"servicesEncrypted":"${b64(enc)}:${b64(salt)}:${b64(iv)}","schemaVersion":4}"""
    }

    @Test fun twoFasEncryptedRoundTripWithEmptyServicesArray() {
        // در بکاپ‌های واقعی 2FAS v3 و v4 فیلد services برابر [] است نه null
        val r = Importers.parse(twoFasEncrypted("pass-123", emptyServicesArray = true), "pass-123".toCharArray())
        assertEquals("2FAS", r.source); assertEquals(2, r.items.size); assertEquals(1, r.skipped)
        assertEquals("Google", r.items[0].title); assertEquals("a@b.c", r.items[0].username); assertEquals("Work", r.items[0].category)
        assertEquals(OtpType.HOTP, r.items[1].otpType); assertEquals(7L, r.items[1].totpCounter)
        assertThrows(WrongImportPasswordException::class.java) { Importers.parse(twoFasEncrypted("pass-123"), "nope".toCharArray()) }
        assertThrows(ImportPasswordRequired::class.java) { Importers.parse(twoFasEncrypted("x")) }
    }

    @Test fun twoFasPlainWithNullServicesEncrypted() {
        val r = Importers.parse("""{"services":[{"name":"Acme","secret":"$secret","otp":{"account":"u","issuer":"Acme","digits":8,"period":60,"algorithm":"SHA256"}}],"servicesEncrypted":null}""")
        assertEquals(8, r.items[0].totpDigits); assertEquals(60, r.items[0].totpPeriod); assertEquals("SHA256", r.items[0].totpAlgorithm)
    }

    // ───── Proton Authenticator رمزشده ─────
    @Test fun protonAuthenticatorEncryptedRoundTrip() {
        val inner = """{"version":1,"entries":[{"id":"1","content":{"uri":"otpauth://totp/Proton:me@pm.me?secret=$secret&issuer=Proton","name":"Proton"}}]}"""
        val salt = rand(16); val nonce = rand(12)
        val key = ForeignCrypto.argon2id("p-auth-99".toCharArray(), salt, iterations = 2, memoryKiB = 19 * 1024, parallelism = 1, keyBytes = 32)
        val ct = gcm(key, nonce, inner.toByteArray(), "proton.authenticator.export.v1".toByteArray())
        val json = """{"version":1,"salt":"${b64(salt)}","content":"${b64(nonce + ct)}"}"""
        assertThrows(ImportPasswordRequired::class.java) { Importers.parse(json) }
        assertThrows(WrongImportPasswordException::class.java) { Importers.parse(json, "bad".toCharArray()) }
        val r = Importers.parse(json, "p-auth-99".toCharArray())
        assertEquals("Proton Authenticator", r.source); assertEquals(1, r.items.size); assertEquals(secret, r.items[0].totpSecret)
    }

    // ───── Bitwarden رمزشده (PBKDF2 و Argon2id) ─────
    private fun bwEnc(text: String, enc: ByteArray, mac: ByteArray): String {
        val iv = rand(16)
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(enc, "AES"), IvParameterSpec(iv))
        val ct = c.doFinal(text.toByteArray())
        val m = Mac.getInstance("HmacSHA256"); m.init(SecretKeySpec(mac, "HmacSHA256")); m.update(iv); m.update(ct)
        return "2.${b64(iv)}|${b64(ct)}|${b64(m.doFinal())}"
    }

    private fun bitwardenEncrypted(password: String, argon2: Boolean = false): String {
        val salt = b64(rand(16))
        val master = if (argon2) {
            ForeignCrypto.bitwardenArgon2(password.toCharArray(), salt, iterations = 1, memoryMiB = 8, parallelism = 1)
        } else {
            ForeignCrypto.pbkdf2Sha256(password.toCharArray(), salt.toByteArray(), 1000)
        }
        val enc = ForeignCrypto.hkdfExpand32(master, "enc"); val mac = ForeignCrypto.hkdfExpand32(master, "mac")
        val data = """{"folders":[{"id":"f1","name":"Personal"}],"items":[{"type":1,"folderId":"f1","name":"Site","notes":null,"login":{"username":"u","password":"p","uris":[{"uri":"https://a.com"}],"totp":null}},{"type":4,"name":"Me","identity":{"firstName":"Ali","lastName":"Reza","email":"a@b.c","phone":"0912"}}]}"""
        val kdfFields = if (argon2) """"kdfType":1,"kdfIterations":1,"kdfMemory":8,"kdfParallelism":1""" else """"kdfType":0,"kdfIterations":1000,"kdfMemory":null,"kdfParallelism":null"""
        return """{"encrypted":true,"passwordProtected":true,"salt":"$salt",$kdfFields,"encKeyValidation_DO_NOT_EDIT":"${bwEnc("x", enc, mac)}","data":"${bwEnc(data, enc, mac)}"}"""
    }

    @Test fun bitwardenEncryptedRoundTripPbkdf2AndArgon2() {
        for (argon2 in listOf(false, true)) {
            val f = bitwardenEncrypted("master-pw", argon2)
            val r = Importers.parse(f, "master-pw".toCharArray())
            assertEquals("Bitwarden", r.source); assertEquals(2, r.items.size)
            assertEquals("Site", r.items[0].title); assertEquals("p", r.items[0].password)
            assertEquals("", r.items[0].totpSecret) // totp: null نباید تبدیل به "NULL" شود!
            assertEquals("", r.items[0].note)
            assertEquals("Personal", r.items[0].category)
            assertEquals(ItemKind.Identity, r.items[1].kind); assertEquals("Ali Reza", r.items[1].fullName)
            assertThrows(WrongImportPasswordException::class.java) { Importers.parse(f, "bad".toCharArray()) }
            assertThrows(ImportPasswordRequired::class.java) { Importers.parse(f) }
        }
    }

    @Test fun bitwardenAccountEncryptedExplains() {
        assertThrows(ImportException::class.java) { Importers.parse("""{"encrypted":true,"items":[]}""", "x".toCharArray()) }
    }

    // ───── Proton Pass / Ente / andOTP (.aes) / Raivo ─────
    @Test fun protonPassJsonAndIdentity() {
        val json = """{"version":"1.0","userId":"u","encrypted":false,"vaults":{"v1":{"name":"Personal","items":[
          {"itemId":"1","data":{"metadata":{"name":"Mail","note":"nn"},"type":"login","content":{"itemEmail":"a@b.c","itemUsername":"","password":"pw","urls":["https://m.com"],"totpUri":"otpauth://totp/Mail:a?secret=$secret&issuer=Mail"}}},
          {"itemId":"2","data":{"metadata":{"name":"Memo","note":"text"},"type":"note","content":{}}},
          {"itemId":"3","data":{"metadata":{"name":"Visa","note":""},"type":"creditCard","content":{"cardholderName":"Ali","number":"4111","verificationNumber":"123","expirationDate":"2030-07"}}},
          {"itemId":"4","data":{"metadata":{"name":"ID"},"type":"identity","content":{"fullName":"Sara","email":"s@x.com","phoneNumber":"0912"}}},
          {"itemId":"5","data":{"metadata":{"name":"Al"},"type":"alias","content":{}}}]}}}"""
        val r = Importers.parse(json)
        assertEquals("Proton Pass", r.source); assertEquals(4, r.items.size); assertEquals(1, r.skipped)
        assertEquals("a@b.c", r.items[0].username); assertEquals(secret, r.items[0].totpSecret); assertEquals("Personal", r.items[0].category)
        assertEquals(ItemKind.Note, r.items[1].kind)
        assertEquals("07/30", r.items[2].cardExpiry)
        assertEquals(ItemKind.Identity, r.items[3].kind); assertEquals("Sara", r.items[3].fullName)
    }

    @Test fun protonZipSkipsMacOsMetadataAndPgpIsExplained() {
        val json = """{"encrypted":false,"vaults":{"v":{"items":[{"data":{"metadata":{"name":"A"},"type":"login","content":{"password":"x"}}}]}}}"""
        val zip = ByteArrayOutputStream().also { bo ->
            ZipOutputStream(bo).use { z ->
                z.putNextEntry(ZipEntry("__MACOSX/._data.json")); z.write("garbage".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("Proton Pass/data.json")); z.write(json.toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        assertEquals("A", Importers.parseBytes(zip).items[0].title)
        val pgpZip = ByteArrayOutputStream().also { bo -> ZipOutputStream(bo).use { z -> z.putNextEntry(ZipEntry("Proton Pass/data.pgp")); z.write("x".toByteArray()); z.closeEntry() } }.toByteArray()
        val e = assertThrows(ImportException::class.java) { Importers.parseBytes(pgpZip) }
        assertTrue(e.message!!.contains("PGP"))
        assertThrows(ImportException::class.java) { Importers.parseBytes("-----BEGIN PGP MESSAGE-----\nabc".toByteArray()) }
    }

    @Test fun enteEncryptedIsExplainedAndOtpauthJsonWorks() {
        val e = assertThrows(ImportException::class.java) { Importers.parse("""{"version":1,"kdfParams":{"memLimit":1,"opsLimit":1,"salt":"s"},"encryptedData":"x","encryptionNonce":"n"}""") }
        assertTrue(e.message!!.contains("Ente"))
        val r = Importers.parse("""{"version":1,"entries":[{"id":"1","content":{"uri":"otpauth://totp/A:b?secret=$secret&issuer=A","name":"x"}}]}""")
        assertEquals(1, r.items.size); assertEquals(secret, r.items[0].totpSecret)
    }

    @Test fun andOtpPlainAndEncryptedAndRaivo() {
        val a = Importers.parse("""[{"secret":"$secret","label":"GitHub - user","digits":6,"type":"TOTP","algorithm":"SHA1","period":30,"tags":["Dev"]},{"secret":"$secret","issuer":"H","label":"h","type":"HOTP","period":0,"counter":3}]""")
        assertEquals("andOTP", a.source); assertEquals(2, a.items.size); assertEquals(0, a.skipped)
        assertEquals("GitHub", a.items[0].title); assertEquals("user", a.items[0].username); assertEquals("Dev", a.items[0].category)
        assertEquals(OtpType.HOTP, a.items[1].otpType); assertEquals(3L, a.items[1].totpCounter)

        // andOTP رمزشده (قالب جدید PBKDF2-SHA1)
        val plainJson = """[{"secret":"$secret","issuer":"Bank","label":"me","digits":6,"type":"TOTP","algorithm":"SHA1","period":30}]""".toByteArray()
        val iters = 1000; val salt = rand(12); val nonce = rand(12)
        val key = ForeignCrypto.pbkdf2Sha1("otp-pw".toCharArray(), salt, iters, 32)
        val ct = gcm(key, nonce, plainJson)
        val aesBytes = ByteBuffer.allocate(4).putInt(iters).array() + salt + nonce + ct
        assertThrows(ImportPasswordRequired::class.java) { Importers.parseBytes(aesBytes, null, "otp_accounts.json.aes") }
        assertThrows(WrongImportPasswordException::class.java) { Importers.parseBytes(aesBytes, "wrong".toCharArray(), "otp_accounts.json.aes") }
        val dec = Importers.parseBytes(aesBytes, "otp-pw".toCharArray(), "otp_accounts.json.aes")
        assertEquals("andOTP", dec.source); assertEquals("Bank", dec.items.single().title)

        // Raivo با TOTP و HOTP
        val r = Importers.parse("""[{"kind":"TOTP","issuer":"R","account":"acc","secret":"$secret","algorithm":"SHA256","digits":"8","timer":"45"},{"kind":"HOTP","issuer":"RH","account":"h","secret":"$secret","algorithm":"SHA1","digits":"6","counter":"9"}]""")
        assertEquals("Raivo", r.source); assertEquals(2, r.items.size)
        assertEquals(8, r.items[0].totpDigits); assertEquals(45, r.items[0].totpPeriod)
        assertEquals(OtpType.HOTP, r.items[1].otpType); assertEquals(9L, r.items[1].totpCounter)
    }

    @Test fun moreCsvFlavoursIncludingSemicolon() {
        val lastpass = "url,username,password,totp,extra,name,grouping,fav\nhttps://a.com,u,p,,note,A,Work,1\n"
        val lp = Importers.parse(lastpass).items[0]
        assertEquals("A", lp.title); assertEquals("Work", lp.category); assertEquals(true, lp.favorite)
        val keepass = "\"Title\",\"User Name\",\"Password\",\"URL\",\"Notes\"\n\"K\",\"ku\",\"kp\",\"https://k.com\",\"kn\"\n"
        assertEquals("ku", Importers.parse(keepass).items[0].username)
        val proton = "type,name,url,email,username,password,note,totp,vault\nlogin,P,https://p.com,e@x.com,pu,pp,,,V\nalias,Al,,,,,,,V\n"
        val pr = Importers.parse(proton)
        assertEquals(1, pr.items.size); assertEquals("pu", pr.items[0].username); assertEquals("V", pr.items[0].category)
        val semi = "name;url;username;password\nS;https://s.ir;u1;p1\n"
        assertEquals("u1", Importers.parse(semi).items.single().username)
    }
}
