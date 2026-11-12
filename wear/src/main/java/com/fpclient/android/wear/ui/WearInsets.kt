package com.fpclient.android.wear.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Round-screen insets shared by every scrolling Wear screen (Play review of versionCode 68:
 * "Watch shapes — no text or controls cut off by the screen edges").
 *
 * Wear's layout guidance insets list content by 5.2% of the screen width on round displays, so
 * buttons and chips stay inside the circle. Wrapping text needs more: it is laid out full width
 * and, once scrolled towards the top or bottom of the circle, its line ends fall off the curve
 * (the Body sensors message on the workout screen). Text items therefore add a further inset,
 * landing them inside ~12% of the width per side — the circle's chord at about a sixth of the
 * screen height from the edge. Square watches keep the previous fixed 8dp.
 */
private const val LIST_INSET_FRACTION = 0.052f
private const val TEXT_EXTRA_INSET_FRACTION = 0.07f
private val SQUARE_INSET = 8.dp

@Composable
private fun roundFraction(fraction: Float, square: Dp): Dp {
    val configuration = LocalConfiguration.current
    return if (configuration.isScreenRound) (configuration.screenWidthDp * fraction).dp else square
}

/** Horizontal padding for a [androidx.wear.compose.material.ScalingLazyColumn]'s contentPadding. */
@Composable
internal fun listHorizontalInset(): Dp = roundFraction(LIST_INSET_FRACTION, SQUARE_INSET)

/**
 * [listHorizontalInset] on both sides plus the given vertical padding. On a round screen the
 * bottom gets at least [ROUND_END_PADDING_FRACTION] of the screen height: [RoundSafeList] shrinks
 * items near the edge so they fit the circle, and this room lets the last button (Stop, Grant
 * sensor access) scroll up to where it is nearly full size instead of staying small at the edge.
 * A centred screen whose content fits (Home) passes [roomForLastItem] = false: the extra room
 * would push its centred content up and the title under the clock.
 */
@Composable
internal fun listContentPadding(
    top: Dp = 10.dp,
    bottom: Dp = 10.dp,
    roomForLastItem: Boolean = true,
): PaddingValues {
    val horizontal = listHorizontalInset()
    val configuration = LocalConfiguration.current
    val end = if (configuration.isScreenRound && roomForLastItem) {
        maxOf(bottom, (configuration.screenHeightDp * ROUND_END_PADDING_FRACTION).dp)
    } else {
        bottom
    }
    return PaddingValues(start = horizontal, end = horizontal, top = top, bottom = end)
}

private const val ROUND_END_PADDING_FRACTION = 0.2f

/** Extra inset for multi-line text inside the list, on top of [listHorizontalInset]. */
@Composable
internal fun textExtraInset(): Dp = roundFraction(TEXT_EXTRA_INSET_FRACTION, 0.dp)
