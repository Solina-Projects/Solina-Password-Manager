package app.solina.passwordmanager

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.WindowManager
import android.view.autofill.AutofillManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import app.solina.passwordmanager.auth.OAuthLinkResult
import app.solina.passwordmanager.core.crypto.AuthenticationFailedException
import app.solina.passwordmanager.core.crypto.CorruptVaultException
import app.solina.passwordmanager.core.crypto.CreatedVault
import app.solina.passwordmanager.core.crypto.InvalidRecoveryPhraseException
import app.solina.passwordmanager.core.crypto.VaultCrypto
import app.solina.passwordmanager.core.crypto.VaultHeader
import app.solina.passwordmanager.core.security.KdfCalibrator
import app.solina.passwordmanager.core.backup.Backup
import app.solina.passwordmanager.core.backup.BackupFormatException
import app.solina.passwordmanager.core.backup.BackupHeaderInfo
import app.solina.passwordmanager.core.fa
import app.solina.passwordmanager.core.importer.ImportException
import app.solina.passwordmanager.core.importer.Importers
import app.solina.passwordmanager.core.otp.OtpImport
import app.solina.passwordmanager.core.storage.HistoryEntry
import app.solina.passwordmanager.core.storage.ItemPayload
import app.solina.passwordmanager.core.storage.SecurityAnalyzer
import app.solina.passwordmanager.sync.ConnectResult
import app.solina.passwordmanager.sync.StageJoinResult
import app.solina.passwordmanager.sync.StagedJoin
import app.solina.passwordmanager.sync.SyncManager
import app.solina.passwordmanager.ui.components.SIcon
import app.solina.passwordmanager.ui.components.copySensitive
import app.solina.passwordmanager.ui.screens.BiometricOfferScreen
import app.solina.passwordmanager.ui.screens.ChangeMasterPasswordScreen
import app.solina.passwordmanager.ui.screens.CodesScreen
import app.solina.passwordmanager.ui.screens.ConfirmWordsScreen
import app.solina.passwordmanager.ui.screens.CreatePasswordScreen
import app.solina.passwordmanager.ui.screens.DataScreen
import app.solina.passwordmanager.ui.screens.DataStep
import app.solina.passwordmanager.ui.screens.DetailScreen
import app.solina.passwordmanager.ui.screens.DiagnosticsScreen
import app.solina.passwordmanager.ui.screens.EditScreen
import app.solina.passwordmanager.ui.screens.ExternalAccountsScreen
import app.solina.passwordmanager.ui.screens.GeneratorScreen
import app.solina.passwordmanager.ui.screens.HomeScreen
import app.solina.passwordmanager.ui.screens.RecoveryEntryScreen
import app.solina.passwordmanager.ui.screens.RecoveryWordsScreen
import app.solina.passwordmanager.ui.screens.ResetPasswordScreen
import app.solina.passwordmanager.ui.screens.ScanScreen
import app.solina.passwordmanager.ui.screens.SettingsScreen
import app.solina.passwordmanager.ui.screens.VaultScreen
import app.solina.passwordmanager.ui.screens.VaultUnlockScreen
import app.solina.passwordmanager.ui.screens.WebDavSetupScreen
import app.solina.passwordmanager.ui.screens.LegacyBackupRestoreScreen
import app.solina.passwordmanager.ui.screens.WelcomeRestoreBackupScreen
import app.solina.passwordmanager.ui.screens.WelcomeScreen
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons
import app.solina.passwordmanager.ui.theme.SolinaTheme
import app.solina.passwordmanager.ui.viewmodel.SolinaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    companion object {
        /** وقتی نوتیف خطا زده شد، با این extra باز می‌شود تا مستقیم گزارش عیب‌یابی نشان داده شود. */
        const val EXTRA_SHOW_DIAGNOSTICS = "solina_show_diagnostics"

        const val RC_JOIN_FOLDER = 1001
        const val RC_CONNECT_FOLDER = 1002
        const val RC_EXPORT_FILE = 1003
        const val RC_RESTORE_FILE = 1004
        const val RC_IMPORT_FILE = 1005
        const val RC_NOTIFICATION = 1006
        const val RC_CAMERA = 1007
        const val RC_GALLERY_IMAGE = 1008
        const val RC_WELCOME_RESTORE_FILE = 1009
    }

    private val container get() = (application as SolinaApplication).container
    private val _pendingOAuthCallback = MutableStateFlow<Uri?>(null)
    val pendingOAuthCallback = _pendingOAuthCallback.asStateFlow()

    fun consumeOAuthCallback() {
        _pendingOAuthCallback.value = null
    }

    private fun captureOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme.equals("solina-auth", ignoreCase = true) && data.host.equals("oauth", ignoreCase = true)) {
            _pendingOAuthCallback.value = data
        }
    }

    /** نتیجهٔ startActivityForResult را به تابعی که هنگام شروع ثبت شد می‌رساند. */
    private val activityResultHandlers = mutableMapOf<Int, (resultCode: Int, data: Intent?) -> Unit>()
    private val permissionHandlers = mutableMapOf<Int, (granted: Boolean) -> Unit>()

    @Suppress("DEPRECATION")
    fun launchForResult(intent: Intent, requestCode: Int, handler: (resultCode: Int, data: Intent?) -> Unit, onFail: () -> Unit) {
        try {
            DiagLog.log("PICKER", "launching picker")
            activityResultHandlers[requestCode] = handler
            startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            activityResultHandlers.remove(requestCode)
            DiagLog.logError("PICKER", e, "launchForResult")
            onFail()
        }
    }

    fun requestPermissionOnce(permission: String, requestCode: Int, handler: (granted: Boolean) -> Unit, onFail: () -> Unit = {}) {
        try {
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                handler(true)
                return
            }
            permissionHandlers[requestCode] = handler
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        } catch (e: Exception) {
            permissionHandlers.remove(requestCode)
            DiagLog.logError("PERM", e, "requestPermissionOnce")
            onFail()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        activityResultHandlers.remove(requestCode)?.invoke(resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHandlers.remove(requestCode)?.invoke(
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureOAuthIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // جلوگیری از اسکرین‌شات و نمایش محتوا در پیش‌نمایش اپ‌های اخیر.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        captureOAuthIntent(intent)
        val auth = AuthController(container)
        val viewModel = ViewModelProvider(this)[SolinaViewModel::class.java]
        DiagLog.log("ACT", "onCreate")
        setContent {
            val themeMode by container.securityPrefs.themeMode.collectAsState()
            val language by container.securityPrefs.language.collectAsState()
            SolinaTheme(themeMode = themeMode, language = language) {
                var showDiag by remember { mutableStateOf(intent.getBooleanExtra(EXTRA_SHOW_DIAGNOSTICS, false)) }
                Box(Modifier.fillMaxSize().background(Solina.colors.bg)) {
                    if (showDiag) {
                        DiagScreenHost(this@MainActivity) { showDiag = false }
                    } else {
                        Root(this@MainActivity, container, auth, viewModel, onDiagnostics = { showDiag = true })
                        CrashBanner(this@MainActivity) { showDiag = true }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        DiagLog.setForeground(false)
        DiagLog.log("ACT", "onStop")
        container.scheduleAutoLock()
    }

    override fun onStart() {
        super.onStart()
        DiagLog.setForeground(true)
        DiagLog.log("ACT", "onStart")
        container.cancelAutoLock()
        container.session.onForegrounded(container.securityPrefs.autoLockMillis())
    }
}

/** اگر دفعهٔ قبل برنامه ناگهان بسته شده، گزارش (بدون داده‌های شخصی) را برای کپی نشان می‌دهد. */
@Composable
private fun CrashBanner(activity: FragmentActivity, onOpenLog: () -> Unit) {
    val c = Solina.colors
    var report by remember { mutableStateOf(CrashReporter.read(activity)) }
    val text = report ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.secondaryContainer)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("دفعهٔ قبل برنامه ناگهان بسته شد", color = c.onSecondaryContainer, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "برای رفع اشکال، گزارش را کپی کنید و بفرستید. گزارش فقط نام کلاس‌ها و خطوط کد است، بدون رمز و داده‌های شما.",
                color = c.onSecondaryContainer, fontSize = 13.sp, lineHeight = 20.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "کپی گزارش",
                    color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { copySensitive(activity, "crash", text, 120_000L) }.padding(8.dp),
                )
                Text(
                    "گزارش کامل",
                    color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onOpenLog() }.padding(8.dp),
                )
                Text(
                    "بستن",
                    color = c.onSecondaryContainer, fontSize = 14.sp,
                    modifier = Modifier.clickable { CrashReporter.clear(activity); report = null }.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun DiagScreenHost(activity: FragmentActivity, onBack: () -> Unit) {
    var text by remember { mutableStateOf(buildLog(activity)) }
    BackHandler { onBack() }
    DiagnosticsScreen(
        text = text,
        onBack = onBack,
        onCopy = { copySensitive(activity, "solina-diagnostics", text, 120_000L) },
        onShare = {
            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
            runCatching { activity.startActivity(Intent.createChooser(send, "ارسال گزارش")) }
        },
        onClear = { DiagLog.clear(); CrashReporter.clear(activity); text = buildLog(activity) },
    )
}

private fun buildLog(context: android.content.Context): String {
    val head = "Solina ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()} | Android ${android.os.Build.VERSION.SDK_INT} | ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n\n"
    val crash = CrashReporter.read(context)?.let { "== آخرین خطا ==\n$it\n" }.orEmpty()
    return head + crash + "== رویدادها ==\n" + DiagLog.read().takeLast(20_000)
}

/** آیا روی این دستگاه برنامه‌ای برای انتخاب پوشه (ACTION_OPEN_DOCUMENT_TREE) هست؟ */
@Suppress("DEPRECATION")
private fun hasFolderPicker(activity: FragmentActivity): Boolean {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    return activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
}

// ساخت Intent انتخاب فایل برای هر پسوند (از جمله .slnb).
// افزودن EXTRA_MIME_TYPES با wildcard روی برخی رام‌های اندروید (MIUI/OneUI/EMUI)
// باعث غیرفعال شدن فایل‌های با پسوند ناشناخته مانند .slnb می‌شود؛ بنابراین فقط setType تنظیم می‌شود.
@Suppress("DEPRECATION")
private fun buildOpenAnyFileIntent(activity: FragmentActivity): Intent {
    val openDoc = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")
    if (activity.packageManager.resolveActivity(openDoc, PackageManager.MATCH_DEFAULT_ONLY) != null) {
        return openDoc
    }
    return Intent(Intent.ACTION_GET_CONTENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")
}

private fun readLimitedUri(resolver: android.content.ContentResolver, uri: Uri, max: Int): ByteArray? =
    resolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) return null
        }
        out.toByteArray()
    }

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String =
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull().orEmpty()

private fun writeBytesSafe(resolver: android.content.ContentResolver, uri: Uri, bytes: ByteArray): Boolean =
    runCatching {
        val stream = runCatching { resolver.openOutputStream(uri, "w") }.getOrNull()
            ?: runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri)
            ?: return@runCatching false
        stream.use { out ->
            out.write(bytes)
            out.flush()
        }
        true
    }.getOrDefault(false)

private const val NO_FOLDER_PICKER_MESSAGE =
    "روی این دستگاه برنامه‌ای برای انتخاب پوشه نیست (Files / DocumentsUI). " +
        "آن را فعال کنید یا «Files by Google» را نصب کنید، بعد دوباره بزنید."

private enum class Stage { Welcome, CreatePassword, ShowWords, ConfirmWords }

private enum class UnlockMode { Password, Recovery }

internal fun waitText(millis: Long): String {
    val seconds = ((millis + 999) / 1000).toInt()
    return if (seconds >= 60) "${((seconds + 59) / 60).fa()} دقیقه" else "${seconds.fa()} ثانیه"
}

internal fun messageFor(result: UnlockResult): String? = when (result) {
    UnlockResult.Success -> null
    UnlockResult.WrongPassword -> "رمز اصلی درست نیست."
    UnlockResult.InvalidPhrase -> "عبارت بازیابی معتبر نیست. تعداد کلمات و املای آن‌ها را بررسی کنید."
    UnlockResult.Corrupt -> "فایل خزانه خراب یا دست‌کاری شده است."
    is UnlockResult.Wait -> "به‌خاطر تلاش‌های ناموفق، ${waitText(result.millis)} صبر کنید و دوباره امتحان کنید."
}

@Composable
private fun Root(
    activity: MainActivity,
    container: AppContainer,
    auth: AuthController,
    viewModel: SolinaViewModel,
    onDiagnostics: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val unlocked by container.session.unlocked.collectAsState()

    LaunchedEffect(unlocked) {
        if (unlocked) runCatching { container.repository.load() } else container.repository.clear()
    }

    var hasVault by remember { mutableStateOf(auth.hasVault()) }
    var stage by remember { mutableStateOf(Stage.Welcome) }
    var created by remember { mutableStateOf<CreatedVault?>(null) }
    var offerBiometric by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    var mode by remember { mutableStateOf(UnlockMode.Password) }
    var message by remember { mutableStateOf<String?>(null) }
    var mustReset by remember { mutableStateOf(false) }
    var joinMessage by remember { mutableStateOf<String?>(null) }
    var showJoinCloud by remember { mutableStateOf(false) }
    var showJoinWebDav by remember { mutableStateOf(false) }
    var webDavJoinBusy by remember { mutableStateOf(false) }
    var webDavJoinMsg by remember { mutableStateOf<String?>(null) }
    var stagedJoin by remember { mutableStateOf<StagedJoin?>(null) }
    var welcomeRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var welcomeRestoreInfo by remember { mutableStateOf<BackupHeaderInfo?>(null) }
    var welcomeRestoreName by remember { mutableStateOf("") }
    var welcomeRestoreBusy by remember { mutableStateOf(false) }
    var welcomeRestoreMsg by remember { mutableStateOf<String?>(null) }
    var pendingRestoredPayloads by remember { mutableStateOf<List<ItemPayload>>(emptyList()) }
    var stagedBackupHeader by remember { mutableStateOf<VaultHeader?>(null) }
    var stagedBackupRecoveryMode by remember { mutableStateOf(false) }
    var legacyBackupPw by remember { mutableStateOf<CharArray?>(null) }
    var legacyBackupBusy by remember { mutableStateOf(false) }
    var legacyBackupMsg by remember { mutableStateOf<String?>(null) }

    fun launchWelcomeRestoreFile() {
        joinMessage = null
        activity.launchForResult(
            buildOpenAnyFileIntent(activity),
            MainActivity.RC_WELCOME_RESTORE_FILE,
            handler = { resultCode, data ->
                val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
                if (uri != null) {
                    welcomeRestoreBusy = true
                    scope.launch {
                        try {
                            val (bytes, name) = withContext(Dispatchers.IO) {
                                readLimitedUri(activity.contentResolver, uri, 64 * 1024 * 1024) to
                                    queryDisplayName(activity.contentResolver, uri)
                            }
                            if (bytes == null) {
                                joinMessage = "فایل بکاپ خوانده نشد یا بیش از حد بزرگ است."
                            } else {
                                val info = Backup.inspect(bytes)
                                welcomeRestoreBytes = bytes
                                welcomeRestoreInfo = info
                                welcomeRestoreName = name
                                welcomeRestoreMsg = null
                            }
                        } catch (e: BackupFormatException) {
                            joinMessage = e.message ?: "این فایل یک بکاپ معتبر Solina (.slnb) نیست."
                        } catch (e: Exception) {
                            DiagLog.logError("RESTORE", e, "launchWelcomeRestoreFile")
                            joinMessage = "خواندن فایل بکاپ ناموفق بود."
                        } finally {
                            welcomeRestoreBusy = false
                        }
                    }
                }
            },
            onFail = { joinMessage = "انتخاب‌گر فایل روی این دستگاه در دسترس نیست." },
        )
    }

    fun launchJoinFolder(kind: String) {
        if (!hasFolderPicker(activity)) {
            DiagLog.log("PICKER", "no handler for ACTION_OPEN_DOCUMENT_TREE")
            joinMessage = NO_FOLDER_PICKER_MESSAGE
            return
        }
        activity.launchForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
            MainActivity.RC_JOIN_FOLDER,
            handler = { resultCode, data ->
                val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
                DiagLog.log("JOIN", "picker returned: ${if (uri == null) "cancelled" else "uri"}")
                if (uri != null) scope.launch {
                    when (val r = container.sync.stageJoinFromFolder(uri, kind)) {
                        is StageJoinResult.Ready -> {
                            message = null
                            stagedJoin = r.staged
                        }
                        is StageJoinResult.BackupFilesFound -> joinMessage = "در این پوشه فایل بکاپ (.slnb) وجود دارد، نه پوشهٔ همگام‌سازی Solina-Sync. برای بازیابی آن، گزینهٔ «بازیابی از فایل بکاپ (.slnb)» را بزنید و مستقیم فایل را انتخاب کنید."
                        StageJoinResult.NoVaultInFolder -> joinMessage = "در این پوشه خزانه‌ای پیدا نشد. پوشهٔ همگام‌سازی «Solina-Sync» (یا پوشهٔ والد آن) را انتخاب کنید، یا برای فایل‌های .slnb گزینهٔ «بازیابی از فایل بکاپ (.slnb)» را بزنید."
                        is StageJoinResult.Failed -> joinMessage = r.reason
                    }
                }
            },
            onFail = { joinMessage = "انتخاب‌گر پوشه روی این دستگاه در دسترس نیست." },
        )
    }

    when {
        offerBiometric && unlocked -> BiometricOfferScreen(
            onEnable = { auth.enableBiometric(activity) { offerBiometric = false } },
            onSkip = { offerBiometric = false },
        )

        !hasVault && stagedJoin != null -> {
            val staged = stagedJoin!!
            VaultUnlockScreen(
                biometricReady = false,
                message = message,
                busy = busy,
                subtitle = "برای پیوستن به خزانهٔ «${staged.folderName}» رمز اصلی را وارد کنید",
                onPassword = { password ->
                    busy = true
                    message = null
                    scope.launch {
                        val ok = container.sync.commitStagedJoin(staged, password)
                        password.fill(' ')
                        busy = false
                        if (ok) {
                            stagedJoin = null
                            hasVault = true
                        } else {
                            message = "رمز اصلی با خزانهٔ این پوشه مطابقت ندارد."
                        }
                    }
                },
                onBiometric = {},
                onForgot = {},
                onCancel = {
                    stagedJoin = null
                    message = null
                },
            )
        }

        !hasVault && stagedBackupHeader != null -> {
            val header = stagedBackupHeader!!
            if (!stagedBackupRecoveryMode) {
                VaultUnlockScreen(
                    biometricReady = false,
                    message = message,
                    busy = busy,
                    subtitle = "فایل بکاپ باز شد (${pendingRestoredPayloads.size.fa()} ورودی). برای حفظ رمز و ۲۴ کلمهٔ قبلی، رمز اصلی خزانهٔ قبلی را وارد کنید.",
                    onPassword = { masterPw ->
                        busy = true
                        message = null
                        scope.launch {
                            val vk = withContext(Dispatchers.Default) {
                                runCatching { VaultCrypto.unlockWithPassword(header, masterPw) }.getOrNull()
                            }
                            masterPw.fill('\u0000')
                            if (vk != null) {
                                val toImport = pendingRestoredPayloads
                                auth.restoreExistingVault(header, vk)
                                stagedBackupHeader = null
                                pendingRestoredPayloads = emptyList()
                                hasVault = true
                                runCatching { container.repository.importItems(toImport) }
                                busy = false
                            } else {
                                busy = false
                                message = "رمز اصلی با خزانهٔ قبلی مطابقت ندارد. می‌توانید با ۲۴ کلمهٔ قبلی نیز باز کنید."
                            }
                        }
                    },
                    onBiometric = {},
                    onForgot = { message = null; stagedBackupRecoveryMode = true },
                    onCancel = {
                        stagedBackupHeader = null
                        stagedBackupRecoveryMode = false
                        pendingRestoredPayloads = emptyList()
                        message = null
                    },
                )
            } else {
                RecoveryEntryScreen(
                    message = message,
                    busy = busy,
                    onSubmit = { phrase ->
                        busy = true
                        message = null
                        scope.launch {
                            val res = withContext(Dispatchers.Default) {
                                try {
                                    Result.success(VaultCrypto.unlockWithRecovery(header, phrase))
                                } catch (e: InvalidRecoveryPhraseException) {
                                    Result.failure(e)
                                } catch (e: AuthenticationFailedException) {
                                    Result.failure(e)
                                }
                            }
                            val vk = res.getOrNull()
                            if (vk != null) {
                                val toImport = pendingRestoredPayloads
                                auth.restoreExistingVault(header, vk)
                                stagedBackupHeader = null
                                stagedBackupRecoveryMode = false
                                pendingRestoredPayloads = emptyList()
                                mustReset = true
                                hasVault = true
                                runCatching { container.repository.importItems(toImport) }
                                busy = false
                            } else {
                                busy = false
                                message = if (res.exceptionOrNull() is InvalidRecoveryPhraseException) {
                                    "عبارت بازیابی معتبر نیست. تعداد کلمات و املای آن‌ها را بررسی کنید."
                                } else {
                                    "این ۲۴ کلمه با خزانهٔ این بکاپ مطابقت ندارد."
                                }
                            }
                        }
                    },
                    onBack = { message = null; stagedBackupRecoveryMode = false },
                )
            }
        }

        !hasVault && legacyBackupPw != null -> {
            BackHandler {
                legacyBackupPw?.fill('\u0000')
                legacyBackupPw = null
                pendingRestoredPayloads = emptyList()
                legacyBackupMsg = null
            }
            LegacyBackupRestoreScreen(
                itemCount = pendingRestoredPayloads.size,
                busy = legacyBackupBusy,
                message = legacyBackupMsg,
                onRestoreWithExistingWords = { phrase, customPw ->
                    val usePw = customPw ?: legacyBackupPw?.copyOf() ?: return@LegacyBackupRestoreScreen
                    legacyBackupBusy = true
                    legacyBackupMsg = null
                    scope.launch {
                        val res = auth.createWithExistingRecovery(usePw, phrase)
                        usePw.fill('\u0000')
                        if (res == UnlockResult.Success) {
                            val toImport = pendingRestoredPayloads
                            legacyBackupPw?.fill('\u0000')
                            legacyBackupPw = null
                            pendingRestoredPayloads = emptyList()
                            hasVault = true
                            runCatching { container.repository.importItems(toImport) }
                            legacyBackupBusy = false
                        } else {
                            legacyBackupBusy = false
                            legacyBackupMsg = "عبارت بازیابی ۲۴ کلمه‌ای معتبر نیست. املای کلمات انگلیسی و ترتیب آن‌ها را بررسی کنید."
                        }
                    }
                },
                onQuickRestoreWithBackupPassword = {
                    val usePw = legacyBackupPw?.copyOf() ?: return@LegacyBackupRestoreScreen
                    legacyBackupBusy = true
                    legacyBackupMsg = null
                    scope.launch {
                        created = auth.create(usePw)
                        usePw.fill('\u0000')
                        legacyBackupPw?.fill('\u0000')
                        legacyBackupPw = null
                        legacyBackupBusy = false
                        stage = Stage.ShowWords
                    }
                },
                onCancel = {
                    legacyBackupPw?.fill('\u0000')
                    legacyBackupPw = null
                    pendingRestoredPayloads = emptyList()
                    legacyBackupMsg = null
                },
            )
        }

        !hasVault && welcomeRestoreBytes != null && welcomeRestoreInfo != null -> {
            BackHandler {
                welcomeRestoreBytes?.fill(0)
                welcomeRestoreBytes = null
                welcomeRestoreInfo = null
                welcomeRestoreMsg = null
            }
            WelcomeRestoreBackupScreen(
                fileName = welcomeRestoreName,
                headerInfo = welcomeRestoreInfo!!,
                busy = welcomeRestoreBusy,
                message = welcomeRestoreMsg,
                onSubmit = { pw ->
                    val bytes = welcomeRestoreBytes ?: return@WelcomeRestoreBackupScreen
                    welcomeRestoreBusy = true
                    welcomeRestoreMsg = null
                    scope.launch {
                        try {
                            val decoded = withContext(Dispatchers.Default) { Backup.decodeFull(bytes, pw) }
                            bytes.fill(0)
                            welcomeRestoreBytes = null
                            welcomeRestoreInfo = null
                            val embeddedHeader = decoded.vaultHeader?.let { vhBytes ->
                                runCatching { VaultHeader.decode(vhBytes) }.getOrNull()
                            }
                            if (embeddedHeader != null) {
                                // ابتدا با همان رمز فایل بکاپ امتحان می‌کنیم: اگر برابر رمز اصلی باشد، مستقیم و در یک مرحله بازیابی می‌شود!
                                val directVk = withContext(Dispatchers.Default) {
                                    runCatching { VaultCrypto.unlockWithPassword(embeddedHeader, pw) }.getOrNull()
                                }
                                if (directVk != null) {
                                    auth.restoreExistingVault(embeddedHeader, directVk)
                                    hasVault = true
                                    runCatching { container.repository.importItems(decoded.items) }
                                } else {
                                    // رمز بکاپ با رمز اصلی فرق داشته: فقط رمز اصلی قبلی (یا ۲۴ کلمهٔ قبلی) را می‌پرسیم، بدون ساخت رمز/کلمات جدید!
                                    pendingRestoredPayloads = decoded.items
                                    stagedBackupHeader = embeddedHeader
                                    stagedBackupRecoveryMode = false
                                    message = null
                                }
                            } else {
                                // فایل بکاپ قدیمی بدون هدر خزانه: اجازه می‌دهیم ۲۴ کلمهٔ قبلی و همان رمز را حفظ کند (بدون اجبار به ساخت جدید)
                                pendingRestoredPayloads = decoded.items
                                legacyBackupPw = pw.copyOf()
                                legacyBackupMsg = null
                            }
                        } catch (e: AuthenticationFailedException) {
                            welcomeRestoreMsg = "رمز فایل بکاپ درست نیست یا فایل دست‌کاری شده است."
                        } catch (e: BackupFormatException) {
                            welcomeRestoreMsg = e.message ?: "ساختار بکاپ معتبر نیست."
                        } catch (e: CorruptVaultException) {
                            welcomeRestoreMsg = "هدر خزانه درون فایل بکاپ آسیب دیده است."
                        } catch (e: Exception) {
                            DiagLog.logError("RESTORE", e, "welcomeRestoreDecode")
                            welcomeRestoreMsg = "بازگشایی فایل بکاپ ناموفق بود."
                        } finally {
                            pw.fill('\u0000')
                            welcomeRestoreBusy = false
                        }
                    }
                },
                onCancel = {
                    welcomeRestoreBytes?.fill(0)
                    welcomeRestoreBytes = null
                    welcomeRestoreInfo = null
                    welcomeRestoreMsg = null
                },
            )
        }

        !hasVault && showJoinWebDav -> {
            BackHandler { showJoinWebDav = false; webDavJoinMsg = null }
            WebDavSetupScreen(
                isJoin = true,
                busy = webDavJoinBusy,
                message = webDavJoinMsg,
                onSubmit = { url, user, pass ->
                    webDavJoinBusy = true
                    webDavJoinMsg = null
                    scope.launch {
                        when (val r = container.sync.stageJoinFromWebDav(url, user, pass)) {
                            is StageJoinResult.Ready -> {
                                showJoinWebDav = false
                                message = null
                                stagedJoin = r.staged
                            }
                            StageJoinResult.NoVaultInFolder, is StageJoinResult.BackupFilesFound -> webDavJoinMsg = "در این مسیر WebDAV خزانه‌ای پیدا نشد."
                            is StageJoinResult.Failed -> webDavJoinMsg = r.reason
                        }
                        webDavJoinBusy = false
                    }
                },
                onBack = { showJoinWebDav = false; webDavJoinMsg = null },
            )
        }

        !hasVault -> when (stage) {
            Stage.Welcome -> {
                WelcomeScreen(
                    message = joinMessage,
                    busy = welcomeRestoreBusy,
                    onCreate = { joinMessage = null; pendingRestoredPayloads = emptyList(); stage = Stage.CreatePassword },
                    onRestoreBackupFile = { launchWelcomeRestoreFile() },
                    onDiagnostics = onDiagnostics,
                    onJoinCloud = { joinMessage = null; DiagLog.log("JOIN", "cloud button tapped"); showJoinCloud = true },
                    onJoinStorage = { joinMessage = null; DiagLog.log("JOIN", "storage button tapped"); launchJoinFolder("storage") },
                    onJoinWebDav = { joinMessage = null; webDavJoinMsg = null; showJoinWebDav = true },
                )
                if (showJoinCloud) {
                    val providers = remember { app.solina.passwordmanager.sync.listCloudProviders(activity) }
                    app.solina.passwordmanager.ui.screens.CloudProviderDialog(
                        providers = providers,
                        onPick = { showJoinCloud = false; launchJoinFolder("cloud") },
                        onDismiss = { showJoinCloud = false },
                    )
                }
            }
            Stage.CreatePassword -> CreatePasswordScreen(busy, restoreCount = pendingRestoredPayloads.size) { password ->
                busy = true
                scope.launch {
                    created = auth.create(password)
                    password.fill('\u0000')
                    busy = false
                    stage = Stage.ShowWords
                }
            }
            Stage.ShowWords -> RecoveryWordsScreen(created?.recoveryWords.orEmpty()) { stage = Stage.ConfirmWords }
            Stage.ConfirmWords -> ConfirmWordsScreen(
                words = created?.recoveryWords.orEmpty(),
                onConfirmed = {
                    created?.let { vault ->
                        auth.commit(vault)
                        created = null
                        val toRestore = pendingRestoredPayloads
                        pendingRestoredPayloads = emptyList()
                        hasVault = true
                        if (toRestore.isNotEmpty()) {
                            scope.launch {
                                runCatching { container.repository.importItems(toRestore) }
                            }
                        }
                        offerBiometric = auth.biometricAvailable(activity)
                    }
                },
                onBack = { stage = Stage.ShowWords },
            )
        }

        unlocked && mustReset -> ResetPasswordScreen(busy) { password ->
            busy = true
            scope.launch {
                val ok = auth.setNewPassword(password)
                password.fill('\u0000')
                busy = false
                if (ok) {
                    mustReset = false
                    container.sync.pushHeader()
                    auth.disableBiometric()
                } else {
                    container.session.lock()
                    mustReset = false
                }
            }
        }

        !unlocked -> when (mode) {
            UnlockMode.Password -> {
                val biometricReady = auth.hasBiometric()
                LaunchedEffect(Unit) {
                    if (auth.hasBiometric()) {
                        delay(300)
                        auth.unlockWithBiometric(activity) { }
                    }
                }
                VaultUnlockScreen(
                    biometricReady = biometricReady,
                    message = message,
                    busy = busy,
                    onPassword = { password ->
                        busy = true
                        message = null
                        scope.launch {
                            val result = auth.unlockWithPassword(password)
                            password.fill('\u0000')
                            busy = false
                            message = messageFor(result)
                        }
                    },
                    onBiometric = { auth.unlockWithBiometric(activity) { ok -> if (!ok) message = "با اثر انگشت باز نشد. از رمز اصلی استفاده کنید." } },
                    onForgot = { message = null; mode = UnlockMode.Recovery },
                )
            }

            UnlockMode.Recovery -> RecoveryEntryScreen(
                message = message,
                busy = busy,
                onSubmit = { phrase ->
                    busy = true
                    message = null
                    mustReset = true
                    scope.launch {
                        val result = auth.unlockWithRecovery(phrase)
                        busy = false
                        message = messageFor(result)
                        if (result != UnlockResult.Success) mustReset = false else mode = UnlockMode.Password
                    }
                },
                onBack = { message = null; mode = UnlockMode.Password },
            )
        }

        else -> MainApp(
            activity = activity,
            container = container,
            auth = auth,
            viewModel = viewModel,
            onDiagnostics = onDiagnostics,
            onVaultWiped = {
                viewModel.clearBreachState()
                hasVault = false
                stage = Stage.Welcome
                message = null
                joinMessage = "خزانهٔ محلی به‌طور امن پاک‌سازی شد."
            },
        )
    }
}

private enum class Tab(val icon: ImageVector) {
    Home(SolinaIcons.Home),
    Vault(SolinaIcons.Lock),
    Codes(SolinaIcons.Clock),
    Settings(SolinaIcons.Sliders),
}

@Composable
private fun MainApp(
    activity: MainActivity,
    container: AppContainer,
    auth: AuthController,
    viewModel: SolinaViewModel,
    onDiagnostics: () -> Unit,
    onVaultWiped: () -> Unit,
) {
    val c = Solina.colors
    val scope = rememberCoroutineScope()
    val repo = container.repository
    val items by repo.items.collectAsState()
    val unreadable by repo.unreadable.collectAsState()
    val report = remember(items) { SecurityAnalyzer.analyze(items) }
    val breachState by viewModel.breachState.collectAsState()
    val externalAccounts by container.externalAccounts.accounts.collectAsState()
    val pendingOAuthUri by activity.pendingOAuthCallback.collectAsState()

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val autoLock by container.securityPrefs.autoLock.collectAsState()
    val clipboardClear by container.securityPrefs.clipboardClear.collectAsState()
    val themeMode by container.securityPrefs.themeMode.collectAsState()
    val language by container.securityPrefs.language.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var generator by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var showExternalAccounts by remember { mutableStateOf(false) }
    var oauthBusy by remember { mutableStateOf(false) }
    var oauthMessage by remember { mutableStateOf<String?>(null) }
    var showWebDavConnect by remember { mutableStateOf(false) }
    var webDavBusy by remember { mutableStateOf(false) }
    var webDavMessage by remember { mutableStateOf<String?>(null) }
    var showChangePassword by remember { mutableStateOf(false) }
    var changePwBusy by remember { mutableStateOf(false) }
    var changePwMessage by remember { mutableStateOf<String?>(null) }

    val syncStatus by container.sync.status.collectAsState()
    val localRev by repo.localChanges.collectAsState()
    var showConnectCloud by remember { mutableStateOf(false) }

    LaunchedEffect(pendingOAuthUri) {
        val uri = pendingOAuthUri ?: return@LaunchedEffect
        activity.consumeOAuthCallback()
        oauthBusy = true
        oauthMessage = null
        showExternalAccounts = true
        when (val res = container.externalAccounts.completeCallback(uri)) {
            is OAuthLinkResult.Linked -> {
                oauthMessage = null
                toast = "حساب ${res.account.provider.displayName} متصل شد"
            }
            is OAuthLinkResult.Failed -> {
                oauthMessage = res.reason
            }
        }
        oauthBusy = false
    }

    fun launchConnectFolder(kind: String) {
        if (!hasFolderPicker(activity)) {
            DiagLog.log("PICKER", "no handler for ACTION_OPEN_DOCUMENT_TREE")
            toast = NO_FOLDER_PICKER_MESSAGE
            return
        }
        activity.launchForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
            MainActivity.RC_CONNECT_FOLDER,
            handler = { resultCode, data ->
                val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
                if (uri != null) scope.launch {
                    when (val r = container.sync.connect(uri, kind)) {
                        ConnectResult.Ok -> { toast = "متصل شد؛ در حال همگام‌سازی…"; container.sync.syncNow() }
                        ConnectResult.DifferentVault -> toast = "این پوشه خزانهٔ دیگری دارد؛ وصل نشد"
                        ConnectResult.NoVaultInFolder -> toast = "خزانه‌ای در پوشه نیست"
                        is ConnectResult.Failed -> toast = r.reason
                    }
                }
            },
            onFail = { toast = "انتخاب‌گر پوشه روی این دستگاه در دسترس نیست" },
        )
    }

    LaunchedEffect(Unit) { container.sync.syncIfDue(app.solina.passwordmanager.core.sync.BackupTrigger.Unlock) }
    LaunchedEffect(localRev) {
        if (localRev > 0) {
            delay(1500)
            container.sync.syncIfDue(app.solina.passwordmanager.core.sync.BackupTrigger.Change)
        }
    }

    var dataStep by remember { mutableStateOf<DataStep?>(null) }
    var dataBusy by remember { mutableStateOf(false) }
    var dataMessage by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRestore by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRestoreInfo by remember { mutableStateOf<BackupHeaderInfo?>(null) }
    var pendingRestoreFileName by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    val resolver = activity.contentResolver

    fun launchExportFile(fileName: String) {
        activity.launchForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_TITLE, fileName),
            MainActivity.RC_EXPORT_FILE,
            handler = { resultCode, data ->
                val bytes = pendingExport
                pendingExport = null
                val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
                if (uri != null && bytes != null) {
                    scope.launch {
                        try {
                            val ok = withContext(Dispatchers.IO) { writeBytesSafe(resolver, uri, bytes) }
                            toast = if (ok) "فایل بکاپ ذخیره شد" else "ذخیرهٔ فایل انجام نشد"
                            if (ok) dataStep = DataStep.Menu else dataMessage = "نوشتن فایل بکاپ ناموفق بود؛ مسیر دیگری انتخاب کنید."
                        } finally {
                            bytes.fill(0)
                            dataBusy = false
                        }
                    }
                } else {
                    bytes?.fill(0)
                    dataBusy = false
                }
            },
            onFail = {
                pendingExport?.fill(0)
                pendingExport = null
                dataMessage = "انتخاب‌گر فایل در دسترس نیست."
                dataBusy = false
            },
        )
    }

    fun launchRestoreFile() {
        activity.launchForResult(
            buildOpenAnyFileIntent(activity),
            MainActivity.RC_RESTORE_FILE,
            handler = { resultCode, data ->
                val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
                if (uri != null) {
                    dataBusy = true
                    dataMessage = null
                    scope.launch {
                        try {
                            val (bytes, name) = withContext(Dispatchers.IO) {
                                readLimitedUri(resolver, uri, 64 * 1024 * 1024) to queryDisplayName(resolver, uri)
                            }
                            if (bytes == null) {
                                dataMessage = "فایل خوانده نشد یا بیش از حد بزرگ است."
                            } else {
                                val headerInfo = Backup.inspect(bytes)
                                pendingRestore = bytes
                                pendingRestoreInfo = headerInfo
                                pendingRestoreFileName = name
                                dataMessage = null
                                dataStep = DataStep.RestorePassword
                            }
                        } catch (e: BackupFormatException) {
                            dataMessage = e.message ?: "ساختار فایل بکاپ معتبر نیست."
                        } catch (e: Exception) {
                            DiagLog.logError("RESTORE", e, "launchRestoreFile")
                            dataMessage = "خواندن فایل بکاپ ناموفق بود."
                        } finally {
                            dataBusy = false
                        }
                    }
                }
            },
            onFail = { dataMessage = "انتخاب‌گر فایل در دسترس نیست." },
        )
    }

    suspend fun runImport(bytes: ByteArray, password: CharArray?, displayName: String? = null) {
        try {
            // اگر کاربر به‌اشتباه فایل بکاپ ‎.slnb‎ سولینا را از بخش «ورود از برنامهٔ دیگر» انتخاب کرد، مستقیم به بازیابی بکاپ هدایت شود
            if (password == null && Backup.isSolinaBackup(bytes)) {
                val info = Backup.inspect(bytes)
                pendingRestore = bytes
                pendingRestoreInfo = info
                pendingRestoreFileName = displayName
                dataMessage = null
                dataStep = DataStep.RestorePassword
                return
            }
            val r = withContext(Dispatchers.Default) { Importers.parseBytes(bytes, password) }
            val stats = repo.importItems(r.items)
            pendingImport = null
            dataMessage = null
            dataStep = DataStep.Menu
            toast = "${stats.added.fa()} مورد از ${r.source} وارد شد" +
                if (stats.skipped + r.skipped > 0) " (${(stats.skipped + r.skipped).fa()} رد شد)" else ""
        } catch (e: app.solina.passwordmanager.core.importer.ImportPasswordRequired) {
            pendingImport = bytes
            dataMessage = null
            dataStep = DataStep.ImportPassword
        } catch (e: app.solina.passwordmanager.core.importer.WrongImportPasswordException) {
            dataMessage = "رمز درست نیست یا فایل دست‌کاری شده است."
        } catch (e: BackupFormatException) {
            dataMessage = e.message
        } catch (e: ImportException) {
            dataMessage = e.message
        } catch (e: Exception) {
            DiagLog.logError("IMPORT", e, "runImport")
            dataMessage = "ورود از فایل ناموفق بود."
        } finally {
            password?.fill('\u0000')
        }
    }

    fun launchImportFile() {
        activity.launchForResult(
            buildOpenAnyFileIntent(activity),
            MainActivity.RC_IMPORT_FILE,
            handler = { resultCode, data ->
                val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
                if (uri != null) {
                    dataBusy = true
                    scope.launch {
                        try {
                            val (bytes, name) = withContext(Dispatchers.IO) {
                                runCatching { readLimitedUri(resolver, uri, 20 * 1024 * 1024) }.getOrNull() to
                                    queryDisplayName(resolver, uri)
                            }
                            if (bytes == null) {
                                dataMessage = "فایل خوانده نشد یا بیش از حد بزرگ است."
                            } else {
                                runImport(bytes, null, name)
                            }
                        } catch (e: ImportException) {
                            dataMessage = e.message
                        } catch (e: Exception) {
                            dataMessage = "ورود از فایل ناموفق بود."
                        } finally {
                            dataBusy = false
                        }
                    }
                }
            },
            onFail = { dataMessage = "انتخاب‌گر فایل در دسترس نیست." },
        )
    }

    var prefill by remember { mutableStateOf<ItemPayload?>(null) }
    var saving by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var biometricEnabled by remember { mutableStateOf(auth.hasBiometric()) }
    fun autofillOn() = activity.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
    var autofillEnabled by remember { mutableStateOf(autofillOn()) }
    val notifSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notifGranted by remember {
        mutableStateOf(
            !notifSupported ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                autofillEnabled = autofillOn()
                notifGranted = !notifSupported ||
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }

    val detailItem = items.firstOrNull { it.id == detailId }
    LaunchedEffect(detailId, detailItem?.rev) {
        history = if (detailId != null && detailItem != null) runCatching { repo.history(detailId!!) }.getOrDefault(emptyList()) else emptyList()
    }

    fun onScanned(text: String) {
        scanning = false
        val parsed = OtpImport.parse(text)
        if (parsed == null) {
            toast = "این QR یک کد OTP معتبر نیست"
        } else if (parsed.size == 1) {
            prefill = parsed[0]; detailId = null; editing = true
        } else {
            scope.launch {
                val known = items.map { it.payload.totpSecret.uppercase() }.toSet()
                var added = 0
                for (p in parsed) if (p.totpSecret.uppercase() !in known) { runCatching { repo.add(p) }.onSuccess { added++ } }
                toast = "${added.fa()} مورد افزوده شد" + if (added < parsed.size) " (${(parsed.size - added).fa()} تکراری یا ناموفق)" else ""
            }
        }
    }

    if (showConnectCloud) {
        val providers = remember { app.solina.passwordmanager.sync.listCloudProviders(activity) }
        app.solina.passwordmanager.ui.screens.CloudProviderDialog(
            providers = providers,
            onPick = { showConnectCloud = false; launchConnectFolder("cloud") },
            onDismiss = { showConnectCloud = false },
        )
    }

    val inSub = generator || editing || detailId != null || scanning || dataStep != null ||
        showExternalAccounts || showWebDavConnect || showChangePassword

    BackHandler(enabled = inSub) {
        when {
            showChangePassword -> { showChangePassword = false; changePwMessage = null }
            showWebDavConnect -> { showWebDavConnect = false; webDavMessage = null }
            showExternalAccounts -> { showExternalAccounts = false; oauthMessage = null }
            dataStep != null -> {
                if (dataStep == DataStep.Menu) dataStep = null
                else { dataStep = DataStep.Menu; pendingRestore = null; pendingRestoreInfo = null; pendingRestoreFileName = null; pendingImport = null }
                dataMessage = null
            }
            scanning -> scanning = false
            editing -> { editing = false; prefill = null }
            generator -> generator = false
            else -> detailId = null
        }
    }

    val authItemsCount = remember(items) { items.count { it.payload.totpSecret.isNotBlank() } }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when {
                    showChangePassword -> ChangeMasterPasswordScreen(
                        busy = changePwBusy,
                        message = changePwMessage,
                        onSubmit = { current, next ->
                            changePwBusy = true
                            changePwMessage = null
                            scope.launch {
                                val ok = auth.verifyAndChangePassword(current, next)
                                current.fill('\u0000')
                                next.fill('\u0000')
                                changePwBusy = false
                                if (ok) {
                                    container.sync.pushHeader()
                                    showChangePassword = false
                                    toast = "رمز اصلی با موفقیت تغییر کرد"
                                } else {
                                    changePwMessage = "رمز اصلی فعلی درست نیست."
                                }
                            }
                        },
                        onBack = { showChangePassword = false; changePwMessage = null },
                    )
                    showWebDavConnect -> WebDavSetupScreen(
                        isJoin = false,
                        busy = webDavBusy,
                        message = webDavMessage,
                        onSubmit = { url, user, pass ->
                            webDavBusy = true
                            webDavMessage = null
                            scope.launch {
                                when (val r = container.sync.connectWebDav(url, user, pass)) {
                                    ConnectResult.Ok -> {
                                        showWebDavConnect = false
                                        toast = "سرور WebDAV متصل شد؛ در حال همگام‌سازی…"
                                        container.sync.syncNow()
                                    }
                                    ConnectResult.DifferentVault -> webDavMessage = "در این مسیر خزانهٔ دیگری ذخیره شده است."
                                    ConnectResult.NoVaultInFolder -> webDavMessage = "خزانه‌ای در مسیر یافت نشد."
                                    is ConnectResult.Failed -> webDavMessage = r.reason
                                }
                                webDavBusy = false
                            }
                        },
                        onBack = { showWebDavConnect = false; webDavMessage = null },
                    )
                    showExternalAccounts -> ExternalAccountsScreen(
                        accounts = externalAccounts,
                        initialGoogleClientId = container.externalAccounts.savedClientId(app.solina.passwordmanager.core.auth.OAuthProvider.Google),
                        initialGitHubClientId = container.externalAccounts.savedClientId(app.solina.passwordmanager.core.auth.OAuthProvider.GitHub),
                        busy = oauthBusy,
                        message = oauthMessage,
                        onConnect = { provider, clientId ->
                            try {
                                oauthMessage = null
                                val authUrl = container.externalAccounts.beginSignIn(provider, clientId)
                                val browse = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                activity.startActivity(browse)
                            } catch (e: Exception) {
                                oauthMessage = e.message ?: "باز کردن مرورگر برای OAuth انجام نشد."
                            }
                        },
                        onDisconnect = { id ->
                            container.externalAccounts.disconnect(id)
                            toast = "اتصال حساب خارجی قطع شد"
                        },
                        onBack = { showExternalAccounts = false; oauthMessage = null },
                    )
                    dataStep != null -> DataScreen(
                        step = dataStep!!,
                        busy = dataBusy,
                        message = dataMessage,
                        itemCount = items.size,
                        authCount = authItemsCount,
                        restoreHeaderInfo = pendingRestoreInfo,
                        restoreFileName = pendingRestoreFileName,
                        onBack = {
                            if (dataStep == DataStep.Menu) dataStep = null
                            else { dataStep = DataStep.Menu; pendingRestore = null; pendingRestoreInfo = null; pendingRestoreFileName = null; pendingImport = null }
                            dataMessage = null
                        },
                        onStartExport = { dataMessage = null; dataStep = DataStep.ExportPassword },
                        onSubmitExport = { pw ->
                            dataBusy = true
                            dataMessage = null
                            scope.launch {
                                try {
                                    val payloads = items.map { it.payload }
                                    val vh = runCatching { container.files.readHeader().encode() }.getOrNull()
                                    val bytes = withContext(Dispatchers.Default) { Backup.encode(payloads, pw, System.currentTimeMillis(), KdfCalibrator.pickOnDevice(), vh) }
                                    pw.fill('\u0000')
                                    pendingExport = bytes
                                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
                                    launchExportFile("solina-backup-$stamp.slnb")
                                } catch (e: Exception) {
                                    dataMessage = "ساخت بکاپ ناموفق بود."
                                    dataBusy = false
                                }
                            }
                        },
                        onStartAuthExport = { dataMessage = null; dataStep = DataStep.ExportAuthPassword },
                        onSubmitAuthExport = { pw ->
                            dataBusy = true
                            dataMessage = null
                            scope.launch {
                                try {
                                    val payloads = items.map { it.payload }
                                    val bytes = withContext(Dispatchers.Default) {
                                        Backup.encodeAuthenticatorOnly(payloads, pw, System.currentTimeMillis(), KdfCalibrator.pickOnDevice(), runCatching { container.files.readHeader().encode() }.getOrNull())
                                    }
                                    pw.fill('\u0000')
                                    pendingExport = bytes
                                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
                                    launchExportFile("solina-authenticator-$stamp.slnb")
                                } catch (e: BackupFormatException) {
                                    pw.fill('\u0000')
                                    dataMessage = e.message
                                    dataBusy = false
                                } catch (e: Exception) {
                                    pw.fill('\u0000')
                                    dataMessage = "ساخت بکاپ Authenticator ناموفق بود."
                                    dataBusy = false
                                }
                            }
                        },
                        onPickRestore = { dataMessage = null; launchRestoreFile() },
                        onSubmitRestore = { pw ->
                            val bytes = pendingRestore
                            if (bytes != null) {
                                dataBusy = true
                                dataMessage = null
                                scope.launch {
                                    try {
                                        val payloads = withContext(Dispatchers.Default) { Backup.decode(bytes, pw) }
                                        val stats = repo.importItems(payloads)
                                        pendingRestore = null
                                        pendingRestoreInfo = null
                                        pendingRestoreFileName = null
                                        dataStep = DataStep.Menu
                                        toast = "${stats.added.fa()} ورودی بازیابی شد" + if (stats.skipped > 0) " (${stats.skipped.fa()} تکراری)" else ""
                                    } catch (e: AuthenticationFailedException) {
                                        dataMessage = "رمز بکاپ درست نیست یا فایل دست‌کاری شده است."
                                    } catch (e: BackupFormatException) {
                                        dataMessage = e.message
                                    } catch (e: Exception) {
                                        dataMessage = "بازیابی ناموفق بود."
                                    } finally {
                                        pw.fill('\u0000')
                                        dataBusy = false
                                    }
                                }
                            }
                        },
                        onPickImport = { dataMessage = null; launchImportFile() },
                        onSubmitImport = { pw ->
                            val bytes = pendingImport
                            if (bytes != null) {
                                dataBusy = true
                                dataMessage = null
                                scope.launch { try { runImport(bytes, pw) } finally { dataBusy = false } }
                            }
                        },
                    )
                    scanning -> ScanScreen(activity, onResult = ::onScanned, onCancel = { scanning = false }, onToast = { toast = it })
                    editing -> EditScreen(
                        initial = if (detailId == null) prefill else detailItem?.payload,
                        isNew = detailId == null,
                        busy = saving,
                        onCancel = { editing = false; prefill = null },
                        onSave = { payload ->
                            saving = true
                            scope.launch {
                                try {
                                    val id = detailId
                                    if (id == null) {
                                        val added = repo.add(payload)
                                        detailId = added.id
                                        toast = "ورودی ذخیره شد"
                                    } else {
                                        repo.update(id, payload)
                                        toast = "تغییرات ذخیره شد"
                                    }
                                    editing = false
                                    prefill = null
                                } catch (e: Exception) {
                                    toast = "ذخیره نشد: خزانه قفل است یا خطای ذخیره‌سازی"
                                } finally {
                                    saving = false
                                }
                            }
                        },
                    )
                    generator -> GeneratorScreen(onBack = { generator = false }, onToast = { toast = it }, clipboardClearMillis = clipboardClear.millis)
                    detailId != null -> DetailScreen(
                        item = detailItem,
                        history = history,
                        breachCount = detailId?.let { breachState.breachedIds[it] } ?: 0,
                        onBack = { detailId = null },
                        onEdit = { editing = true },
                        onDelete = {
                            val id = detailId!!
                            scope.launch {
                                runCatching { repo.delete(id) }
                                detailId = null
                                toast = "ورودی حذف شد"
                            }
                        },
                        onRestore = { entry ->
                            scope.launch {
                                runCatching { repo.restore(detailId!!, entry) }
                                toast = "نسخهٔ قدیمی بازگردانده شد"
                            }
                        },
                        onIncrementHotp = {
                            val currentItem = detailItem ?: return@DetailScreen
                            scope.launch {
                                runCatching {
                                    repo.update(currentItem.id, currentItem.payload.copy(totpCounter = currentItem.payload.totpCounter + 1L))
                                }
                                toast = "شمارندهٔ HOTP افزایش یافت"
                            }
                        },
                        onToast = { toast = it },
                        clipboardClearMillis = clipboardClear.millis,
                    )
                    else -> when (tab) {
                        Tab.Home -> HomeScreen(
                            items = items,
                            report = report,
                            breachState = breachState,
                            onRunBreachCheck = { viewModel.runBreachCheck(items) },
                            onLock = { container.session.lock() },
                            onOpenItem = { detailId = it },
                            onGoVault = { tab = Tab.Vault },
                            onGoCodes = { tab = Tab.Codes },
                            onGoGenerator = { generator = true },
                            onAdd = { detailId = null; prefill = null; editing = true },
                            onScan = { scanning = true },
                        )
                        Tab.Vault -> VaultScreen(
                            items = items,
                            report = report,
                            unreadable = unreadable,
                            breachState = breachState,
                            onOpenItem = { detailId = it },
                            onAdd = { detailId = null; prefill = null; editing = true },
                        )
                        Tab.Codes -> CodesScreen(
                            items = items,
                            onToast = { toast = it },
                            onOpenItem = { detailId = it },
                            onScan = { scanning = true },
                            onAdd = { detailId = null; prefill = null; editing = true },
                            onIncrementHotp = { target ->
                                scope.launch {
                                    runCatching {
                                        repo.update(target.id, target.payload.copy(totpCounter = target.payload.totpCounter + 1L))
                                    }
                                    toast = "کد بعدی HOTP ساخته شد"
                                }
                            },
                            onBackupAuth = { dataMessage = null; dataStep = DataStep.ExportAuthPassword },
                            clipboardClearMillis = clipboardClear.millis,
                        )
                        Tab.Settings -> SettingsScreen(
                            biometricAvailable = auth.biometricAvailable(activity),
                            biometricEnabled = biometricEnabled,
                            onToggleBiometric = { wanted ->
                                if (wanted) {
                                    auth.enableBiometric(activity) { ok ->
                                        biometricEnabled = ok
                                        toast = if (ok) "اثر انگشت فعال شد" else "فعال‌سازی انجام نشد"
                                    }
                                } else {
                                    auth.disableBiometric()
                                    biometricEnabled = false
                                    toast = "اثر انگشت غیرفعال شد"
                                }
                            },
                            autofillEnabled = autofillEnabled,
                            notifSupported = notifSupported,
                            notifGranted = notifGranted,
                            onRequestNotifPermission = {
                                activity.requestPermissionOnce(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                    MainActivity.RC_NOTIFICATION,
                                    handler = { granted ->
                                        notifGranted = granted
                                        DiagLog.log("NOTIF", "permission granted=$granted")
                                        toast = if (granted) "اعلان خطا فعال شد" else "اعلان خطا فعال نشد"
                                    },
                                    onFail = { toast = "درخواست مجوز انجام نشد" },
                                )
                            },
                            onOpenData = { dataMessage = null; dataStep = DataStep.Menu },
                            syncStatus = syncStatus,
                            onSyncConnectCloud = { showConnectCloud = true },
                            onSyncConnectStorage = { launchConnectFolder("storage") },
                            onSyncConnectWebDav = { webDavMessage = null; showWebDavConnect = true },
                            onSyncNow = {
                                scope.launch {
                                    toast = when (container.sync.syncNow()) {
                                        is SyncManager.StatusMessage.Done -> "همگام‌سازی انجام شد"
                                        SyncManager.StatusMessage.Busy -> "همگام‌سازی در حال انجام است"
                                        SyncManager.StatusMessage.Failed -> "همگام‌سازی ناموفق بود"
                                        else -> "همگام‌سازی ممکن نشد"
                                    }
                                }
                            },
                            onSyncDisconnect = { container.sync.disconnect(); toast = "اتصال قطع شد" },
                            onSetSchedule = { container.sync.setSchedule(it) },
                            autoLock = autoLock,
                            onSetAutoLock = { container.securityPrefs.setAutoLock(it) },
                            clipboardClear = clipboardClear,
                            onSetClipboardClear = { container.securityPrefs.setClipboardClear(it) },
                            themeMode = themeMode,
                            onSetThemeMode = { container.securityPrefs.setThemeMode(it) },
                            language = language,
                            onSetLanguage = { container.securityPrefs.setLanguage(it) },
                            externalAccountCount = externalAccounts.size,
                            onOpenExternalAccounts = { oauthMessage = null; showExternalAccounts = true },
                            onChangeMasterPassword = { changePwMessage = null; showChangePassword = true },
                            onWipeLocalVault = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { container.wipeLocalVaultSecurely() }
                                    onVaultWiped()
                                }
                            },
                            onDiagnostics = onDiagnostics,
                            onEnableAutofill = {
                                try {
                                    activity.startActivity(
                                        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${activity.packageName}")),
                                    )
                                } catch (e: Exception) {
                                    toast = "این دستگاه تنظیم Autofill را پشتیبانی نمی‌کند"
                                }
                            },
                            onLock = { container.session.lock() },
                        )
                    }
                }
            }
            if (!inSub) {
                NavBar(tab) { tab = it }
            }
        }
        toast?.let {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (inSub) 24.dp else 96.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (c.isDark) c.container else c.text)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SIcon(SolinaIcons.Check, if (c.isDark) c.primary else Color(0xFF5EEAD4), 20.dp)
                    Text(it, color = if (c.isDark) c.text else c.bg, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun NavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = Solina.colors
    val str = Solina.strings
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.container)
            .navigationBarsPadding()
            .height(80.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val active = t == selected
            val label = when (t) {
                Tab.Home -> str.tabHome
                Tab.Vault -> str.tabVault
                Tab.Codes -> str.tabCodes
                Tab.Settings -> str.tabSettings
            }
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Tab, onClick = { onSelect(t) })
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(width = 56.dp, height = 32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) c.primaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) { SIcon(t.icon, if (active) c.onPrimaryContainer else c.muted, 22.dp) }
                Text(
                    label,
                    color = if (active) c.text else c.muted,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
