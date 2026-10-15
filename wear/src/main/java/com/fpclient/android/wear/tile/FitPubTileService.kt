package com.fpclient.android.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TypeBuilders.StringLayoutConstraint
import androidx.wear.protolayout.TypeBuilders.StringProp
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.Chip
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.MultiButtonLayout
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.fpclient.android.wear.R
import com.fpclient.android.wear.WearLog
import com.fpclient.android.wear.WearMainActivity
import com.fpclient.android.wear.recording.WorkoutActivityType
import com.fpclient.android.wear.recording.WorkoutActivityUsageStore
import com.fpclient.android.wear.recording.WorkoutSessionSnapshot
import com.fpclient.android.wear.recording.WorkoutSessionStore
import com.fpclient.android.wear.recording.WorkoutStatus
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.time.Instant

/**
 * Quick-start tile, the counterpart of Strava's "Start Workout" tile.
 *
 * - No workout: a big start button for the last-used activity and a button for the activity list.
 * - Recording: the activity and its moving time. The time is a platform-evaluated expression, so
 *   it ticks on the tile without the app pushing updates (tiles refresh rarely and on the system's
 *   schedule). Paused shows the frozen time.
 *
 * Every tap opens [WearMainActivity] with a [WorkoutTileAction]; the service asks for a re-render
 * via [requestWorkoutTileUpdate] whenever the session changes.
 */
class FitPubTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val device = requestParams.deviceConfiguration
        val session = WorkoutSessionStore(this).load()
            ?.takeIf { it.status == WorkoutStatus.RECORDING || it.status == WorkoutStatus.PAUSED }
        WearLog.d(TAG, "onTileRequest session=${session?.status}")
        val layout = if (session != null) workoutLayout(device, session) else idleLayout(device)
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(Timeline.fromLayoutElement(layout))
                .build(),
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(ICON_PLAY, image(R.drawable.ic_tile_play))
                .addIdToImageMapping(ICON_LIST, image(R.drawable.ic_tile_list))
                .build(),
        )

    private fun idleLayout(device: DeviceParameters): LayoutElement {
        val type = WorkoutActivityUsageStore(this).lastUsedType()
        val start = Button.Builder(this, launch("start", WorkoutTileAction.Start(type)))
            .setIconContent(ICON_PLAY)
            .setButtonColors(ButtonColors.primaryButtonColors(COLORS))
            .setContentDescription("Start ${type.label}")
            .build()
        val choose = Button.Builder(this, launch("choose", WorkoutTileAction.ChooseActivity))
            .setIconContent(ICON_LIST)
            .setButtonColors(ButtonColors.secondaryButtonColors(COLORS))
            .setContentDescription("Choose activity")
            .build()
        return PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(label(getString(R.string.tile_label), Typography.TYPOGRAPHY_CAPTION1, COLORS.primary))
            .setSecondaryLabelTextContent(label(type.label, Typography.TYPOGRAPHY_TITLE3, COLORS.onSurface))
            .setContent(MultiButtonLayout.Builder().addButtonContent(start).addButtonContent(choose).build())
            .build()
    }

    private fun workoutLayout(device: DeviceParameters, session: WorkoutSessionSnapshot): LayoutElement {
        val paused = session.status == WorkoutStatus.PAUSED
        val time = if (paused) {
            Text.Builder(this, formatMinutesSeconds(session.accumulatedMovingMs))
        } else {
            // Moving time = accumulated + (now - resumedAt), i.e. the time since a virtual start.
            val resumedAt = session.resumedAtEpochMs ?: System.currentTimeMillis()
            val virtualStart = Instant.ofEpochMilli(resumedAt - session.accumulatedMovingMs)
            val elapsed = DynamicInstant.withSecondsPrecision(virtualStart)
                .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
            val twoDigits = DynamicInt32.IntFormatter.Builder().setMinIntegerDigits(2).build()
            val text = elapsed.toIntMinutes().format()
                .concat(DynamicString.constant(":"))
                .concat(elapsed.secondsPart.format(twoDigits))
            Text.Builder(
                this,
                StringProp.Builder(formatMinutesSeconds(session.movingMsAt(System.currentTimeMillis())))
                    .setDynamicValue(text)
                    .build(),
                StringLayoutConstraint.Builder("000:00").build(),
            )
        }.setTypography(Typography.TYPOGRAPHY_DISPLAY2)
            .setColor(argb(if (paused) COLORS.onSurface else COLORS.primary))
            .build()
        val open = Chip.Builder(this, launch("open", WorkoutTileAction.OpenWorkout), device)
            .setPrimaryLabelContent("Open")
            .setChipColors(ChipColors.secondaryChipColors(COLORS))
            .build()
        return PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(label(session.activityType.label, Typography.TYPOGRAPHY_CAPTION1, COLORS.primary))
            .setSecondaryLabelTextContent(label(if (paused) "Paused" else "Recording", Typography.TYPOGRAPHY_CAPTION2, COLORS.onSurface))
            .setContent(time)
            .setPrimaryChipContent(open)
            .build()
    }

    private fun label(text: String, typography: Int, color: Int): LayoutElement =
        Text.Builder(this, text).setTypography(typography).setColor(argb(color)).build()

    private fun launch(id: String, action: WorkoutTileAction): Clickable =
        Clickable.Builder()
            .setId(id)
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(WearMainActivity::class.java.name)
                            .addKeyToExtraMapping(
                                WorkoutTileAction.EXTRA,
                                ActionBuilders.AndroidStringExtra.Builder().setValue(action.encode()).build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun image(resId: Int) = ResourceBuilders.ImageResource.Builder()
        .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(resId).build())
        .build()

    private companion object {
        const val TAG = "FitPubTile"
        const val RESOURCES_VERSION = "1"
        const val ICON_PLAY = "play"
        const val ICON_LIST = "list"

        /** FitPub green on black, matching FitPubWearTheme. */
        val COLORS = Colors(0xFF2E9E52.toInt(), 0xFF000000.toInt(), 0xFF303133.toInt(), 0xFFFFFFFF.toInt())

        /** Same "M:SS" (total minutes) as the workout screen's duration readout. */
        fun formatMinutesSeconds(ms: Long): String {
            val totalSeconds = ms / 1000L
            return "%d:%02d".format(java.util.Locale.ROOT, totalSeconds / 60L, totalSeconds % 60L)
        }
    }
}
