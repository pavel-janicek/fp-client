package com.fpclient.android.ui

import com.fpclient.android.data.dto.UnitSystems
import com.fpclient.android.data.dto.UserDto
import com.fpclient.android.data.session.Session
import com.fpclient.android.data.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Verifies the unit-system preference. Regression tests for the reported bug:
 * switching between metric/imperial in Settings appeared impossible because
 * [AppViewModel.onProfileLoaded] re-applied the server's value on every profile
 * load — e.g. when pressing Back from Settings, the profile screen reloaded and
 * silently reverted the switch.
 */
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sessionStoreWith(persisted: String = ""): SessionStore =
        mock(SessionStore::class.java).also {
            `when`(it.session).thenReturn(flowOf(Session(serverUrl = "https://fitpub.example")))
            `when`(it.unitSystem).thenReturn(flowOf(persisted))
        }

    @Test
    fun manualChoice_survivesProfileReload() = runTest {
        val vm = AppViewModel(sessionStoreWith())
        advanceUntilIdle()
        assertEquals(UnitSystems.METRIC, vm.unitSystem.value)

        vm.setUnitSystem(UnitSystems.IMPERIAL)
        advanceUntilIdle()
        assertEquals(UnitSystems.IMPERIAL, vm.unitSystem.value)

        // The Back-from-Settings regression: reloading the own profile brings the
        // server's stale unit system, which must NOT revert the manual choice.
        vm.onProfileLoaded(UserDto(username = "sam", unitSystem = UnitSystems.METRIC))
        advanceUntilIdle()
        assertEquals(UnitSystems.IMPERIAL, vm.unitSystem.value)
    }

    @Test
    fun persistedChoice_beatsServerProfile() = runTest {
        val vm = AppViewModel(sessionStoreWith(persisted = UnitSystems.IMPERIAL))
        advanceUntilIdle()
        assertEquals(UnitSystems.IMPERIAL, vm.unitSystem.value)

        vm.onProfileLoaded(UserDto(username = "sam", unitSystem = UnitSystems.METRIC))
        advanceUntilIdle()
        assertEquals(UnitSystems.IMPERIAL, vm.unitSystem.value)
    }

    @Test
    fun serverProfile_seedsUnitSystem_whenNoLocalChoiceExists() = runTest {
        val vm = AppViewModel(sessionStoreWith())
        advanceUntilIdle()
        assertEquals(UnitSystems.METRIC, vm.unitSystem.value)

        vm.onProfileLoaded(UserDto(username = "sam", unitSystem = UnitSystems.IMPERIAL))
        advanceUntilIdle()
        assertEquals(UnitSystems.IMPERIAL, vm.unitSystem.value)
    }

    @Test
    fun profileSave_persistsAndAppliesTheSavedUnitSystem() = runTest {
        val store = sessionStoreWith()
        val vm = AppViewModel(store)
        advanceUntilIdle()

        vm.onProfileSaved(UserDto(username = "sam", unitSystem = UnitSystems.IMPERIAL))
        advanceUntilIdle()

        assertEquals(UnitSystems.IMPERIAL, vm.unitSystem.value)
        verify(store).setUnitSystem(UnitSystems.IMPERIAL)
    }
}
