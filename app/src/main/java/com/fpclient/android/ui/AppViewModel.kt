package com.fpclient.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.UnitSystems
import com.fpclient.android.data.dto.UserDto
import com.fpclient.android.data.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared app state: session + unit preference used for formatting across screens. */
class AppViewModel(
    private val sessionStore: SessionStore,
) : ViewModel() {

    /** Unit system explicitly picked in Settings during this session — wins over everything. */
    private val _manualUnit = MutableStateFlow<String?>(null)

    /** Unit system advertised by the user's server profile (seed only, see [onProfileLoaded]). */
    private val _serverUnit = MutableStateFlow<String?>(null)

    private val _profile = MutableStateFlow<UserDto?>(null)
    val profile: StateFlow<UserDto?> = _profile.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            sessionStore.session.collect { _loaded.value = true }
        }
    }

    /**
     * Effective unit system used for formatting across all screens. Priority:
     * 1. the choice made in Settings during this session ([setUnitSystem]),
     * 2. the choice persisted on this device (survives restarts),
     * 3. the unit system saved on the user's server profile,
     * 4. METRIC.
     */
    val unitSystem: StateFlow<String> = combine(
        sessionStore.unitSystem,
        _manualUnit,
        _serverUnit,
    ) { persisted, manual, server ->
        manual
            ?: persisted.takeIf { it.isNotBlank() }
            ?: server
            ?: UnitSystems.METRIC
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UnitSystems.METRIC)

    val uiState: StateFlow<AppUiState> = combine(
        sessionStore.session,
        _loaded,
    ) { session, isLoaded ->
        AppUiState(
            loaded = isLoaded,
            configured = session.isConfigured,
            loggedIn = session.isLoggedIn,
            guest = session.guest,
            username = session.username,
            displayName = session.displayName,
            serverUrl = session.serverUrl,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    /**
     * Seeds the unit system from the server profile. This must never overwrite the
     * user's manual choice: profile loads happen constantly (every visit to the
     * profile screen — e.g. when pressing Back from Settings — re-loads the profile),
     * and re-applying the server value here silently reverted the user's switch,
     * which made metric/imperial appear unswitchable.
     */
    fun onProfileLoaded(user: UserDto) {
        _profile.value = user
        _serverUnit.value = user.unitSystem
    }

    /**
     * Called after the user saved their profile in Edit Profile. The saved unit
     * system is an explicit user action, so it becomes authoritative and is
     * persisted locally too, keeping Settings and formatting in sync.
     */
    fun onProfileSaved(user: UserDto) {
        _profile.value = user
        user.unitSystem?.let { system ->
            _manualUnit.value = system
            viewModelScope.launch { sessionStore.setUnitSystem(system) }
        }
    }

    /** Manual unit-system switch from Settings: applied immediately and persisted on device. */
    fun setUnitSystem(system: String) {
        _manualUnit.value = system
        viewModelScope.launch { sessionStore.setUnitSystem(system) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container.sessionStore) }
        }
    }
}

data class AppUiState(
    val loaded: Boolean = false,
    val configured: Boolean = false,
    val loggedIn: Boolean = false,
    val guest: Boolean = false,
    val username: String = "",
    val displayName: String = "",
    val serverUrl: String = "",
)