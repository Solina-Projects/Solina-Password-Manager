package app.solina.passwordmanager

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.solina.passwordmanager.ui.components.SIcon
import app.solina.passwordmanager.ui.screens.CodesScreen
import app.solina.passwordmanager.ui.screens.DetailScreen
import app.solina.passwordmanager.ui.screens.GeneratorScreen
import app.solina.passwordmanager.ui.screens.HomeScreen
import app.solina.passwordmanager.ui.screens.SettingsScreen
import app.solina.passwordmanager.ui.screens.UnlockScreen
import app.solina.passwordmanager.ui.screens.VaultScreen
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons
import app.solina.passwordmanager.ui.theme.SolinaTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // جلوگیری از اسکرین‌شات و نمایش محتوا در پیش‌نمایش اپ‌های اخیر.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { SolinaTheme { SolinaApp() } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("خانه", SolinaIcons.Home),
    Vault("خزانه", SolinaIcons.Lock),
    Codes("کدها", SolinaIcons.Clock),
    Settings("تنظیمات", SolinaIcons.Sliders),
}

@Composable
private fun SolinaApp() {
    val c = Solina.colors
    var unlocked by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(Tab.Home) }
    var detailId by remember { mutableStateOf<Int?>(null) }
    var generator by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
    }

    if (!unlocked) {
        UnlockScreen { unlocked = true }
        return
    }

    BackHandler(enabled = detailId != null || generator) {
        if (detailId != null) detailId = null else generator = false
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when {
                    generator -> GeneratorScreen(onBack = { generator = false }, onToast = { toast = it })
                    detailId != null -> DetailScreen(detailId!!, onBack = { detailId = null }, onToast = { toast = it })
                    else -> when (tab) {
                        Tab.Home -> HomeScreen(
                            onLock = { unlocked = false },
                            onOpenItem = { detailId = it },
                            onGoVault = { tab = Tab.Vault },
                            onGoCodes = { tab = Tab.Codes },
                            onGoGenerator = { generator = true },
                        )
                        Tab.Vault -> VaultScreen(onOpenItem = { detailId = it })
                        Tab.Codes -> CodesScreen(onToast = { toast = it })
                        Tab.Settings -> SettingsScreen()
                    }
                }
            }
            if (!generator && detailId == null) {
                NavBar(tab) { tab = it }
            }
        }

        toast?.let {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (generator || detailId != null) 24.dp else 96.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (c.isDark) c.container else c.text)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SIcon(SolinaIcons.Check, c.primaryContainer.let { if (c.isDark) c.primary else androidx.compose.ui.graphics.Color(0xFF5EEAD4) }, 20.dp)
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
                        .background(if (active) c.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),
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
