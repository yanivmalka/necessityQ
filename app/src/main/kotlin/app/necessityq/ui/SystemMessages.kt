package app.necessityq.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

/**
 * The app's system messages ("קוטג' הועבר לסל המחזור", "סומן כנקנה" …) at the bottom of the screen. Like a
 * notification, a message can be swiped aside (to either side) to close it at once, without waiting for it to go
 * and without undoing anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        // a fresh swipe state per message, so the next one does not appear already swiped away
        key(data) {
            val swipe = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) {
                        data.dismiss()
                        true
                    } else {
                        false
                    }
                },
            )
            SwipeToDismissBox(state = swipe, backgroundContent = { Box(Modifier) }) {
                Snackbar(data)
            }
        }
    }
}
