package com.brotv.iptv.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brotv.iptv.data.local.SecureCredentialStore
import com.brotv.iptv.data.model.PlaylistProfile
import com.brotv.iptv.data.pairing.PairingMode
import com.brotv.iptv.data.pairing.QrPairingManager
import com.brotv.iptv.data.remote.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LoginMode { XTREAM, M3U }
enum class LoginField { LIST_NAME, USERNAME, PASSWORD, HOST, LOGIN_BUTTON }

class LoginViewModel(
    private val credentialStore: SecureCredentialStore,
    private val repository: IptvRepository,
    private val qrPairingManager: QrPairingManager = QrPairingManager(),
) : ViewModel() {
    var mode by mutableStateOf(LoginMode.XTREAM); private set
    var listName by mutableStateOf(""); private set
    var username by mutableStateOf(""); private set
    var password by mutableStateOf(""); private set
    var hostUrl by mutableStateOf(""); private set
    var focusedField by mutableStateOf(LoginField.LIST_NAME); private set
    var qrSession by mutableStateOf<QrPairingManager.PairingSession?>(null); private set
    var qrError by mutableStateOf<String?>(null); private set
    var isSubmitting by mutableStateOf(false); private set
    var loginError by mutableStateOf<String?>(null); private set

    private var qrStartJob: Job? = null
    @Volatile private var qrGeneration = 0L

    init {
        credentialStore.loadActiveProfile()?.let { profile ->
            listName = profile.listName; username = profile.username; password = profile.password; hostUrl = profile.hostUrl
            if (profile.username.isBlank() && profile.password.isBlank()) mode = LoginMode.M3U
        }
    }

    fun selectMode(newMode: LoginMode) {
        if (mode == newMode) return
        mode = newMode
        focusedField = LoginField.LIST_NAME
        loginError = null
        qrError = null
        if (newMode == LoginMode.M3U) { username = ""; password = "" }
        // LoginScreen's LaunchedEffect(mode) is the single owner of restarting QR.
        // Keeping startup in one place prevents two concurrent NanoHTTPD binds.
    }

    /**
     * Second, independently-sufficient root cause fix for QA failure #12
     * ("a crash or ANR was observed during the QA run"): this is invoked
     * from `LaunchedEffect(viewModel.mode) { viewModel.startQrSession(...) }`
     * on Login's *first composition* - i.e. on every cold start where no
     * profile is saved yet, which is exactly the state a fresh QA
     * install/run starts from. [QrPairingManager.startSession] is a fully
     * synchronous, non-suspending call chain that performs real blocking I/O:
     * `NetworkInterface.getNetworkInterfaces()` (network stack enumeration)
     * followed by `NanoHTTPD.start()`, which blocks on an actual server
     * socket bind. `LaunchedEffect` runs on the Main/UI dispatcher, so none
     * of that yields - it fully blocks the UI thread before Login's first
     * frame, exactly the kind of main-thread network+socket I/O that
     * produces an ANR, and is especially likely on slower Android TV
     * boxes/dongles with slower network stacks.
     *
     * Fix: do the blocking setup on `Dispatchers.IO` and only hop back to
     * the main/view-model scope to publish the resulting [qrSession] state.
     */
    fun startQrSession(onSuccess: () -> Unit) {
        val pairMode = if (mode == LoginMode.XTREAM) PairingMode.XTREAM else PairingMode.M3U
        val generation = ++qrGeneration
        qrStartJob?.cancel()
        qrSession = null
        qrError = null

        qrStartJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    qrPairingManager.startSession(pairMode) { profile ->
                        // NanoHTTPD invokes this callback on its own worker thread. Only accept
                        // credentials from the newest QR generation; stale sessions are ignored.
                        if (generation == qrGeneration) {
                            viewModelScope.launch {
                                listName = profile.listName
                                username = profile.username
                                password = profile.password
                                hostUrl = profile.hostUrl
                                focusedField = LoginField.LOGIN_BUTTON
                                submitLogin(onSuccess, restartQrOnFailure = true)
                            }
                        }
                    }
                }
            }

            if (generation != qrGeneration) return@launch
            result.onSuccess { session ->
                qrSession = session
                if (session == null) qrError = "تعذر إنشاء رمز QR. تأكد من اتصال التلفزيون بالشبكة المحلية."
            }.onFailure { error ->
                qrSession = null
                qrError = friendlyQrError(error)
            }
        }
    }

    fun updateField(field: LoginField, value: String) {
        loginError = null
        when (field) {
            LoginField.LIST_NAME -> listName = value
            LoginField.USERNAME -> username = value
            LoginField.PASSWORD -> password = value
            LoginField.HOST -> hostUrl = value
            LoginField.LOGIN_BUTTON -> Unit
        }
    }

    fun advanceFocusOnOk() {
        focusedField = when (focusedField) {
            LoginField.LIST_NAME -> if (mode == LoginMode.XTREAM) LoginField.USERNAME else LoginField.HOST
            LoginField.USERNAME -> LoginField.PASSWORD
            LoginField.PASSWORD -> LoginField.HOST
            LoginField.HOST, LoginField.LOGIN_BUTTON -> LoginField.LOGIN_BUTTON
        }
    }

    fun onFieldFocused(field: LoginField) { focusedField = field }

    fun currentProfile() = PlaylistProfile(
        listName.trim(),
        if (mode == LoginMode.XTREAM) username.trim() else "",
        if (mode == LoginMode.XTREAM) password else "",
        normalizeHost(hostUrl),
    )

    fun submitLogin(onSuccess: () -> Unit, restartQrOnFailure: Boolean = false) {
        if (isSubmitting) return
        val profile = currentProfile()
        val valid = listName.isNotBlank() && hostUrl.isNotBlank() && (mode == LoginMode.M3U || (username.isNotBlank() && password.isNotBlank()))
        if (!valid) { loginError = if (mode == LoginMode.M3U) "أكمل اسم القائمة ورابط M3U" else "أكمل اسم القائمة واسم المستخدم وكلمة السر والرابط"; return }
        isSubmitting = true; loginError = null
        viewModelScope.launch {
            repository.authenticate(profile).onSuccess {
                credentialStore.saveProfile(profile)
                qrPairingManager.stopSession()
                qrSession = null
                isSubmitting = false
                onSuccess()
            }.onFailure { error ->
                isSubmitting = false
                loginError = friendlyError(error)
                // A QR token is single-use. If provider authentication fails, immediately
                // issue a fresh token so the user can correct the credentials and retry.
                if (restartQrOnFailure) startQrSession(onSuccess)
            }
        }
    }

    fun stopQr() {
        qrGeneration++
        qrStartJob?.cancel()
        qrStartJob = null
        qrPairingManager.stopSession()
        qrSession = null
    }
    override fun onCleared() { stopQr(); super.onCleared() }

    private fun normalizeHost(value: String): String {
        val v = value.trim(); if (v.isBlank() || v.startsWith("http://") || v.startsWith("https://")) return v
        return "http://$v"
    }

    private fun friendlyQrError(error: Throwable): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("Address already in use", true) || text.contains("EADDRINUSE", true) ->
                "منفذ الاقتران 8988 مستخدم من تطبيق آخر. أغلق التطبيق الآخر ثم أعد فتح شاشة الدخول."
            text.contains("Permission denied", true) -> "تعذر فتح خادم QR على الشبكة المحلية."
            else -> "تعذر تشغيل الاقتران عبر QR${if (text.isNotBlank()) ": $text" else "."}"
        }
    }

    private fun friendlyError(error: Throwable): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("timeout", true) -> "انتهت مهلة الاتصال بالسيرفر"
            text.contains("Unable to resolve host", true) -> "تعذر العثور على عنوان السيرفر"
            text.contains("HTTP 401") || text.contains("HTTP 403") -> "رفض السيرفر بيانات الدخول"
            text.isNotBlank() -> text
            else -> "تعذر التحقق من الاشتراك"
        }
    }
}
