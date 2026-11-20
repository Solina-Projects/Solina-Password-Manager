package app.solina.refuge.core.passkey

import app.solina.refuge.core.storage.ItemPayload
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyTest {
    private fun record() = PasskeyRecord(
        credentialIdB64 = "abcDEF123",
        rpId = "github.com",
        userHandleB64 = "dXNlcjE",
        userName = "nasser",
        displayName = "Nasser",
        createdAt = 1_700_000_000_000L,
    )

    @Test fun recordJsonRoundTrip() {
        val out = PasskeyRecord.fromJson(record().toJson())!!
        assertEquals(record(), out)
    }

    @Test fun recordJsonKeepsPrivateKeyFieldWhenPresent() {
        val withKey = record().copy(privateKeyPkcs8B64 = "cHJpdmF0ZQ")
        val out = PasskeyRecord.fromJson(withKey.toJson())!!
        assertEquals("cHJpdmF0ZQ", out.privateKeyPkcs8B64)
    }

    @Test fun recordJsonRejectsMissingIdOrRp() {
        assertNull(PasskeyRecord.fromJson(JSONObject().put("rp", "github.com")))
        assertNull(PasskeyRecord.fromJson(JSONObject().put("id", "x")))
        assertNull(PasskeyRecord.fromJson(JSONObject()))
    }

    @Test fun listRoundTripSkipsBadEntries() {
        val arr = PasskeyRecord.listToJson(listOf(record()))
        arr.put(JSONObject().put("id", ""))
        val list = PasskeyRecord.listFromJson(arr)
        assertEquals(listOf(record()), list)
        assertEquals(emptyList<PasskeyRecord>(), PasskeyRecord.listFromJson(null))
    }

    @Test fun itemPayloadRoundTripKeepsPasskeys() {
        val payload = ItemPayload(title = "GitHub", website = "github.com", passkeys = listOf(record()))
        val out = ItemPayload.fromBytes(payload.toBytes())
        assertEquals(listOf(record()), out.passkeys)
    }

    @Test fun itemPayloadWithoutPasskeysStaysBackwardCompatible() {
        val payload = ItemPayload(title = "GitHub", website = "github.com")
        val out = ItemPayload.fromBytes(payload.toBytes())
        assertEquals(emptyList<PasskeyRecord>(), out.passkeys)
        // ورودی قدیمی بدون فیلد passkeys هم خوانده می‌شود (optJSONArray).
        val legacy = ItemPayload.fromBytes(
            JSONObject().put("title", "Old").put("website", "example.com").toString().toByteArray(Charsets.UTF_8),
        )
        assertEquals(emptyList<PasskeyRecord>(), legacy.passkeys)
    }

    @Test fun createRequestJsonShape() {
        val json = JSONObject(
            PasskeyRequests.buildCreateRequestJson(
                "github.com",
                "user-1".toByteArray(),
                "nasser",
                "Nasser",
                "challenge".toByteArray(),
            ),
        )
        assertEquals("github.com", json.getJSONObject("rp").getString("id"))
        assertEquals("nasser", json.getJSONObject("user").getString("name"))
        assertEquals("Nasser", json.getJSONObject("user").getString("displayName"))
        assertTrue(json.getString("challenge").isNotEmpty())
        assertEquals(-7, json.getJSONArray("pubKeyCredParams").getJSONObject(0).getInt("alg"))
        assertEquals("required", json.getJSONObject("authenticatorSelection").getString("residentKey"))
    }

    @Test fun getRequestJsonShape() {
        val json = JSONObject(
            PasskeyRequests.buildGetRequestJson("github.com", "challenge".toByteArray(), listOf("abcDEF123")),
        )
        assertEquals("github.com", json.getString("rpId"))
        assertEquals("abcDEF123", json.getJSONArray("allowCredentials").getJSONObject(0).getString("id"))
    }

    @Test fun parseCreateResponse() {
        val response = """{"id":"abcDEF123","rawId":"abcDEF123","type":"public-key",
            "response":{"clientDataJSON":"e30","attestationObject":"o2A"}}"""
        val rec = PasskeyRequests.parseCreateResponse(
            response, "github.com", "user-1".toByteArray(), "nasser", "Nasser", now = 42L,
        )!!
        assertEquals("abcDEF123", rec.credentialIdB64)
        assertEquals("github.com", rec.rpId)
        assertEquals("nasser", rec.userName)
        assertEquals(42L, rec.createdAt)
        assertEquals("", rec.publicKeyB64)
        assertEquals("", rec.privateKeyPkcs8B64)
    }

    @Test fun parseCreateResponseRejectsEmptyId() {
        assertNull(
            PasskeyRequests.parseCreateResponse(
                """{"type":"public-key","response":{}}""",
                "github.com", "u".toByteArray(), "n", "N",
            ),
        )
    }

    @Test fun parseGetResponse() {
        val response = """{"id":"abcDEF123","rawId":"abcDEF123","type":"public-key",
            "response":{"clientDataJSON":"e30","authenticatorData":"AA","signature":"AA"}}"""
        assertEquals("abcDEF123", PasskeyRequests.parseGetResponse(response))
        assertNull(PasskeyRequests.parseGetResponse("""{"type":"public-key"}"""))
    }
}
