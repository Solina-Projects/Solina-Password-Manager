package app.solina.passwordmanager.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.solina.passwordmanager.AuthController
import app.solina.passwordmanager.SolinaApplication
import app.solina.passwordmanager.UnlockResult
import app.solina.passwordmanager.messageFor
import app.solina.passwordmanager.ui.screens.VaultUnlockScreen
import app.solina.passwordmanager.ui.theme.SolinaTheme
import kotlinx.coroutines.launch

/** صفحهٔ باز کردن خزانه برای Autofill؛ پس از باز شدن، پیشنهادها را به سیستم برمی‌گرداند. */
class AutofillAuthActivity : FragmentActivity() {
    private val container get() = (application as SolinaApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        val req = ParsedRequest(
            webHost = intent.getStringExtra(AutofillResponses.EXTRA_HOST),
            packageName = intent.getStringExtra(AutofillResponses.EXTRA_PACKAGE),
            usernameId = IntentCompat.getParcelableExtra(intent, AutofillResponses.EXTRA_USERNAME_ID, AutofillId::class.java),
            passwordId = IntentCompat.getParcelableExtra(intent, AutofillResponses.EXTRA_PASSWORD_ID, AutofillId::class.java),
        )
        val auth = AuthController(container)

        container.cancelAutoLock()
        if (container.session.unlocked.value) {
            finishWithResult(req)
            return
        }

        setContent {
            SolinaTheme {
                val scope = rememberCoroutineScope()
                var busy by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf<String?>(null) }
                VaultUnlockScreen(
                    biometricReady = auth.hasBiometric(),
                    message = message,
                    busy = busy,
                    onPassword = { password ->
                        busy = true
                        message = null
                        scope.launch {
                            val result = auth.unlockWithPassword(password)
                            password.fill('\u0000')
                            busy = false
                            if (result == UnlockResult.Success) finishWithResult(req) else message = messageFor(result)
                        }
                    },
                    onBiometric = {
                        auth.unlockWithBiometric(this@AutofillAuthActivity) { ok ->
                            if (ok) finishWithResult(req) else message = "با اثر انگشت باز نشد. از رمز اصلی استفاده کنید."
                        }
                    },
                    onForgot = { message = "برای بازیابی با کلمات امنیتی، خود برنامهٔ Solina را باز کنید." },
                )
            }
        }
    }

    private fun finishWithResult(req: ParsedRequest) {
        lifecycleScope.launch {
            runCatching { container.repository.load() }
            val response = AutofillResponses.datasets(this@AutofillAuthActivity, container.repository.items.value, req)
            if (response == null) {
                Toast.makeText(this@AutofillAuthActivity, "ورودی مطابقی برای این صفحه پیدا نشد", Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_CANCELED)
            } else {
                setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response))
            }
            // کاربر به برنامهٔ دیگر برمی‌گردد؛ مهلت قفل خودکار از همین لحظه شروع می‌شود.
            container.scheduleAutoLock()
            finish()
        }
    }
}
