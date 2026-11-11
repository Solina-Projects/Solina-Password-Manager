package app.solina.passwordmanager.core.importer

import app.solina.passwordmanager.core.storage.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImportersTest {
    @Test fun bitwarden() {
        val json = """{"encrypted":false,"folders":[],"items":[
          {"type":1,"name":"GitHub","notes":"n","favorite":true,"login":{"username":"ali","password":"pw1","totp":"otpauth://totp/GitHub:ali?secret=JBSWY3DPEHPK3PXP&digits=8","uris":[{"uri":"https://github.com/login"}]}},
          {"type":2,"name":"Wifi","notes":"code 1234"},
          {"type":3,"name":"Card","card":{"cardholderName":"Ali","number":"4111111111111111","expMonth":"3","expYear":"2030","code":"123"}},
          {"type":4,"name":"Identity"}]}"""
        val r = Importers.parse(json)
        assertEquals("Bitwarden", r.source); assertEquals(3, r.items.size); assertEquals(1, r.skipped)
        val g = r.items[0]
        assertEquals("ali", g.username); assertEquals("https://github.com/login", g.website)
        assertEquals("JBSWY3DPEHPK3PXP", g.totpSecret); assertEquals(8, g.totpDigits); assertEquals(true, g.favorite)
        assertEquals(ItemKind.Note, r.items[1].kind); assertEquals("code 1234", r.items[1].note)
        assertEquals("03/30", r.items[2].cardExpiry); assertEquals("4111111111111111", r.items[2].cardNumber)
    }

    @Test fun bitwardenEncryptedRejected() {
        assertThrows(ImportException::class.java) { Importers.parse("""{"encrypted":true,"items":[]}""") }
    }

    @Test fun aegisPlain() {
        val json = """{"version":1,"header":{"slots":null,"params":null},"db":{"version":2,"entries":[
          {"type":"totp","name":"ali@mail.com","issuer":"Google","info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":6,"period":30}},
          {"type":"steam","name":"s","issuer":"Steam","info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":5,"period":30}},
          {"type":"hotp","name":"h","issuer":"H","info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":6,"counter":1}}]}}"""
        val r = Importers.parse(json)
        assertEquals(1, r.items.size); assertEquals(2, r.skipped)
        assertEquals("Google", r.items[0].title); assertEquals("ali@mail.com", r.items[0].username)
    }

    @Test fun aegisEncryptedRejected() {
        assertThrows(ImportException::class.java) { Importers.parse("""{"version":1,"header":{"slots":[{"type":1}],"params":{}},"db":"abc"}""") }
    }

    @Test fun chromeCsvWithQuotesAndBom() {
        val csv = "﻿name,url,username,password,note\r\n" +
            "Digikala,https://www.digikala.com/,09120000000,\"pa,ss\"\"w\"\"ord\",\"line1\nline2\"\r\n" +
            ",https://snapp.ir,user2,pw2,\r\n"
        val r = Importers.parse(csv)
        assertEquals(2, r.items.size)
        assertEquals("pa,ss\"w\"ord", r.items[0].password); assertEquals("line1\nline2", r.items[0].note)
        assertEquals("snapp.ir", r.items[1].title)
    }

    @Test fun firefoxCsvWithoutNameColumn() {
        val csv = "url,username,password,httpRealm,formActionOrigin,guid\nhttps://example.org,bob,pw,,,{x}\n"
        val r = Importers.parse(csv)
        assertEquals("example.org", r.items.single().title); assertEquals("bob", r.items.single().username)
    }

    @Test fun bitwardenCsvNotesAndTotp() {
        val csv = "folder,favorite,type,name,notes,fields,reason,login_uri,login_username,login_password,login_totp\n" +
            ",0,login,Site,,,,https://a.com,u,p,JBSWY3DPEHPK3PXP\n,0,note,Memo,hello,,,,,,\n"
        val r = Importers.parse(csv)
        assertEquals("JBSWY3DPEHPK3PXP", r.items[0].totpSecret); assertEquals(ItemKind.Note, r.items[1].kind)
    }

    @Test fun otpauthText() {
        assertEquals(2, Importers.parse("otpauth://totp/A?secret=JBSWY3DPEHPK3PXP\notpauth://totp/B?secret=MZXW6YTBOI").items.size)
    }

    @Test fun garbageRejected() {
        assertThrows(ImportException::class.java) { Importers.parse("") }
        assertThrows(ImportException::class.java) { Importers.parse("hello world") }
        assertThrows(ImportException::class.java) { Importers.parse("a,b\n1,2\n") }
        assertThrows(ImportException::class.java) { Importers.parse("""{"foo":1}""") }
    }
}
