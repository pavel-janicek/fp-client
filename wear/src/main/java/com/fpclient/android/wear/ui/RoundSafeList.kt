package com.fpclient.android.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.PositionIndicatorState
import androidx.wear.compose.material.PositionIndicatorVisibility
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The scrolling list of every Wear screen, built so that nothing can be cut off by a round
 * display, whatever the scroll position (Play's Wear review, "Watch shapes": versionCode 68 and
 * again 70, where the Body sensors message and the Stop button ran into the circle mid-scroll).
 *
 * Fixed insets cannot guarantee that: an item that is safe in the middle of the screen is wider
 * than the circle's chord near the top and bottom. So each item is fitted geometrically, every
 * frame: [TransformingLazyColumn] tells the item where it is on screen, [roundFitScale] works out
 * the largest scale at which the item's whole bounding box lies inside the circle (minus a small
 * margin), and the item is drawn at that scale around its centre, fading out as it gets small.
 * The bounding box is a conservative stand-in for any shape (a stadium button's rounded ends fit
 * inside it), so the guarantee holds for text, buttons and chips alike. Square screens are left
 * as they are; [listContentPadding] keeps their usual 8dp inset.
 */
@Composable
internal fun rememberRoundSafeListState(): TransformingLazyColumnState = rememberTransformingLazyColumnState()

/** Content scope of [RoundSafeList]: every item is fitted inside the round display. */
internal class RoundSafeListScope(
    private val scope: TransformingLazyColumnScope,
    private val fit: RoundFit?,
) {
    fun item(key: Any? = null, content: @Composable () -> Unit) {
        scope.item(key = key) {
            val itemScope = this
            Box(
                contentAlignment = Alignment.Center,
                modifier = if (fit == null) {
                    Modifier
                } else {
                    Modifier.graphicsLayer {
                        val progress = with(itemScope) { scrollProgress }
                        if (progress.isUnspecified) return@graphicsLayer
                        val fraction = progress.bottomOffsetFraction - progress.topOffsetFraction
                        if (fraction <= 0f || size.height <= 0f) return@graphicsLayer
                        // The list fills the screen, so its height is the display's.
                        val containerHeight = size.height / fraction
                        val centerY = (progress.topOffsetFraction + progress.bottomOffsetFraction) / 2f * containerHeight
                        val scale = roundFitScale(
                            itemWidth = size.width,
                            itemHeight = size.height,
                            centerOffsetY = centerY - containerHeight / 2f,
                            radius = fit.radiusPx(containerHeight),
                        )
                        scaleX = scale
                        scaleY = scale
                        alpha = roundFitAlpha(scale)
                        transformOrigin = TransformOrigin.Center
                    }
                },
            ) {
                content()
            }
        }
    }
}

/** The circle items are fitted into: the display's, less [EDGE_MARGIN_FRACTION]. */
internal class RoundFit(private val screenWidthPx: Float) {
    fun radiusPx(containerHeightPx: Float): Float =
        minOf(screenWidthPx, containerHeightPx) / 2f * (1f - EDGE_MARGIN_FRACTION)
}

/** Breathing room between an item and the circle: 3% of the radius (≈3.4dp on a 227dp watch). */
internal const val EDGE_MARGIN_FRACTION = 0.03f

/**
 * Largest scale in [0, 1] at which a box of [itemWidth] × [itemHeight], horizontally centred and
 * with its centre [centerOffsetY] from the circle's centre, lies wholly inside a circle of
 * [radius]. The box's outer corners are the binding points: (s·w/2)² + (|d| + s·h/2)² ≤ r².
 */
internal fun roundFitScale(itemWidth: Float, itemHeight: Float, centerOffsetY: Float, radius: Float): Float {
    val halfWidth = itemWidth / 2f
    val halfHeight = itemHeight / 2f
    val distance = abs(centerOffsetY)
    if (distance >= radius) return 0f
    val a = halfWidth * halfWidth + halfHeight * halfHeight
    if (a <= 0f) return 1f
    val b = distance * halfHeight
    val c = distance * distance - radius * radius // < 0 here
    val scale = (-b + sqrt(b * b - a * c)) / a
    return scale.coerceIn(0f, 1f)
}

/** Fully opaque down to 70% scale, then fading out by 40%. */
internal fun roundFitAlpha(scale: Float): Float = ((scale - 0.4f) / 0.3f).coerceIn(0f, 1f)

/**
 * A [TransformingLazyColumn] whose items are kept inside a round display ([RoundSafeListScope]).
 * [verticalAlignment] places content shorter than the screen (Top, or CenterVertically for a
 * landing screen).
 */
@Composable
internal fun RoundSafeList(
    state: TransformingLazyColumnState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = listContentPadding(),
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: RoundSafeListScope.() -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val fit = remember(configuration.isScreenRound, configuration.screenWidthDp, density.density) {
        if (configuration.isScreenRound) RoundFit(configuration.screenWidthDp * density.density) else null
    }
    TransformingLazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp, verticalAlignment),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoundSafeListScope(this, fit).content()
    }
}

/** The curved scrollbar Play's Wear review requires, for a [RoundSafeList]. */
@Composable
internal fun RoundSafeListPositionIndicator(state: TransformingLazyColumnState) {
    val adapter = remember(state) { TransformingLazyColumnIndicatorState(state) }
    PositionIndicator(
        state = adapter,
        indicatorHeight = 50.dp,
        indicatorWidth = 4.dp,
        // PositionIndicatorDefaults.horizontalPadding (internal in Wear Material 1.7).
        paddingHorizontal = 2.dp,
    )
}

/**
 * [PositionIndicatorState] for a [TransformingLazyColumnState], by item index like the library's
 * own adapter for LazyColumn (Wear Material 2 has none for this list).
 */
private class TransformingLazyColumnIndicatorState(
    private val state: TransformingLazyColumnState,
) : PositionIndicatorState {
    override val positionFraction: Float
        get() {
            val info = state.layoutInfo
            if (info.visibleItems.isEmpty()) return 0f
            val first = decimalFirstIndex()
            val fromEnd = info.totalItemsCount - decimalLastIndex()
            return if (first + fromEnd == 0f) 0f else first / (first + fromEnd)
        }

    override fun sizeFraction(scrollableContainerSizePx: Float): Float {
        val total = state.layoutInfo.totalItemsCount
        if (total == 0) return 1f
        return ((decimalLastIndex() - decimalFirstIndex()) / total).coerceIn(0f, 1f)
    }

    override fun visibility(scrollableContainerSizePx: Float): PositionIndicatorVisibility =
        if (sizeFraction(scrollableContainerSizePx) < 0.999f) {
            if (state.isScrollInProgress) PositionIndicatorVisibility.Show else PositionIndicatorVisibility.AutoHide
        } else {
            PositionIndicatorVisibility.Hide
        }

    private fun decimalFirstIndex(): Float {
        val first = state.layoutInfo.visibleItems.firstOrNull() ?: return 0f
        val height = first.transformedHeight.coerceAtLeast(1)
        return first.index - first.offset.coerceAtMost(0).toFloat() / height
    }

    private fun decimalLastIndex(): Float {
        val info = state.layoutInfo
        val last = info.visibleItems.lastOrNull() ?: return 0f
        val height = last.transformedHeight.coerceAtLeast(1)
        val visible = (info.viewportSize.height - last.offset).coerceIn(1, height)
        return last.index + visible.toFloat() / height
    }

    override fun equals(other: Any?): Boolean = (other as? TransformingLazyColumnIndicatorState)?.state == state
    override fun hashCode(): Int = state.hashCode()
}
