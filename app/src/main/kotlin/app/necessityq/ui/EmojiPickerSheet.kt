package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.necessityq.domain.EmojiCatalog
import kotlinx.coroutines.launch

/**
 * All the emoji, opened from the "+" of the reaction bar, as in WhatsApp: a sheet of limited height with
 * the categories along the top and the emoji scrolling beneath them; the category in view is lit, and a
 * tap on a category jumps to it. [current] is my reaction now (shown marked).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val categories = EmojiCatalog.categories
    // grid positions: each category is a full-width title followed by its emoji
    val starts = remember {
        var index = 0
        categories.map { c -> index.also { index += 1 + c.emojis.size } }
    }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val inView by remember {
        derivedStateOf { starts.indexOfLast { it <= grid.firstVisibleItemIndex }.coerceAtLeast(0) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                categories.forEachIndexed { i, c ->
                    val lit = i == inView
                    Box(
                        Modifier.size(42.dp)
                            .background(if (lit) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                            .clickable { scope.launch { grid.animateScrollToItem(starts[i]) } }
                            .semantics { contentDescription = c.name },
                        contentAlignment = Alignment.Center,
                    ) { Text(c.icon, fontSize = 22.sp) }
                }
            }
            HorizontalDivider(Modifier.padding(top = 6.dp), color = NqTheme.colors.hairline)
            // a limited strip, as in WhatsApp: the sheet stays low and the emoji scroll inside it
            LazyVerticalGrid(
                columns = GridCells.Adaptive(46.dp),
                state = grid,
                modifier = Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 8.dp).fadingEdges(grid, horizontal = false),
            ) {
                categories.forEach { c ->
                    item(key = "title:${c.name}", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            c.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    c.emojis.forEach { emoji ->
                        item(key = emoji) {
                            Box(
                                Modifier.size(46.dp)
                                    .background(if (emoji == current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                                    .clickable { onPick(emoji) }
                                    .semantics { contentDescription = "תגובה $emoji" },
                                contentAlignment = Alignment.Center,
                            ) { Text(emoji, fontSize = 26.sp) }
                        }
                    }
                }
            }
        }
    }
}
