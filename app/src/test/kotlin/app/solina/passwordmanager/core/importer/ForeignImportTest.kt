package app.solina.passwordmanager.core.importer

import app.solina.passwordmanager.core.storage.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** فایل‌های رمزشدهٔ آزمایشی را همان‌طور که مستندات هر برنامه می‌گوید می‌سازد و با ایمپورت برمی‌گرداند. */
class ForeignImportTest {
    private val rnd = SecureRandom()
    private fun rand(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun gcm(key: ByteArray, nonce: ByteArray, plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return c.doFinal(plain)
    }
    private val secret = "JBSWY3DPEHPK3PXP"

    // ───── Aegis ─────
    private fun aegisEncrypted(password: String): String {
        val db = """{"version":3,"entries":[{"type":"totp","name":"me@x.com","issuer":"GitHub","info":{"secret":"$secret","algo":"SHA1","digits":6,"period":30}},{"type":"hotp","name":"h","issuer":"H","info":{"secret":"$secret","algo":"SHA1","digits":6,"counter":1}}],"groups":[]}"""
        val master = rand(32)
        val salt = rand(32); val n = 1024; val r = 8; val p = 1
        val wrapper = ForeignCrypto.scrypt(password.toCharArray(), salt, n, r, p)
        val keyNonce = rand(12)
        val wrapped = gcm(wrapper, keyNonce, master)
        val dbNonce = rand(12)
        val dbEnc = gcm(master, dbNonce, db.toByteArray())
        val ct = dbEnc.copyOfRange(0, dbEnc.size - 16); val tag = dbEnc.copyOfRange(dbEnc.size - 16, dbEnc.size)
        val key = wrapped.copyOfRange(0, 32); val keyTag = wrapped.copyOfRange(32, 48)
        return """{"version":1,"header":{"slots":[{"type":1,"uuid":"u","key":"${hex(key)}","key_params":{"nonce":"${hex(keyNonce)}","tag":"${hex(keyTag)}"},"n":$n,"r":$r,"p":$p,"salt":"${hex(salt)}"}],"params":{"nonce":"${hex(dbNonce)}","tag":"${hex(tag)}"}},"db":"${b64(ct)}"}"""
    }

    @Test fun aegisEncryptedRoundTrip() {
        val r = Importers.parse(aegisEncrypted("correct horse"), "correct horse".toCharArray())
        assertEquals("Aegis", r.source); assertEquals(1, r.items.size); assertEquals(1, r.skipped)
        assertEquals("GitHub", r.items[0].title); assertEquals(secret, r.items[0].totpSecret)
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
    private fun twoFasEncrypted(password: String): String {
        val services = """[{"name":"Google","secret":"$secret","otp":{"account":"a@b.c","issuer":"Google","digits":6,"period":30,"algorithm":"SHA1","tokenType":"TOTP"}},{"name":"Steam","secret":"$secret","otp":{"tokenType":"STEAM"}}]"""
        val salt = rand(256); val iv = rand(12)
        val key = ForeignCrypto.pbkdf2Sha256(password.toCharArray(), salt, 10_000)
        val enc = gcm(key, iv, services.toByteArray())
        return """{"services":null,"servicesEncrypted":"${b64(enc)}:${b64(salt)}:${b64(iv)}","schemaVersion":4}"""
    }

    @Test fun twoFasEncryptedRoundTrip() {
        val r = Importers.parse(twoFasEncrypted("pass-123"), "pass-123".toCharArray())
        assertEquals("2FAS", r.source); assertEquals(1, r.items.size); assertEquals(1, r.skipped)
        assertEquals("Google", r.items[0].title); assertEquals("a@b.c", r.items[0].username)
        assertThrows(WrongImportPasswordException::class.java) { Importers.parse(twoFasEncrypted("pass-123"), "nope".toCharArray()) }
        assertThrows(ImportPasswordRequired::class.java) { Importers.parse(twoFasEncrypted("x")) }
    }

    @Test fun twoFasPlain() {
        val r = Importers.parse("""{"services":[{"name":"Acme","secret":"$secret","otp":{"account":"u","issuer":"Acme","digits":8,"period":60,"algorithm":"SHA256"}}]}""")
        assertEquals(8, r.items[0].totpDigits); assertEquals(60, r.items[0].totpPeriod); assertEquals("SHA256", r.items[0].totpAlgorithm)
    }

    // ───── Bitwarden رمزشده ─────
    private fun bwEnc(text: String, enc: ByteArray, mac: ByteArray): String {
        val iv = rand(16)
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(enc, "AES"), IvParameterSpec(iv))
        val ct = c.doFinal(text.toByteArray())
        val m = Mac.getInstance("HmacSHA256"); m.init(SecretKeySpec(mac, "HmacSHA256")); m.update(iv); m.update(ct)
        return "2.${b64(iv)}|${b64(ct)}|${b64(m.doFinal())}"
    }

    private fun bitwardenEncrypted(password: String): String {
        val salt = b64(rand(16)); val iters = 1000
        val master = ForeignCrypto.pbkdf2Sha256(password.toCharArray(), salt.toByteArray(), iters)
        val enc = ForeignCrypto.hkdfExpand32(master, "enc"); val mac = ForeignCrypto.hkdfExpand32(master, "mac")
        val data = """{"folders":[],"items":[{"type":1,"name":"Site","notes":"n","login":{"username":"u","password":"p","uris":[{"uri":"https://a.com"}],"totp":"$secret"}}]}"""
        return """{"encrypted":true,"passwordProtected":true,"salt":"$salt","kdfType":0,"kdfIterations":$iters,"kdfMemory":null,"kdfParallelism":null,"encKeyValidation_DO_NOT_EDIT":"${bwEnc("x", enc, mac)}","data":"${bwEnc(data, enc, mac)}"}"""
    }

    @Test fun bitwardenEncryptedRoundTrip() {
        val f = bitwardenEncrypted("master-pw")
        val r = Importers.parse(f, "master-pw".toCharArray())
        assertEquals("Bitwarden", r.source); assertEquals("Site", r.items[0].title); assertEquals("p", r.items[0].password)
        assertEquals(secret, r.items[0].totpSecret)
        assertThrows(WrongImportPasswordException::class.java) { Importers.parse(f, "bad".toCharArray()) }
        assertThrows(ImportPasswordRequired::class.java) { Importers.parse(f) }
    }

    @Test fun bitwardenAccountEncryptedExplains() {
        assertThrows(ImportException::class.java) { Importers.parse("""{"encrypted":true,"items":[]}""", "x".toCharArray()) }
    }

    // ───── Proton / Ente / سایر ─────
    @Test fun protonPassJson() {
        val json = """{"version":"1.0","userId":"u","encrypted":false,"vaults":{"v1":{"name":"Personal","items":[
          {"itemId":"1","data":{"metadata":{"name":"Mail","note":"nn"},"type":"login","content":{"itemEmail":"a@b.c","itemUsername":"","password":"pw","urls":["https://m.com"],"totpUri":"otpauth://totp/Mail:a?secret=$secret&issuer=Mail"}}},
          {"itemId":"2","data":{"metadata":{"name":"Memo","note":"text"},"type":"note","content":{}}},
          {"itemId":"3","data":{"metadata":{"name":"Visa","note":""},"type":"creditCard","content":{"cardholderName":"Ali","number":"4111","verificationNumber":"123","expirationDate":"2030-07"}}},
          {"itemId":"4","data":{"metadata":{"name":"Al"},"type":"alias","content":{}}}]}}}"""
        val r = Importers.parse(json)
        assertEquals("Proton Pass", r.source); assertEquals(3, r.items.size); assertEquals(1, r.skipped)
        assertEquals("a@b.c", r.items[0].username); assertEquals(secret, r.items[0].totpSecret)
        assertEquals(ItemKind.Note, r.items[1].kind)
        assertEquals("07/30", r.items[2].cardExpiry)
    }

    @Test fun protonZipIsUnpackedAndPgpIsExplained() {
        val json = """{"encrypted":false,"vaults":{"v":{"items":[{"data":{"metadata":{"name":"A"},"type":"login","content":{"password":"x"}}}]}}}"""
        val zip = ByteArrayOutputStream().also { bo -> ZipOutputStream(bo).use { z -> z.putNextEntry(ZipEntry("Proton Pass/data.json")); z.write(json.toByteArray()); z.closeEntry() } }.toByteArray()
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

    @Test fun andOtpAndRaivo() {
        val a = Importers.parse("""[{"secret":"$secret","issuer":"Iss","label":"user","digits":6,"type":"TOTP","algorithm":"SHA1","period":30},{"secret":"$secret","issuer":"H","label":"h","type":"HOTP"}]""")
        assertEquals("andOTP", a.source); assertEquals(1, a.items.size); assertEquals(1, a.skipped)
        val r = Importers.parse("""[{"kind":"TOTP","issuer":"R","account":"acc","secret":"$secret","algorithm":"SHA256","digits":"8","timer":"45"}]""")
        assertEquals("Raivo", r.source); assertEquals(8, r.items[0].totpDigits); assertEquals(45, r.items[0].totpPeriod)
    }

    @Test fun moreCsvFlavours() {
        val lastpass = "url,username,password,totp,extra,name,grouping,fav\nhttps://a.com,u,p,,note,A,g,0\n"
        assertEquals("A", Importers.parse(lastpass).items[0].title)
        val keepass = "\"Title\",\"User Name\",\"Password\",\"URL\",\"Notes\"\n\"K\",\"ku\",\"kp\",\"https://k.com\",\"kn\"\n"
        assertEquals("ku", Importers.parse(keepass).items[0].username)
        val proton = "type,name,url,email,username,password,note,totp,vault\nlogin,P,https://p.com,e@x.com,pu,pp,,,V\nalias,Al,,,,,,,V\n"
        val pr = Importers.parse(proton)
        assertEquals(1, pr.items.size); assertEquals("pu", pr.items[0].username)
    }
}
