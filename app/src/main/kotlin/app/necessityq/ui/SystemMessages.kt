package app.necessityq.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** One system message: what happened to which item ("חלב הושלם"), and its own "ביטול" when it can be undone. */
class SystemMessage(val id: Long, val text: String, val undo: (() -> Unit)?)

/** How long a message stays on screen when nobody touches it. */
const val MESSAGE_VISIBLE_MS = 30_000L

/**
 * The system messages on screen, oldest first. None is dropped for a newer one: each stays until it is swiped
 * aside, undone, or its half minute is up — so every "ביטול" stays reachable.
 */
class SystemMessagesState {
    val messages = mutableStateListOf<SystemMessage>()
    private var nextId = 0L

    fun show(text: String, undo: (() -> Unit)? = null) {
        messages.add(SystemMessage(nextId++, text, undo))
    }

    fun dismiss(message: SystemMessage) {
        messages.remove(message)
    }
}

/** How many messages under the top one peek out of the stack (the rest wait, hidden, beneath them). */
private const val PEEKING = 3

/**
 * The app's system messages at the bottom of the screen, in layers: a new message lies on top of the previous
 * one instead of replacing it, and the older ones peek out above it. Swiping the top message aside (to either
 * side) closes it at once — without undoing anything — and the one beneath shows again. Each message has its
 * own "ביטול". A message goes by itself after half a minute; the time stands still while a finger is on them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemMessagesHost(state: SystemMessagesState, modifier: Modifier = Modifier) {
    var touching by remember { mutableStateOf(false) }
    val all = state.messages.toList()
    if (all.isEmpty()) return

    // each message's own half minute, from when it appeared
    all.forEach { message ->
        key(message.id) {
            LaunchedEffect(Unit) {
                var left = MESSAGE_VISIBLE_MS
                while (left > 0) {
                    delay(250)
                    if (!touching) left -= 250
                }
                state.dismiss(message)
            }
        }
    }

    val shown = all.takeLast(PEEKING + 1)
    Box(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
            // only watches the finger (does not consume it), so swipes and buttons work as usual
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        touching = event.changes.any { it.pressed }
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        shown.forEachIndexed { index, message ->
            val depth = shown.size - 1 - index
            key(message.id) {
                MessageCard(
                    message = message,
                    top = depth == 0,
                    modifier = Modifier
                        .offset(y = (-8 * depth).dp)
                        .graphicsLayer {
                            val scale = 1f - 0.04f * depth
                            scaleX = scale
                            scaleY = scale
                        }
                        .alpha(if (depth == 0) 1f else 0.92f),
                    onDismiss = { state.dismiss(message) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageCard(message: SystemMessage, top: Boolean, modifier: Modifier, onDismiss: () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                dismiss()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        // only the top message is swiped; the ones beneath wait their turn
        enableDismissFromStartToEnd = top,
        enableDismissFromEndToStart = top,
        backgroundContent = { Box(Modifier) },
    ) {
        Snackbar(
            action = message.undo?.let { undo ->
                {
                    TextButton(
                        onClick = {
                            undo()
                            dismiss()
                        },
                        enabled = top,
                        colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                    ) { Text("ביטול", fontWeight = FontWeight.SemiBold) }
                }
            },
        ) { Text(message.text) }
    }
}
