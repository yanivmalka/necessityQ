package app.necessityq.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.flow.first
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset

/*
 * Page changes, as in WhatsApp for a right-to-left app: a page that opens slides in from the left edge
 * (moving left → right) over the page it came from, and a page that closes or goes back slides out to the
 * left (moving right → left), uncovering the page beneath. The page underneath drifts a quarter of the way
 * in the opposite direction, so the two read as stacked sheets rather than a flat swap.
 *
 * Slide offsets are screen pixels (they are not mirrored by the RTL layout), so "-width" is always the
 * left edge.
 */

private const val PAGE_SLIDE_MILLIS = 300

private fun slideSpec() = tween<IntOffset>(PAGE_SLIDE_MILLIS, easing = FastOutSlowInEasing)

private fun fadeSpec() = tween<Float>(PAGE_SLIDE_MILLIS, easing = FastOutSlowInEasing)

/** A page opening: it comes in from the left. */
val PageEnter: EnterTransition = slideInHorizontally(slideSpec()) { -it }

/** A page closing: it leaves to the left. */
val PageExit: ExitTransition = slideOutHorizontally(slideSpec()) { -it }

/**
 * The [ContentTransform] for an [androidx.compose.animation.AnimatedContent] that switches pages.
 * [back] = the change returns to an earlier page; the closing page then stays on top while it slides away.
 * Every page shown this way needs an opaque background, since the two overlap while they move.
 */
fun <S> AnimatedContentTransitionScope<S>.pageSlide(back: Boolean): ContentTransform =
    if (!back) {
        ContentTransform(
            targetContentEnter = PageEnter,
            initialContentExit = slideOutHorizontally(slideSpec()) { it / 4 } + fadeOut(fadeSpec(), targetAlpha = 0.6f),
            targetContentZIndex = 1f,
            sizeTransform = null,
        )
    } else {
        ContentTransform(
            targetContentEnter = slideInHorizontally(slideSpec()) { it / 4 } + fadeIn(fadeSpec(), initialAlpha = 0.6f),
            initialContentExit = PageExit,
            targetContentZIndex = -1f,
            sizeTransform = null,
        )
    }

/**
 * A full-screen page in a window of its own (a צ'אט פוד conversation, a past conversation) that opens and
 * closes with the same slide as every other page. Hold one with [rememberSlidingPage], show it with
 * [SlidingPageDialog], and close it with [SlidingPageState.close]: the page slides away first and only then
 * is the window removed.
 */
@Stable
class SlidingPageState internal constructor() {
    internal val shown = MutableTransitionState(false).apply { targetState = true }

    /** Slides the page out; the owner hears about it once it is off screen. */
    fun close() {
        shown.targetState = false
    }
}

@Composable
fun rememberSlidingPage(onClosed: () -> Unit): SlidingPageState {
    val state = remember { SlidingPageState() }
    val latestOnClosed by rememberUpdatedState(onClosed)
    LaunchedEffect(state) {
        snapshotFlow { !state.shown.targetState && state.shown.isIdle }.first { it }
        latestOnClosed()
    }
    return state
}

@Composable
fun SlidingPageDialog(state: SlidingPageState, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = state::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            // the page brings its own slide; the window itself appears and disappears at once, without a dim
            (view.parent as? DialogWindowProvider)?.window?.apply {
                setWindowAnimations(0)
                setDimAmount(0f)
            }
        }
        // a window of its own starts without the app's ink: give it the theme's, so text is light on the dark theme
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            AnimatedVisibility(
                visibleState = state.shown,
                enter = PageEnter,
                exit = PageExit,
                modifier = Modifier.fillMaxSize(),
            ) { content() }
        }
    }
}
