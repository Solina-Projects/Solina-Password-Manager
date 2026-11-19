package app.solina.passwordmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.solina.passwordmanager.core.security.BreachChecker
import app.solina.passwordmanager.core.storage.ItemKind
import app.solina.passwordmanager.core.storage.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BreachScanState(
    val scanning: Boolean = false,
    val checkedCount: Int = 0,
    val breachedIds: Map<String, Int> = emptyMap(),
    val error: String? = null,
)

/**
 * ViewModel معماری MVVM برای وضعیت‌های تحلیلی خزانه (از جمله بررسی اختیاری افشای رمز با k-Anonymity).
 */
class SolinaViewModel : ViewModel() {
    private val _breachState = MutableStateFlow(BreachScanState())
    val breachState: StateFlow<BreachScanState> = _breachState.asStateFlow()

    fun runBreachCheck(items: List<VaultItem>) {
        if (_breachState.value.scanning) return
        val logins = items.filter { it.payload.kind == ItemKind.Login && it.payload.password.isNotEmpty() }
        if (logins.isEmpty()) {
            _breachState.value = BreachScanState(scanning = false, checkedCount = 0)
            return
        }
        _breachState.value = BreachScanState(scanning = true)
        viewModelScope.launch(Dispatchers.IO) {
            val found = LinkedHashMap<String, Int>()
            val cache = HashMap<String, Int?>()
            var checked = 0
            var networkErrors = 0
            for (item in logins) {
                val pw = item.payload.password
                val count = cache.getOrPut(pw) { BreachChecker.checkOnline(pw) }
                if (count == null) {
                    networkErrors++
                } else {
                    checked++
                    if (count > 0) found[item.id] = count
                }
            }
            _breachState.value = BreachScanState(
                scanning = false,
                checkedCount = checked,
                breachedIds = found,
                error = if (checked == 0 && networkErrors > 0) "اتصال به سرویس بررسی افشا برقرار نشد." else null,
            )
        }
    }

    fun clearBreachState() {
        _breachState.value = BreachScanState()
    }
}
