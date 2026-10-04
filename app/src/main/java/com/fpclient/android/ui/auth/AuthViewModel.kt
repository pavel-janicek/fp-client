package com.fpclient.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.RegisterRequest
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerSetupViewModel(
    private val auth: com.fpclient.android.data.repository.AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done = _done.asStateFlow()

    fun connect(url: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                com.fpclient.android.data.session.SessionStore.normalizeServerUrl(url)
                    .takeIf { it.isNotBlank() }
                    ?.let { auth.setServerUrl(it) }
                _done.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Invalid URL"
            } finally {
                _busy.value = false
            }
        }
    }

    /** Skips account setup: browse the official instance's public timeline as a guest. */
    fun skip() {
        viewModelScope.launch {
            sessionStore.continueAsGuest()
            _done.value = true
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ServerSetupViewModel(container.authRepository, container.sessionStore) }
        }
    }
}

class LoginViewModel(
    private val auth: com.fpclient.android.data.repository.AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _success = MutableStateFlow<Boolean?>(null)
    val success = _success.asStateFlow()

    fun login(usernameOrEmail: String, password: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            when (val result = auth.login(usernameOrEmail, password)) {
                is ApiResult.Success -> _success.value = true
                is ApiResult.Error -> _error.value = result.message ?: "Login failed"
            }
            _busy.value = false
        }
    }

    /** Browses the configured instance's public timeline without signing in. */
    fun browseAsGuest() {
        viewModelScope.launch { sessionStore.startGuestBrowsing() }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { LoginViewModel(container.authRepository, container.sessionStore) }
        }
    }
}

class RegisterViewModel(
    private val auth: com.fpclient.android.data.repository.AuthRepository,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** true when the pending registration has been started and the user should enter the code. */
    private val _awaitingCode = MutableStateFlow(false)
    val awaitingCode = _awaitingCode.asStateFlow()

    private val _registrationStatus = MutableStateFlow<RegistrationUi?>(null)
    val registrationStatus = _registrationStatus.asStateFlow()

    private val _verified = MutableStateFlow<Boolean?>(null)
    val verified = _verified.asStateFlow()

    /**
     * Loads the registration rules of the *current* instance. Driven by the register route
     * keyed on the server URL, so it re-runs after the user switches instance.
     */
    fun loadStatus() {
        viewModelScope.launch {
            val status = auth.registrationStatus()
            if (status is ApiResult.Success) {
                _registrationStatus.value = RegistrationUi(
                    enabled = status.data.enabled,
                    passwordRequired = status.data.passwordRequired,
                )
            }
        }
    }

    fun start(
        username: String, email: String, password: String, displayName: String?,
        bio: String?, timezone: String?, registrationPassword: String?,
    ) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            val result = auth.startRegistration(
                RegisterRequest(
                    username = username,
                    email = email,
                    password = password,
                    displayName = displayName,
                    bio = bio,
                    timezone = timezone,
                    registrationPassword = registrationPassword,
                ),
            )
            if (result is ApiResult.Success) {
                _awaitingCode.value = true
            } else if (result is ApiResult.Error) {
                _error.value = result.message ?: "Registration failed"
            }
            _busy.value = false
        }
    }

    fun verify(email: String, code: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            val result = auth.verifyRegistration(email, code)
            if (result is ApiResult.Success) _verified.value = true
            else if (result is ApiResult.Error) _error.value = result.message ?: "Verification failed. Try again."
            _busy.value = false
        }
    }

    fun resend(email: String) {
        viewModelScope.launch {
            auth.resendRegistrationCode(email)
            _error.value = "If the email matches, a new code was sent."
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { RegisterViewModel(container.authRepository) }
        }
    }
}

data class RegistrationUi(
    val enabled: Boolean = true,
    val passwordRequired: Boolean = false,
)

class PasswordResetViewModel(
    private val auth: com.fpclient.android.data.repository.AuthRepository,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _requested = MutableStateFlow(false)
    val requested = _requested.asStateFlow()

    private val _success = MutableStateFlow<Boolean?>(null)
    val success = _success.asStateFlow()

    fun request(usernameOrEmail: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            val result = auth.requestPasswordReset(usernameOrEmail)
            if (result is ApiResult.Success) _requested.value = true
            else if (result is ApiResult.Error) _error.value = result.message ?: "Request failed"
            _busy.value = false
        }
    }

    fun confirm(token: String, newPassword: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            val result = auth.confirmPasswordReset(token, newPassword)
            if (result is ApiResult.Success) _success.value = true
            else if (result is ApiResult.Error) _error.value = result.message ?: "Reset failed"
            _busy.value = false
        }
    }

        companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { PasswordResetViewModel(container.authRepository) }
        }
    }
}