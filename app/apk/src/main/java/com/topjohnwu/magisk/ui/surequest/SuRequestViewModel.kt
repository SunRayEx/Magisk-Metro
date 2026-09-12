package com.topjohnwu.magisk.ui.surequest

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.CountDownTimer
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.arch.BaseViewModel
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.data.magiskdb.PolicyDao
import com.topjohnwu.magisk.core.ktx.getLabel
import com.topjohnwu.magisk.core.model.su.SuPolicy.Companion.ALLOW
import com.topjohnwu.magisk.core.model.su.SuPolicy.Companion.DENY
import com.topjohnwu.magisk.core.model.su.SuPolicy.Companion.ZERO
import com.topjohnwu.magisk.core.su.SuRequestHandler
<<<<<<< HEAD
import com.topjohnwu.magisk.databinding.set
import com.topjohnwu.magisk.events.AuthEvent
import com.topjohnwu.magisk.events.DieEvent
import com.topjohnwu.magisk.events.ShowUIEvent
import com.topjohnwu.magisk.utils.TextHolder
=======
>>>>>>> 37063225d4f344a8f41de8201f679e57098cb7e6
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit.SECONDS

class SuRequestViewModel(
    policyDB: PolicyDao,
    private val timeoutPrefs: SharedPreferences
) : BaseViewModel() {

    data class UiState(
        val showUi: Boolean = false,
        val icon: Drawable? = null,
        val title: String = "",
        val packageName: String = "",
        val isSharedUid: Boolean = false,
        val selectedItemPosition: Int = 0,
        val grantEnabled: Boolean = false,
        val denyCountdown: Int = 0,
        val useTapjackProtection: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    var authenticate: (onSuccess: () -> Unit) -> Unit = { it() }
    var finishActivity: () -> Unit = {}

    val useTapjackProtection get() = _uiState.value.useTapjackProtection

    private val handler = SuRequestHandler(AppContext.packageManager, policyDB)
    private val millis = SECONDS.toMillis(Config.suDefaultTimeout.toLong())
    private var timer = SuTimer(millis, 1000)
    private var initialized = false
    private var responded = false

    fun setSelectedItemPosition(position: Int) {
        _uiState.update { it.copy(selectedItemPosition = position) }
    }

    fun grantPressed() {
        cancelTimer()
        if (Config.suAuth) {
            authenticate { respond(ALLOW) }
        } else {
            respond(ALLOW)
        }
    }

    /** Grants exactly this request; the policy is never persisted, so it cannot be found in
     * the authorized list afterwards. */
    fun grantOncePressed() {
        cancelTimer()
        if (Config.suAuth) {
            AuthEvent { respondOnce(ALLOW) }.publish()
        } else {
            respondOnce(ALLOW)
        }
    }

    /** Deceptive grant: uid 0 in name only; the daemon sandboxes and ghost-audits the shell. */
    fun zeroPressed() {
        cancelTimer()
        if (Config.suAuth) {
            AuthEvent { respondZero() }.publish()
        } else {
            respondZero()
        }
    }

    fun denyPressed() {
        respond(DENY)
    }

    fun spinnerTouched() {
        cancelTimer()
    }

    fun handleRequest(intent: Intent) {
        viewModelScope.launch(Dispatchers.Default) {
            if (handler.start(intent))
                showDialog()
            else
                finishActivity()
        }
    }

    private fun showDialog() {
        val pm = handler.pm
        val info = handler.pkgInfo
        val app = info.applicationInfo

        val isSharedUid = info.sharedUserId != null
        val icon: Drawable?
        val title: String
        val packageName: String

        if (app == null) {
            icon = pm.defaultActivityIcon
            title = info.sharedUserId.toString()
            packageName = info.sharedUserId.toString()
        } else {
            icon = app.loadIcon(pm)
            title = app.getLabel(pm)
            packageName = info.packageName
        }

        val selectedPos = timeoutPrefs.getInt(packageName, 0)
        _uiState.update {
            it.copy(
                showUi = true,
                icon = icon,
                title = title,
                packageName = packageName,
                isSharedUid = isSharedUid,
                selectedItemPosition = selectedPos,
                useTapjackProtection = Config.suTapjack,
                grantEnabled = false,
            )
        }
        viewModelScope.launch {
            delay(1000)
            _uiState.update { it.copy(grantEnabled = true) }
        }
        timer.start()
        initialized = true
    }

    fun activityDestroyed() {
        if (initialized && !responded) {
            respond(DENY)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timer.cancel()
        if (initialized && !responded) {
            responded = true
            val pos = _uiState.value.selectedItemPosition
            runBlocking(Dispatchers.IO) {
                handler.respond(DENY, Config.Value.TIMEOUT_LIST[pos])
            }
        }
    }

    private fun respond(action: Int) {
        if (!initialized || responded) return
        responded = true
        timer.cancel()

        val pos = _uiState.value.selectedItemPosition
        val pkg = _uiState.value.packageName
        timeoutPrefs.edit { putInt(pkg, pos) }

        viewModelScope.launch(Dispatchers.IO) {
            handler.respond(action, Config.Value.TIMEOUT_LIST[pos])
            withContext(Dispatchers.Main) {
                finishActivity()
            }
        }
    }

    private fun respondZero() {
        if (!initialized) {
            return
        }

        timer.cancel()
        viewModelScope.launch {
            handler.respond(ZERO, 0)
            DieEvent().publish()
        }
    }

    private fun respondOnce(action: Int) {
        if (!initialized) {
            return
        }

        timer.cancel()
        viewModelScope.launch {
            handler.respond(action, -1)
            DieEvent().publish()
        }
    }

    private fun cancelTimer() {
        timer.cancel()
        _uiState.update { it.copy(denyCountdown = 0) }
    }

    private inner class SuTimer(
        millis: Long,
        interval: Long
    ) : CountDownTimer(millis, interval) {

        override fun onTick(remains: Long) {
            _uiState.update {
                it.copy(
                    denyCountdown = (remains / 1000).toInt() + 1
                )
            }
        }

        override fun onFinish() {
            _uiState.update { it.copy(denyCountdown = 0) }
            respond(DENY)
        }
<<<<<<< HEAD

    }

    inner class DenyText : TextHolder() {
        var seconds = 0
            set(value) = set(value, field, { field = it }, BR.denyText)

        override fun getText(resources: Resources): CharSequence {
            return if (seconds != 0)
                "${resources.getString(R.string.deny)} ($seconds)"
            else
                resources.getString(R.string.deny)
        }
    }

    // Invisible for accessibility services
    object EmptyAccessibilityDelegate : View.AccessibilityDelegate() {
        override fun sendAccessibilityEvent(host: View, eventType: Int) {}
        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?) = true
        override fun sendAccessibilityEventUnchecked(host: View, event: AccessibilityEvent) {}
        override fun dispatchPopulateAccessibilityEvent(host: View, event: AccessibilityEvent) = true
        override fun onPopulateAccessibilityEvent(host: View, event: AccessibilityEvent) {}
        override fun onInitializeAccessibilityEvent(host: View, event: AccessibilityEvent) {}
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {}
        override fun addExtraDataToAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo, extraDataKey: String, arguments: Bundle?) {}
        override fun onRequestSendAccessibilityEvent(host: ViewGroup, child: View, event: AccessibilityEvent): Boolean = false
        override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProvider? = null
=======
>>>>>>> 37063225d4f344a8f41de8201f679e57098cb7e6
    }
}
