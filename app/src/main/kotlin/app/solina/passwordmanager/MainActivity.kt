package app.solina.passwordmanager

import android.os.Bundle
import android.view.WindowManager
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
import android.content.Intent
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
    private val container get() = (application as SolinaApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // جلوگیری از اسکرین‌شات و نمایش محتوا در پیش‌نمایش اپ‌های اخیر.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val auth = AuthController(container)
        setContent { SolinaTheme { Root(this, container, auth) } }
    }

    override fun onStop() {
        super.onStop()
        container.scheduleAutoLock()
    }

    override fun onStart() {
        super.onStart()
        container.cancelAutoLock()
        container.session.onForegrounded()
    }
}

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
private fun Root(activity: FragmentActivity, container: AppContainer, auth: AuthController) {
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

    when {
        offerBiometric && unlocked -> BiometricOfferScreen(
            onEnable = { auth.enableBiometric(activity) { offerBiometric = false } },
            onSkip = { offerBiometric = false },
        )

        !hasVault -> when (stage) {
            Stage.Welcome -> WelcomeScreen { stage = Stage.CreatePassword }
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

        else -> MainApp(activity, container, auth)
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("خانه", SolinaIcons.Home),
    Vault("خزانه", SolinaIcons.Lock),
    Codes("کدها", SolinaIcons.Clock),
    Settings("تنظیمات", SolinaIcons.Sliders),
}

@Composable
private fun MainApp(activity: FragmentActivity, container: AppContainer, auth: AuthController) {
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
    var scanning by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf<ItemPayload?>(null) }
    var saving by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var biometricEnabled by remember { mutableStateOf(auth.hasBiometric()) }
    fun autofillOn() = activity.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
    var autofillEnabled by remember { mutableStateOf(autofillOn()) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) autofillEnabled = autofillOn() }
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

    val inSub = generator || editing || detailId != null || scanning
    BackHandler(enabled = inSub) {
        when {
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
                    scanning -> ScanScreen(onResult = ::onScanned, onCancel = { scanning = false }, onToast = { toast = it })
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
                        Tab.Codes -> CodesScreen(items = items, onToast = { toast = it }, onOpenItem = { detailId = it }, onScan = { scanning = true })
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
