package app.solina.refuge.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthPkceTest {
    @Test fun rfc7636AppendixBChallengeVector() {
        // RFC 7636 Appendix B official test vector:
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expectedChallenge, OAuthPkce.computeCodeChallengeS256(verifier))
    }

    @Test fun codeVerifierHasUrlSafeCharsAndEntropy() {
        val v1 = OAuthPkce.generateCodeVerifier()
        val v2 = OAuthPkce.generateCodeVerifier()
        assertTrue(v1.length in 43..128)
        assertFalse(v1.contains("="))
        assertFalse(v1.contains("+"))
        assertFalse(v1.contains("/"))
        assertTrue(v1 != v2)
    }

    @Test fun buildsHttpsAuthorizationUrlWithPkceAndNonce() {
        val googleReq = OAuthPkce.createPendingRequest(OAuthProvider.Google, "google-client-123")
        val googleUrl = OAuthPkce.buildAuthorizationUrl(googleReq)
        assertTrue(googleUrl.startsWith("https://accounts.google.com/"))
        assertTrue(googleUrl.contains("code_challenge_method=S256"))
        assertTrue(googleUrl.contains("state="))
        assertTrue(googleUrl.contains("nonce="))

        val ghReq = OAuthPkce.createPendingRequest(OAuthProvider.GitHub, "github-client-456")
        val ghUrl = OAuthPkce.buildAuthorizationUrl(ghReq)
        assertTrue(ghUrl.startsWith("https://github.com/login/oauth/authorize"))
        assertTrue(ghUrl.contains("code_challenge_method=S256"))
        assertTrue(ghUrl.contains("state="))
    }

    @Test fun callbackQueryEnforcesCsrfStateAndParsesErrors() {
        val ok = OAuthPkce.parseCallbackQuery("code=abc123&state=expected-state", "expected-state")
        assertTrue(ok is OAuthCallbackResult.Code)
        assertEquals("abc123", (ok as OAuthCallbackResult.Code).code)

        val csrf = OAuthPkce.parseCallbackQuery("code=abc123&state=wrong-state", "expected-state")
        assertTrue(csrf is OAuthCallbackResult.Error)

        val denied = OAuthPkce.parseCallbackQuery("error=access_denied&error_description=User+denied", "expected-state")
        assertTrue(denied is OAuthCallbackResult.Error)
        assertEquals("User denied", (denied as OAuthCallbackResult.Error).error)
    }

    @Test fun linkedAccountJsonSerializationRoundTrip() {
        val list = listOf(
            LinkedExternalAccount("google:123", OAuthProvider.Google, "123", "u@gmail.com", "User", 1700000000L),
            LinkedExternalAccount("github:456", OAuthProvider.GitHub, "456", "dev@gh.io", "Dev", 1700000001L),
        )
        val raw = LinkedExternalAccount.serializeList(list)
        assertEquals(list, LinkedExternalAccount.deserializeList(raw))
    }
}
