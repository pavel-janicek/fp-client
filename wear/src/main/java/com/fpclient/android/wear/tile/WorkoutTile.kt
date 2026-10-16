package com.fpclient.android.wear.tile

import android.content.Context
import android.content.Intent
import androidx.wear.tiles.TileService
import com.fpclient.android.wear.recording.WorkoutActivityType

/**
 * What a tap on the quick-start tile asks [com.fpclient.android.wear.WearMainActivity] to do. The
 * tile can only launch the activity, not start recording itself: a location foreground service
 * may not be started from the background, and missing permissions need a screen to ask on.
 */
sealed interface WorkoutTileAction {
    data class Start(val type: WorkoutActivityType) : WorkoutTileAction
    data object ChooseActivity : WorkoutTileAction
    data object OpenWorkout : WorkoutTileAction

    fun encode(): String = when (this) {
        is Start -> "$START_PREFIX${type.name}"
        ChooseActivity -> CHOOSE
        OpenWorkout -> OPEN
    }

    companion object {
        const val EXTRA = "com.fpclient.android.wear.TILE_ACTION"
        private const val START_PREFIX = "start:"
        private const val CHOOSE = "choose"
        private const val OPEN = "open"

        fun decode(value: String?): WorkoutTileAction? = when {
            value == null -> null
            value.startsWith(START_PREFIX) -> WorkoutActivityType.entries
                .firstOrNull { it.name == value.removePrefix(START_PREFIX) }
                ?.let(::Start)
            value == CHOOSE -> ChooseActivity
            value == OPEN -> OpenWorkout
            else -> null
        }

        fun from(intent: Intent?): WorkoutTileAction? = decode(intent?.getStringExtra(EXTRA))
    }
}

/** Asks the system to re-render the tile; call whenever the workout state or the last type changes. */
fun requestWorkoutTileUpdate(context: Context) {
    runCatching { TileService.getUpdater(context).requestUpdate(FitPubTileService::class.java) }
}
