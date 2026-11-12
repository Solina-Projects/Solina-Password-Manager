package app.solina.passwordmanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.passwordmanager.sync.CloudProvider
import app.solina.passwordmanager.ui.theme.Solina

/** فهرست فضاهای ابریِ نصب‌شده روی گوشی؛ با انتخاب هر کدام انتخابگر پوشه باز می‌شود. */
@Composable
fun CloudProviderDialog(providers: List<CloudProvider>, onPick: (CloudProvider) -> Unit, onDismiss: () -> Unit) {
    val c = Solina.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.container,
        confirmButton = {
            Text("بستن", color = c.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp))
        },
        title = { Text("فضای ابری", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (providers.isEmpty()) {
                    Text(
                        "هیچ فضای ابری روی این گوشی پیدا نشد. برنامهٔ Google Drive، OneDrive یا Nextcloud را نصب و وارد حساب خود شوید، سپس دوباره امتحان کنید.",
                        color = c.muted, fontSize = 13.sp, lineHeight = 21.sp,
                    )
                } else {
                    Text(
                        "یکی را انتخاب کنید؛ در انتخابگر بعدی همان را از منوی کناری باز کنید و پوشه را انتخاب یا بسازید. فقط داده‌های رمزشده نوشته می‌شود.",
                        color = c.muted, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 8.dp),
                    )
                    for (p in providers) {
                        Text(p.label, color = c.text, fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 14.dp))
                    }
                }
                Text("اتصال مستقیم با حساب Google یا GitHub: به‌زودی", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        },
    )
}
