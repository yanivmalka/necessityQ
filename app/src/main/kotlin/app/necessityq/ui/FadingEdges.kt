package app.necessityq.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Fades the ends of a scrolling strip out to nothing, each end only while there is more to scroll that way — so
 * a row cut at the edge of a tile (or a list cut at the edge of the screen) shows that it goes on, instead of
 * ending in a hard cut. Put it before the scroll modifier (or on the lazy list itself), so it covers the visible part.
 *
 * Works with every scroll state: [ScrollState] (horizontalScroll / verticalScroll) and the lazy lists' and grids'
 * states. A horizontal strip follows the layout direction: in Hebrew the start is on the right.
 */
fun Modifier.fadingEdges(state: ScrollableState, horizontal: Boolean, length: Dp = 24.dp): Modifier =
    this
        .then(if (horizontal) Modifier.keepSidewaysScroll() else Modifier)
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val extent = if (horizontal) size.width else size.height
            val fade = length.toPx().coerceAtMost(extent / 3)
            if (fade <= 0f) return@drawWithContent
            // backward = the start of the content (the right in Hebrew, the top of a list)
            val backwardAtLow = !horizontal || layoutDirection == LayoutDirection.Ltr
            val lowFades = if (backwardAtLow) state.canScrollBackward else state.canScrollForward
            val highFades = if (backwardAtLow) state.canScrollForward else state.canScrollBackward
            if (lowFades) {
                if (horizontal) {
                    drawRect(
                        Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = fade),
                        size = Size(fade, size.height),
                        blendMode = BlendMode.DstIn,
                    )
                } else {
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = fade),
                        size = Size(size.width, fade),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            if (highFades) {
                if (horizontal) {
                    drawRect(
                        Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - fade, endX = size.width),
                        topLeft = Offset(size.width - fade, 0f),
                        size = Size(fade, size.height),
                        blendMode = BlendMode.DstIn,
                    )
                } else {
                    drawRect(
                        Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fade, endY = size.height),
                        topLeft = Offset(0f, size.height - fade),
                        size = Size(size.width, fade),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
        }

/** A horizontal strip with its own scroll state, its ends faded while there is more to scroll ([fadingEdges]). */
fun Modifier.fadingHorizontalScroll(state: ScrollState): Modifier =
    fadingEdges(state, horizontal = true).horizontalScroll(state)

/** A vertical column with its own scroll state, its top and bottom faded while there is more to scroll. */
fun Modifier.fadingVerticalScroll(state: ScrollState): Modifier =
    fadingEdges(state, horizontal = false).verticalScroll(state)


private object SwallowSideways : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(available.x, 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity(available.x, 0f)
}

/**
 * A strip that scrolls sideways keeps the sideways drag to itself: what it cannot scroll any further (at its end)
 * is swallowed, instead of passing on to the pager that switches between the tabs (and settings tabs) and sliding
 * the whole page. Put it outside the scroll modifier ([fadingEdges] does, for every horizontal strip).
 */
fun Modifier.keepSidewaysScroll(): Modifier = nestedScroll(SwallowSideways)
