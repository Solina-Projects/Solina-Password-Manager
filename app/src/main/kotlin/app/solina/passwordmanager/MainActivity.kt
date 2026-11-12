package app.solina.passwordmanager

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.statusBarsPadding
import app.solina.passwordmanager.ui.components.copySensitive
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.fragment.app.FragmentActivity
import app.solina.passwordmanager.core.crypto.CreatedVault
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.solina.passwordmanager.core.fa
import app.solina.passwordmanager.core.storage.HistoryEntry
import app.solina.passwordmanager.core.storage.ItemPayload
import app.solina.passwordmanager.core.storage.SecurityAnalyzer
import app.solina.passwordmanager.ui.components.SIcon
import app.solina.passwordmanager.ui.screens.BiometricOfferScreen
import app.solina.passwordmanager.core.backup.Backup
import app.solina.passwordmanager.core.backup.BackupFormatException
import app.solina.passwordmanager.core.crypto.AuthenticationFailedException
import app.solina.passwordmanager.core.importer.ImportException
import app.solina.passwordmanager.core.importer.Importers
import app.solina.passwordmanager.ui.screens.DataScreen
import app.solina.passwordmanager.ui.screens.DiagnosticsScreen
import app.solina.passwordmanager.sync.ConnectResult
import app.solina.passwordmanager.sync.SyncManager
import kotlinx.coroutines.flow.first
import app.solina.passwordmanager.ui.screens.DataStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.solina.passwordmanager.ui.screens.CodesScreen
import app.solina.passwordmanager.ui.screens.ConfirmWordsScreen
import app.solina.passwordmanager.ui.screens.CreatePasswordScreen
import app.solina.passwordmanager.ui.screens.DetailScreen
import app.solina.passwordmanager.ui.screens.EditScreen
import app.solina.passwordmanager.ui.screens.GeneratorScreen
import app.solina.passwordmanager.ui.screens.HomeScreen
import app.solina.passwordmanager.ui.screens.RecoveryEntryScreen
import app.solina.passwordmanager.ui.screens.ScanScreen
import app.solina.passwordmanager.core.otp.OtpImport
import app.solina.passwordmanager.ui.screens.RecoveryWordsScreen
import app.solina.passwordmanager.ui.screens.ResetPasswordScreen
import app.solina.passwordmanager.ui.screens.SettingsScreen
import app.solina.passwordmanager.ui.screens.VaultScreen
import app.solina.passwordmanager.ui.screens.VaultUnlockScreen
import app.solina.passwordmanager.ui.screens.WelcomeScreen
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons
import app.solina.passwordmanager.ui.theme.SolinaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    companion object {
        /** وقتی نوتیف خطا زده شد، با این extra باز می‌شود تا مستقیم گزارش عیب‌یابی نشان داده شود. */
        const val EXTRA_SHOW_DIAGNOSTICS = "solina_show_diagnostics"

        /**
         * کدهای درخواست کوچک و ثابت برای startActivityForResult / requestPermissions.
         * چرا ثابت و کوچک: ActivityResultRegistry در androidx.activity برای هر لانچر
         * کد تصادفی ‎>= 65536‎ می‌سازد (Random.nextInt(2147418112) + 65536) و
         * FragmentActivity فقط ۱۶ بیت پایین کد را قبول می‌کند؛ برای همین همهٔ لانچرهای
         * Activity Result در این اپ با IllegalArgumentException
         * «Can only use lower 16 bits for requestCode» می‌مردند (0.0.11-alpha5:
         * کرش در شروع اپ، کرش اسکن QR، و باز نشدن انتخاب‌گر پوشه). این کدها همه
         * زیر 0xFFFF هستند و چون دیگر هیچ لانچری در اپ نیست، با چیزی تداخل ندارند.
         */
        const val RC_JOIN_FOLDER = 1001
        const val RC_CONNECT_FOLDER = 1002
        const val RC_EXPORT_FILE = 1003
        const val RC_RESTORE_FILE = 1004
        const val RC_IMPORT_FILE = 1005
        const val RC_NOTIFICATION = 1006
        const val RC_CAMERA = 1007
        const val RC_GALLERY_IMAGE = 1008
    }

    private val container get() = (application as SolinaApplication).container

    /** نتیجهٔ startActivityForResult را به تابعی که هنگام شروع ثبت شد می‌رساند. */
    private val activityResultHandlers = mutableMapOf<Int, (resultCode: Int, data: Intent?) -> Unit>()
    private val permissionHandlers = mutableMapOf<Int, (granted: Boolean) -> Unit>()

    /**
     * جایگزین ActivityResultLauncher: اکتیویتی را با کد کوچک و ثابت شروع می‌کند و
     * نتیجه را به [handler] می‌دهد. اگر شروع ناموفق بود (مثلاً هیچ برنامه‌ای برای
     * آن Intent نیست)، خطا در گزارش عیب‌یابی ثبت می‌شود و [onFail] صدا زده می‌شود؛
     * هیچ‌وقت کرش نمی‌کند.
     */
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

    /**
     * جایگزین RequestPermission: اگر مجوز از قبل داده شده، همان‌جا true برمی‌گرداند
     * و دیالوگ سیستم باز نمی‌شود. فقط از ضربهٔ کاربر صدا زده می‌شود، هرگز خودکار
     * در شروع اپ یا LaunchedEffect نیست.
     */
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
        // اول super: FragmentActivity نتیجه را به فرگمنت‌ها و رجیستری می‌رساند.
        super.onActivityResult(requestCode, resultCode, data)
        activityResultHandlers.remove(requestCode)?.invoke(resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHandlers.remove(requestCode)?.invoke(
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // جلوگیری از اسکرین‌شات و نمایش محتوا در پیش‌نمایش اپ‌های اخیر.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val auth = AuthController(container)
        DiagLog.log("ACT", "onCreate")
        setContent {
            SolinaTheme {
                var showDiag by remember { mutableStateOf(intent.getBooleanExtra(EXTRA_SHOW_DIAGNOSTICS, false)) }
                // مجوز نوتیفیکیشن دیگر اینجا خودکار خواسته نمی‌شود: در 0.0.11-alpha5
                // همان درخواست در اولین ترکیب‌بندی کرش می‌کرد و اپ اصلاً بالا نمی‌آمد.
                // حالا فقط از تنظیمات و با ضربهٔ کاربر درخواست می‌شود (SettingsScreen).
                Box(Modifier.fillMaxSize().background(Solina.colors.bg)) {
                    if (showDiag) {
                        DiagScreenHost(this@MainActivity) { showDiag = false }
                    } else {
                        Root(this@MainActivity, container, auth, onDiagnostics = { showDiag = true })
                        CrashBanner(this@MainActivity) { showDiag = true }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        DiagLog.log("ACT", "onStop")
        container.scheduleAutoLock()
    }

    override fun onStart() {
        super.onStart()
        DiagLog.log("ACT", "onStart")
        container.cancelAutoLock()
        container.session.onForegrounded()
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
private fun Root(activity: MainActivity, container: AppContainer, auth: AuthController, onDiagnostics: () -> Unit) {
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
                    when (val r = container.sync.joinFromFolder(uri, kind)) {
                        ConnectResult.Ok -> hasVault = true
                        ConnectResult.NoVaultInFolder -> joinMessage = "در این پوشه خزانه‌ای پیدا نشد. پوشه‌ای را انتخاب کنید که شامل «Solina-Sync» است (یعنی همان پوشه‌ای که در دستگاه اول انتخاب شد)."
                        ConnectResult.DifferentVault -> joinMessage = "پوشه خزانهٔ دیگری دارد."
                        is ConnectResult.Failed -> joinMessage = r.reason
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

        !hasVault -> when (stage) {
            Stage.Welcome -> {
                WelcomeScreen(
                    message = joinMessage,
                    onCreate = { joinMessage = null; stage = Stage.CreatePassword },
                    onDiagnostics = onDiagnostics,
                    onJoinCloud = { joinMessage = null; DiagLog.log("JOIN", "cloud button tapped"); showJoinCloud = true },
                    onJoinStorage = { joinMessage = null; DiagLog.log("JOIN", "storage button tapped"); launchJoinFolder("storage") },
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
            Stage.CreatePassword -> CreatePasswordScreen(busy) { password ->
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
                        hasVault = true
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
                    // پس از بازیابی، میان‌بر اثر انگشت برای احتیاط غیرفعال می‌شود (از تنظیمات دوباره فعال می‌شود).
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

        else -> MainApp(activity, container, auth, onDiagnostics)
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("خانه", SolinaIcons.Home),
    Vault("خزانه", SolinaIcons.Lock),
    Codes("کدها", SolinaIcons.Clock),
    Settings("تنظیمات", SolinaIcons.Sliders),
}

@Composable
private fun MainApp(activity: MainActivity, container: AppContainer, auth: AuthController, onDiagnostics: () -> Unit) {
    val c = Solina.colors
    val scope = rememberCoroutineScope()
    val repo = container.repository
    val items by repo.items.collectAsState()
    val unreadable by repo.unreadable.collectAsState()
    val report = remember(items) { SecurityAnalyzer.analyze(items) }

    var tab by remember { mutableStateOf(Tab.Home) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) } // با detailId == null یعنی ورودی جدید
    var generator by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val syncStatus by container.sync.status.collectAsState()
    val localRev by repo.localChanges.collectAsState()
    var showConnectCloud by remember { mutableStateOf(false) }
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
    // هم‌گام‌سازی خودکار: بعد از باز شدن خزانه، و ۱٫۵ ثانیه بعد از هر تغییر محلی (debounce).
    // زمان‌بندی کاربر اعمال می‌شود: هر تغییر / روزانه / هفتگی / ماهانه / دستی.
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
    val resolver = activity.contentResolver

    fun readLimited(uri: Uri, max: Int): ByteArray? =
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
                    val ok = runCatching { resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null }.getOrDefault(false)
                    toast = if (ok) "بکاپ ذخیره شد" else "ذخیرهٔ فایل انجام نشد"
                    if (ok) dataStep = DataStep.Menu
                }
                dataBusy = false
            },
            onFail = { dataMessage = "انتخاب‌گر فایل در دسترس نیست."; dataBusy = false },
        )
    }

    fun launchRestoreFile() {
        activity.launchForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("*/*")),
            MainActivity.RC_RESTORE_FILE,
            handler = { resultCode, data ->
                val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
                if (uri != null) {
                    val bytes = runCatching { readLimited(uri, 64 * 1024 * 1024) }.getOrNull()
                    if (bytes == null) dataMessage = "فایل خوانده نشد یا بیش از حد بزرگ است."
                    else { pendingRestore = bytes; dataMessage = null; dataStep = DataStep.RestorePassword }
                }
            },
            onFail = { dataMessage = "انتخاب‌گر فایل در دسترس نیست." },
        )
    }

    fun launchImportFile() {
        activity.launchForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("*/*")),
            MainActivity.RC_IMPORT_FILE,
            handler = { resultCode, data ->
                val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
                if (uri != null) {
                    dataBusy = true
                    scope.launch {
                        try {
                            val bytes = withContext(Dispatchers.IO) { runCatching { readLimited(uri, 20 * 1024 * 1024) }.getOrNull() }
                            if (bytes == null) {
                                dataMessage = "فایل خوانده نشد یا بیش از حد بزرگ است."
                            } else {
                                val r = Importers.parse(String(bytes, Charsets.UTF_8))
                                val stats = repo.importItems(r.items)
                                dataMessage = null
                                toast = "${stats.added.fa()} مورد از ${r.source} وارد شد" +
                                    if (stats.skipped + r.skipped > 0) " (${(stats.skipped + r.skipped).fa()} رد شد)" else ""
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
    // اعلان خطا (اندروید ۱۳ به بعد): فقط از تنظیمات و با ضربهٔ کاربر درخواست می‌شود؛
    // درخواست خودکار در شروع اپ در 0.0.11-alpha5 باعث کرش می‌شد.
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
            toast = "این QR یک کد TOTP معتبر نیست"
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
    val inSub = generator || editing || detailId != null || scanning || dataStep != null
    BackHandler(enabled = inSub) {
        when {
            dataStep != null -> { if (dataStep == DataStep.Menu) dataStep = null else { dataStep = DataStep.Menu; pendingRestore = null }; dataMessage = null }
            scanning -> scanning = false
            editing -> { editing = false; prefill = null }
            generator -> generator = false
            else -> detailId = null
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when {
                    dataStep != null -> DataScreen(
                        step = dataStep!!,
                        busy = dataBusy,
                        message = dataMessage,
                        itemCount = items.size,
                        onBack = { if (dataStep == DataStep.Menu) dataStep = null else { dataStep = DataStep.Menu; pendingRestore = null }; dataMessage = null },
                        onStartExport = { dataMessage = null; dataStep = DataStep.ExportPassword },
                        onSubmitExport = { pw ->
                            dataBusy = true
                            dataMessage = null
                            scope.launch {
                                try {
                                    val payloads = items.map { it.payload }
                                    val bytes = withContext(Dispatchers.Default) { Backup.encode(payloads, pw, System.currentTimeMillis()) }
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
                    generator -> GeneratorScreen(onBack = { generator = false }, onToast = { toast = it })
                    detailId != null -> DetailScreen(
                        item = detailItem,
                        history = history,
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
                        onToast = { toast = it },
                    )
                    else -> when (tab) {
                        Tab.Home -> HomeScreen(
                            items = items,
                            report = report,
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
                            onOpenItem = { detailId = it },
                            onAdd = { detailId = null; prefill = null; editing = true },
                        )
                        Tab.Codes -> CodesScreen(items = items, onToast = { toast = it }, onOpenItem = { detailId = it }, onScan = { scanning = true }, onAdd = { detailId = null; prefill = null; editing = true })
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
                            onSyncNow = {
                                scope.launch {
                                    toast = when (val r = container.sync.syncNow()) {
                                        is SyncManager.StatusMessage.Done -> "همگام‌سازی انجام شد"
                                        SyncManager.StatusMessage.Busy -> "همگام‌سازی در حال انجام است"
                                        SyncManager.StatusMessage.Failed -> "همگام‌سازی ناموفق بود"
                                        else -> "همگام‌سازی ممکن نشد"
                                    }
                                }
                            },
                            onSyncDisconnect = { container.sync.disconnect(); toast = "اتصال قطع شد" },
                            onSetSchedule = { container.sync.setSchedule(it) },
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
                    t.label,
                    color = if (active) c.text else c.muted,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
