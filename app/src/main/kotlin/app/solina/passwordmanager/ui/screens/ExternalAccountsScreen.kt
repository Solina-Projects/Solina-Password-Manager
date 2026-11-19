package app.solina.passwordmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.passwordmanager.core.auth.LinkedExternalAccount
import app.solina.passwordmanager.core.auth.OAuthPkce
import app.solina.passwordmanager.core.auth.OAuthProvider
import app.solina.passwordmanager.ui.components.Card
import app.solina.passwordmanager.ui.components.IconAction
import app.solina.passwordmanager.ui.components.Mono
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons

@Composable
fun ExternalAccountsScreen(
    accounts: List<LinkedExternalAccount>,
    initialGoogleClientId: String,
    initialGitHubClientId: String,
    busy: Boolean,
    message: String?,
    onConnect: (OAuthProvider, String) -> Unit,
    onDisconnect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val c = Solina.colors
    val str = Solina.strings
    var googleClientId by remember { mutableStateOf(initialGoogleClientId) }
    var githubClientId by remember { mutableStateOf(initialGitHubClientId) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.statusBarsPadding().padding(top = 12.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(SolinaIcons.ChevronRight, str.cancel, tint = c.text, onClick = onBack)
            Text(str.externalAccountsTitle, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Card(background = c.primaryContainer) {
            Text(
                if (str.isFa) {
                    "اتصال حساب Google یا GitHub کاملاً اختیاری است و با OAuth 2.0 + PKCE (S256) انجام می‌شود. " +
                        "هیچ رمز عبور، کلید خزانه یا عبارت بازیابی به سرویس‌های خارجی ارسال نمی‌شود و قطع اتصال حساب، داده‌های خزانه را پاک نمی‌کند."
                } else {
                    "Linking Google or GitHub is optional and uses OAuth 2.0 + PKCE (S256). " +
                        "Master password, vault keys, and entries are never sent to external providers, and disconnecting never deletes local vault data."
                },
                color = c.onPrimaryContainer,
                fontSize = 13.sp,
                lineHeight = 22.sp,
            )
        }

        if (message != null) {
            Card(background = c.secondaryContainer) {
                Text(message, color = c.onSecondaryContainer, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }

        if (accounts.isNotEmpty()) {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (str.isFa) "حساب‌های متصل (${accounts.size})" else "Connected Accounts (${accounts.size})",
                        color = c.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    accounts.forEach { acc ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${acc.provider.displayName} · ${acc.displayName}", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                if (acc.email.isNotBlank()) Text(acc.email, color = c.muted, fontSize = 12.sp, fontFamily = Mono)
                            }
                            Text(
                                if (str.isFa) "قطع اتصال" else "Disconnect",
                                color = c.secondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(role = Role.Button) { onDisconnect(acc.id) }.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Google / Gmail (OIDC + PKCE)", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Field("OAuth Client ID (Google)", googleClientId, { googleClientId = it }, mono = true)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (!busy && googleClientId.isNotBlank()) c.primary else c.container)
                        .clickable(enabled = !busy && googleClientId.isNotBlank(), role = Role.Button) {
                            onConnect(OAuthProvider.Google, googleClientId)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (str.isFa) "ورود و اتصال با Google" else "Sign in with Google",
                        color = if (!busy && googleClientId.isNotBlank()) c.onPrimary else c.muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("GitHub (OAuth 2.0 + PKCE)", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Field("OAuth Client ID (GitHub)", githubClientId, { githubClientId = it }, mono = true)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (!busy && githubClientId.isNotBlank()) c.primary else c.container)
                        .clickable(enabled = !busy && githubClientId.isNotBlank(), role = Role.Button) {
                            onConnect(OAuthProvider.GitHub, githubClientId)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (str.isFa) "ورود و اتصال با GitHub" else "Sign in with GitHub",
                        color = if (!busy && githubClientId.isNotBlank()) c.onPrimary else c.muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Redirect URI (RFC 8252)", color = c.muted, fontSize = 12.sp)
                Text(OAuthPkce.REDIRECT_URI, color = c.text, fontSize = 13.sp, fontFamily = Mono)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
