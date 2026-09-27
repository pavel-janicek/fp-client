package com.fpclient.android.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the live ntfy socket is doing, for Settings → Push to render honestly (Iteration 8j).
 *
 * The old card told the user to install the ntfy app and could therefore not say anything
 * about a connection it did not own. Now that FP Client *is* the subscriber, the socket state
 * is first-class: the user can see whether the thing they switched on is actually working,
 * which is the difference between "instant delivery" and a switch that silently does nothing.
 *
 * [Stopped] is the honest default — the service only runs while a session is signed in and
 * owns an `instantEnabled` subscription, so a guest or a switched-off account genuinely has
 * no connection and the card must not imply otherwise.
 */
sealed interface InstantDeliveryState {

    /** No service running: the user has not switched instant delivery on for this account. */
    data object Stopped : InstantDeliveryState

    /** The service is up and dialing; nothing has been received on this connection yet. */
    data object Connecting : InstantDeliveryState

    /** Stream established at [since] (epoch millis). [lastMessageAt] is the newest delivery. */
    data class Connected(val since: Long, val lastMessageAt: Long? = null) : InstantDeliveryState

    /**
     * The stream dropped (or ntfy asked for a re-poll) and a retry is scheduled.
     * [nextRetryInMs] is the current backoff, so the card can say "retrying" without guessing.
     */
    data class Retrying(val attempts: Int, val nextRetryInMs: Long, val lastError: String?) :
        InstantDeliveryState
}

/**
 * Process-wide bus for [InstantDeliveryState], the 8j counterpart of `TrackRecordingBus`.
 *
 * A singleton flow rather than something persisted: the socket state is *live process state*,
 * it is meaningless across process death (a restarted service starts at [Stopped] and walks
 * to [Connecting] again), and both the service and the Settings card live in the same
 * process while the card is visible.
 */
object InstantDeliveryBus {

    private val _state = MutableStateFlow<InstantDeliveryState>(InstantDeliveryState.Stopped)

    /** The current socket state; the Settings card collects this. */
    val state: StateFlow<InstantDeliveryState> = _state.asStateFlow()

    /** Publishes a transition. Internal to the app; the UI only reads. */
    internal fun publish(state: InstantDeliveryState) {
        _state.value = state
    }

    /**
     * Returns to [Stopped]. Called when the service is destroyed, and by the "stop" control,
     * so a visible card never shows a socket that is not there.
     */
    internal fun reset() {
        _state.value = InstantDeliveryState.Stopped
    }

    /** The newest delivery timestamp in the current state, or null when nothing arrived. */
    fun lastMessageAt(): Long? = when (val s = _state.value) {
        is InstantDeliveryState.Connected -> s.lastMessageAt
        else -> null
    }
}
