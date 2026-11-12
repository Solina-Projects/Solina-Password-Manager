package app.solina.passwordmanager.sync

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract

data class CloudProvider(val authority: String, val label: String)

/**
 * فضاهای ابری موجود روی گوشی = ارائه‌دهندگان فایل (DocumentsProvider) نصب‌شده، مثل Google Drive، OneDrive، Nextcloud.
 * حافظهٔ داخلی، دانلودها و رسانه (ارائه‌دهندگان خود سیستم) در بخش «ذخیره‌سازی» می‌آیند.
 */
fun listCloudProviders(context: Context): List<CloudProvider> {
    val pm = context.packageManager
    val found = runCatching {
        pm.queryIntentContentProviders(Intent(DocumentsContract.PROVIDER_INTERFACE), 0)
    }.getOrDefault(emptyList())
    return found
        .mapNotNull { ri ->
            val info = ri.providerInfo ?: return@mapNotNull null
            val authority = info.authority?.substringBefore(';') ?: return@mapNotNull null
            if (info.packageName == context.packageName) return@mapNotNull null
            if (authority.startsWith("com.android.") || authority.startsWith("com.google.android.documentsui")) return@mapNotNull null
            CloudProvider(authority, ri.loadLabel(pm).toString().ifBlank { info.packageName })
        }
        .distinctBy { it.authority }
        .sortedBy { it.label }
}
