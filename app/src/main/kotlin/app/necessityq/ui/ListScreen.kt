package app.necessityq.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import app.necessityq.domain.ListMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.Category
import app.necessityq.domain.ItemMessage
import app.necessityq.domain.ItemTag
import app.necessityq.domain.ItemTags
import app.necessityq.domain.TagKind
import app.necessityq.domain.ItemStats
import app.necessityq.domain.LineStatus
import app.necessityq.domain.ListLine
import app.necessityq.domain.ListOrdering
import app.necessityq.domain.ListSort
import app.necessityq.domain.Quantity
import app.necessityq.domain.StoreType
import app.necessityq.domain.Suggestion
import app.necessityq.platform.DisplayOptions
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** One row of the needed-items list: a group header or a product tile. */
private sealed interface ListRow {
    val key: String

    data class Header(val category: Category?, val title: String) : ListRow {
        override val key: String = "h_" + (category?.name ?: "all")
    }

    data class Item(val line: ListLine) : ListRow {
        override val key: String = line.id
    }
}

private fun buildRows(lines: List<ListLine>, sort: ListSort, ranks: Map<String, Double>): List<ListRow> =
    ListOrdering.arrange(lines, sort, ranks).flatMap { (category, items) ->
        val header = if (category != null) listOf(ListRow.Header(category, category.label)) else emptyList()
        header + items.map { ListRow.Item(it) }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListScreen(vm: AppViewModel, householdName: String, modifier: Modifier = Modifier) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val display by vm.display.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val messages by vm.itemMessages.collectAsStateWithLifecycle()
    val names by vm.memberNames.collectAsStateWithLifecycle()
    val pickRanks by vm.pickRanks.collectAsStateWithLifecycle()
    var openItem by remember { mutableStateOf<ListLine?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDisplay by remember { mutableStateOf(false) }
    var showBin by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    val shopping = mode == ListMode.SHOP

    // Local copy of the rows so a drag can move items immediately; re-synced from the live data when idle.
    val rows = remember { mutableStateListOf<ListRow>() }
    var dragging by remember { mutableStateOf(false) }
    val built = remember(snapshot?.lines, sort, pickRanks) { buildRows(snapshot?.lines.orEmpty(), sort, pickRanks) }
    LaunchedEffect(built, dragging) {
        if (!dragging) {
            rows.clear()
            rows.addAll(built)
        }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = rows.indexOfFirst { it.key == from.key }
        val toIndex = rows.indexOfFirst { it.key == to.key }
        if (fromIndex >= 0 && toIndex >= 0) rows.add(toIndex, rows.removeAt(fromIndex))
    }

    fun persistMove(lineId: String) {
        val index = rows.indexOfFirst { it.key == lineId }
        val line = (rows.getOrNull(index) as? ListRow.Item)?.line ?: return
        val header = rows.subList(0, index).lastOrNull { it is ListRow.Header } as? ListRow.Header
        val category = header?.category ?: line.category
        val previous = (rows.getOrNull(index - 1) as? ListRow.Item)?.line
        val next = (rows.getOrNull(index + 1) as? ListRow.Item)?.line
        vm.moveLine(line, category, previous, next)
    }

    val today = LocalDate.now()
    val sortOptions = if (shopping) listOf(ListSort.GROUPS, ListSort.ALPHA, ListSort.PICK) else listOf(ListSort.GROUPS, ListSort.ALPHA, ListSort.ADDED)

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(householdName, fontWeight = FontWeight.ExtraBold) },
            actions = {
                if (shopping) {
                    IconButton(onClick = { showScan = true }) { Icon(Icons.Default.QrCodeScanner, contentDescription = "בדיקת מחיר בסריקה") }
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "מיון") }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        sortOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text((if (option == sort) "✓ " else "") + option.label) },
                                onClick = { vm.setSort(option); showSortMenu = false },
                            )
                        }
                    }
                }
                IconButton(onClick = { showBin = true }) {
                    BadgedBox(badge = { snapshot?.removed?.size?.takeIf { it > 0 }?.let { Badge { Text("$it") } } }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "סל מחזור")
                    }
                }
                IconButton(onClick = { showDisplay = true }) { Icon(Icons.Default.Tune, contentDescription = "מה להציג") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        // usage stage: preparing the list vs. walking the shop — each shows what matters for it
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            ListMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { vm.setMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, ListMode.entries.size),
                    icon = {},
                ) {
                    Text(m.label, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Quick add: always open while preparing; tucked behind a button while shopping
        if (!shopping || addOpen || query.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("הוספה מהירה: 2 קוטג' 5%") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.submitQuery() }),
                trailingIcon = {
                    IconButton(onClick = vm::submitQuery, enabled = query.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "הוסף")
                    }
                },
            )
        }
        if (completions.isNotEmpty()) {
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                completions.forEach { item ->
                    SuggestionChip(onClick = { vm.pickCompletion(item) }, label = { Text(item.displayName) })
                }
            }
        }
        StoreBar(vm, prominent = shopping, onAddItem = if (shopping && !addOpen) ({ addOpen = true }) else null)

        val snap = snapshot
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (snap == null) {
                item { Text("טוען…", Modifier.padding(16.dp)) }
                return@LazyColumn
            }

            if (!shopping && snap.runningLow.isNotEmpty()) {
                item(key = "low_title") { SectionTitle("כנראה נגמר בקרוב") }
                items(snap.runningLow, key = { "low_" + it.itemId }) { s ->
                    RunningLowCard(
                        s,
                        info = recommendationInfo(s, snap.stats[s.itemId], favorites, display, today),
                        onAdd = { vm.accept(s) },
                        onDismiss = { vm.dismiss(s) },
                    )
                }
            }

            if (!shopping && snap.frequent.isNotEmpty()) {
                item(key = "frequent") {
                    Column {
                        SectionTitle("קונים בדרך כלל")
                        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            snap.frequent.forEach { s ->
                                val star = display.showFavorite && s.itemId in favorites
                                AssistChip(
                                    onClick = { vm.accept(s) },
                                    label = { Text((if (star) "★ " else "") + s.identity.displayName) },
                                    leadingIcon = { Icon(Icons.Default.Add, null) },
                                    shape = RoundedCornerShape(50),
                                )
                            }
                        }
                    }
                }
            }

            if (rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (shopping) "אין מה לקנות — הרשימה ריקה." else "הרשימה ריקה. הוסיפו מוצרים בשורה למעלה, או בהקשה על אחת ההצעות.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                item(key = "hint") {
                    Text(
                        "החליקו והחזיקו רגע: לצד אחד נקנה, לשני מחיקה" + if (sort == ListSort.GROUPS) " · ≡ לגרירה" else "",
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(rows, key = { it.key }) { row ->
                ReorderableItem(reorderState, key = row.key) { isDragging ->
                    when (row) {
                        is ListRow.Header -> SectionTitle(row.title, row.category)
                        is ListRow.Item -> {
                            val line = row.line
                            val stats = snap.stats[line.itemId]
                            ProductTile(
                                line = line,
                                shopping = shopping,
                                subline = tileInfo(line, stats, favorites, names, display, today),
                                tags = ItemTags.of(line, stats, favorite = line.itemId in favorites)
                                    .filter { display.showFavorite || it.kind != TagKind.FAVORITE },
                                unread = vm.unreadCount(line.itemId, messages),
                                hasMessages = messages.any { it.itemId == line.itemId },
                                showPrices = display.showPrices,
                                lifted = isDragging,
                                dragHandle = if (sort == ListSort.GROUPS) {
                                    {
                                        Icon(
                                            Icons.Default.DragIndicator,
                                            contentDescription = "גרירה",
                                            tint = MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.draggableHandle(
                                                onDragStarted = { dragging = true },
                                                onDragStopped = {
                                                    persistMove(line.id)
                                                    dragging = false
                                                },
                                            ).padding(2.dp),
                                        )
                                    }
                                } else null,
                                onBought = { vm.checkOff(line) },
                                onMissing = { vm.setMissing(line, !line.missing) },
                                onPlus = { vm.adjustQuantity(line, +1) },
                                onMinus = { vm.adjustQuantity(line, -1) },
                                onDelete = { vm.delete(line) },
                                onOpen = { openItem = line },
                            )
                        }
                    }
                }
            }

            if (snap.bought.isNotEmpty()) {
                item(key = "bought_header") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("נקנו (${snap.bought.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::clearBought) { Text("נקה לסל המחזור") }
                    }
                }
                items(snap.bought, key = { "b_" + it.id }) { line ->
                    BoughtRow(line, onReopen = { vm.reopen(line) })
                }
            }
        }
    }

    openItem?.let { line ->
        ItemSheet(vm, itemId = line.itemId, itemName = line.displayName, onDismiss = { openItem = null })
    }
    if (showDisplay) {
        DisplayOptionsDialog(display, onChange = vm::setDisplay, onDismiss = { showDisplay = false })
    }
    if (showBin) {
        RecycleBinSheet(snapshot?.removed.orEmpty(), vm, onDismiss = { showBin = false })
    }
    if (showScan) {
        AlertDialog(
            onDismissRequest = { showScan = false },
            icon = { Icon(Icons.Default.QrCodeScanner, null) },
            title = { Text("בדיקת מחיר בסריקה") },
            text = {
                Text(
                    "סריקת הברקוד במצלמה תציג את המחיר והמבצע בסניף שבו אתם נמצאים (לפי המיקום). " +
                        "האפשרות תופעל כשיחובר מאגר מחירים עדכני.",
                )
            },
            confirmButton = { TextButton(onClick = { showScan = false }) { Text("הבנתי") } },
        )
    }
}

// ---------- recycle bin ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecycleBinSheet(removed: List<ListLine>, vm: AppViewModel, onDismiss: () -> Unit) {
    var confirmEmpty by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("סל מחזור", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (removed.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("ריקון") }
            }
            Text(
                "פריטים שנמחקו, ופריטים שסומנו כנקנו ונוקו מהרשימה.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (removed.isEmpty()) {
                Text("הסל ריק.", Modifier.padding(vertical = 24.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(removed, key = { "r_" + it.id }) { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.displayName, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(
                                    line.removedReason?.label,
                                    line.removedAtMillis?.let { binTime(it) },
                                    line.quantity?.format(),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = { vm.restoreToList(line) }) {
                            Icon(Icons.Default.RestoreFromTrash, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("לרשימה")
                        }
                    }
                }
            }
        }
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("לרוקן את סל המחזור?") },
            text = { Text("${removed.size} פריטים יימחקו לצמיתות. היסטוריית הקניות לא נפגעת.") },
            confirmButton = { TextButton(onClick = { vm.emptyBin(); confirmEmpty = false }) { Text("ריקון") } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("ביטול") } },
        )
    }
}

private fun binTime(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d.M HH:mm"))

/** Accent colour per category: a thin strip on each tile and a dot on the group header. */
fun categoryColor(category: Category): Color = when (category) {
    Category.PRODUCE -> Color(0xFF2F9E44)
    Category.DAIRY -> Color(0xFF1C7ED6)
    Category.BAKERY -> Color(0xFFE8890C)
    Category.MEAT_FISH -> Color(0xFFE03131)
    Category.PANTRY -> Color(0xFF9C6B30)
    Category.BREAKFAST_SNACKS -> Color(0xFFD6336C)
    Category.DRINKS -> Color(0xFF1098AD)
    Category.FROZEN -> Color(0xFF4DABF7)
    Category.CLEANING -> Color(0xFF7048E8)
    Category.TOILETRIES -> Color(0xFFAE3EC9)
    Category.BABY -> Color(0xFFF783AC)
    Category.OTHER -> Color(0xFF868E96)
}

// ---------- tile ----------

private const val HOLD_MS = 650

/**
 * Modern product tile. Swiping reveals an icon in the coloured area; the action only happens if the
 * tile is held past the threshold until the ring around the icon fills, so a brushed swipe never acts.
 * Every action can be undone from the snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProductTile(
    line: ListLine,
    shopping: Boolean,
    subline: String,
    tags: List<ItemTag>,
    unread: Int,
    hasMessages: Boolean,
    showPrices: Boolean,
    lifted: Boolean,
    dragHandle: (@Composable () -> Unit)?,
    onBought: () -> Unit,
    onMissing: () -> Unit,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    var widthPx by remember { mutableStateOf(1f) }
    var armedAt by remember { mutableStateOf<Long?>(null) }
    val ring = remember { Animatable(0f) }
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) return@rememberSwipeToDismissBoxState true
            val held = armedAt?.let { System.currentTimeMillis() - it >= HOLD_MS } == true
            if (held) {
                if (value == SwipeToDismissBoxValue.StartToEnd) onBought() else onDelete()
            }
            false // the tile always springs back; the data change re-renders the list
        },
        positionalThreshold = { it * 0.3f },
    )
    // arm when the tile is pulled past the threshold; disarm when it goes back
    LaunchedEffect(swipe) {
        snapshotFlow { abs(runCatching { swipe.requireOffset() }.getOrDefault(0f)) >= widthPx * 0.3f }
            .distinctUntilChanged()
            .collect { past ->
                if (past) {
                    armedAt = System.currentTimeMillis()
                    ring.snapTo(0f)
                    ring.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                } else {
                    armedAt = null
                    ring.snapTo(0f)
                }
            }
    }

    Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) {
        SwipeToDismissBox(
            state = swipe,
            modifier = Modifier.onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) },
            backgroundContent = {
                val direction = swipe.dismissDirection
                if (direction == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
                val toBought = direction == SwipeToDismissBoxValue.StartToEnd
                val color = if (toBought) Color(0xFF00875A) else Color(0xFFD92D20)
                val ready = ring.value >= 1f
                Row(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(color).padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (toBought) Arrangement.Start else Arrangement.End,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { ring.value },
                            modifier = Modifier.size(44.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                            strokeWidth = 3.dp,
                        )
                        Icon(
                            if (toBought) Icons.Default.Check else Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            !ready -> "החזיקו…"
                            toBought -> "שחררו: נקנה"
                            else -> "שחררו: מחיקה"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            },
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                border = if (line.missing) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        lifted -> MaterialTheme.colorScheme.primaryContainer
                        line.missing -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (lifted) 10.dp else 1.5.dp),
            ) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    // category accent strip
                    Box(Modifier.width(6.dp).fillMaxHeight().background(categoryColor(line.category)))
                    Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            dragHandle?.invoke()
                            // round check button — larger while shopping
                            val checkSize = if (shopping) 48.dp else 40.dp
                            Box(
                                Modifier.size(checkSize).clip(CircleShape).clickable(onClick = onBought),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(checkSize - 16.dp)
                                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(line.displayName, style = ProductNameStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (tags.isNotEmpty() || hasMessages) {
                                    FlowRow(
                                        Modifier.padding(top = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        tags.forEach { TagBadge(it) }
                                        if (hasMessages) ChatBadge(unread)
                                    }
                                }
                            }
                            if (showPrices) PriceSlot(price = null, average = null)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text(
                                subline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (shopping) {
                                // shopping: quick "missing / ask about a substitute" instead of delete
                                IconButton(onClick = onMissing, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        Icons.Default.ReportProblem, contentDescription = "חסר",
                                        tint = if (line.missing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "שאלה / תחליף", tint = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                            QuantityStepper(line.quantity, onMinus, onPlus)
                            if (!shopping) {
                                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "מחיקה", tint = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        // Reserved strip under the tile for a promotion from the price database (none yet).
        PromoStrip(promo = null)
    }
}

@Composable
private fun ChatBadge(unread: Int) {
    val active = unread > 0
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 7.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Chat, null, Modifier.size(13.dp),
            tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (active) {
            Spacer(Modifier.width(3.dp))
            Text("$unread", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---------- info lines ----------

private fun daysAgo(stats: ItemStats?, today: LocalDate): String? {
    val last = stats?.lastPurchase ?: return null
    val days = ChronoUnit.DAYS.between(last, today)
    return when {
        days <= 0 -> "נקנה היום"
        days == 1L -> "נקנה אתמול"
        else -> "נקנה לפני $days ימים"
    }
}

private fun cadence(stats: ItemStats?): String? =
    stats?.cadenceDays?.let { "בערך כל ${it.roundToInt().coerceAtLeast(1)} ימים" }

private fun tileInfo(
    line: ListLine,
    stats: ItemStats?,
    favorites: Set<String>,
    names: Map<String, String>,
    display: DisplayOptions,
    today: LocalDate,
): String = listOfNotNull(
    line.note,
    if (display.showLastBought) daysAgo(stats, today) else null,
    if (display.showFrequency) cadence(stats) else null,
    if (display.showAddedBy) names[line.addedBy]?.let { "הוסיף/ה: $it" } else null,
).joinToString(" · ")

private fun recommendationInfo(
    s: Suggestion,
    stats: ItemStats?,
    favorites: Set<String>,
    display: DisplayOptions,
    today: LocalDate,
): String = listOfNotNull(
    if (display.showReason) s.reason else null,
    if (display.showLastBought && !display.showReason) daysAgo(stats, today) else null,
    if (display.showFrequency && !display.showReason) cadence(stats) else null,
    if (display.showFavorite && s.itemId in favorites) "★ במועדפים" else null,
).joinToString(" · ")

// ---------- building blocks ----------

@Composable
private fun SectionTitle(text: String, category: Category? = null) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (category != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(category)))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RunningLowCard(s: Suggestion, info: String, onAdd: () -> Unit, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.identity.displayName + (s.quantity?.let { " · " + it.format() } ?: ""), style = ProductNameStyle)
                if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "לא עכשיו") }
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "הוסף לרשימה") }
        }
    }
}

/** Current price and household/market average; filled once a live price source is connected. */
@Composable
fun PriceSlot(price: Double?, average: Double?) {
    Column(
        Modifier.widthIn(min = 68.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(price?.let { "₪%.2f".format(it) } ?: "₪ —", fontWeight = FontWeight.ExtraBold)
        Text(
            "ממוצע " + (average?.let { "₪%.2f".format(it) } ?: "—"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Promotion line right under a product tile; renders nothing until there is a promotion. */
@Composable
fun PromoStrip(promo: String?) {
    if (promo == null) return
    Text(
        "🏷 $promo",
        Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            .background(Color(0xFFFFF1B8), RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        fontWeight = FontWeight.Bold,
        color = Color(0xFF5C4400),
    )
}

@Composable
fun QuantityStepper(quantity: Quantity?, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinus, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Remove, contentDescription = "פחות", Modifier.size(20.dp)) }
        Text(
            quantity?.format() ?: "—",
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.widthIn(min = 36.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onPlus, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Add, contentDescription = "יותר", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun BoughtRow(line: ListLine, onReopen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onReopen).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = "החזרה לרשימה", tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(
            line.displayName,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = TextDecoration.LineThrough,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f),
        )
        line.quantity?.let { Text(it.format(), color = MaterialTheme.colorScheme.outline) }
    }
}

// ---------- store bar ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreBar(vm: AppViewModel, prominent: Boolean, onAddItem: (() -> Unit)?) {
    val stores by vm.stores.collectAsStateWithLifecycle()
    val currentId by vm.currentStoreId.collectAsStateWithLifecycle()
    val nearby by vm.nearbyStore.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(vm.hasLocationPermission()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result.values.any { it }
        if (hasPermission) vm.refreshLocation()
    }
    LaunchedEffect(stores, hasPermission) { if (hasPermission) vm.refreshLocation() }
    val current = stores.firstOrNull { it.id == currentId }

    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            FilterChip(
                selected = current != null,
                onClick = { menu = true },
                label = { Text(current?.let { "קונים ב: ${it.name}" } ?: "איפה קונים?") },
                leadingIcon = { Icon(Icons.Default.Storefront, null, Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                stores.forEach { store ->
                    DropdownMenuItem(
                        text = { Text("${store.name} · ${store.type.label}") },
                        onClick = { vm.selectStore(store.id); menu = false },
                    )
                }
                DropdownMenuItem(text = { Text("הוספת חנות כאן…") }, onClick = { menu = false; adding = true })
                if (current != null) DropdownMenuItem(text = { Text("ללא חנות") }, onClick = { vm.selectStore(null); menu = false })
            }
        }
        val near = nearby
        if (near != null && near.id != currentId) {
            AssistChip(
                onClick = { vm.selectStore(near.id) },
                label = { Text("אתם ליד ${near.name} — לבחור?") },
                leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)) },
            )
        } else if (!hasPermission && prominent) {
            TextButton(onClick = {
                permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }) { Text("מיקום להצעת חנות") }
        }
        if (onAddItem != null) {
            AssistChip(onClick = onAddItem, label = { Text("הוספת פריט") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(StoreType.SUPERMARKET) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("חנות חדשה") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("שם (למשל: רמי לוי תלפיות)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StoreType.entries.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                        }
                    }
                    Text(
                        if (hasPermission) "המיקום הנוכחי יישמר כדי להציע את החנות כשתגיעו אליה."
                        else "בלי הרשאת מיקום החנות תישמר בלי מיקום.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.addStore(name, type); adding = false }) { Text("שמירה") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("ביטול") } },
        )
    }
}

// ---------- display options ----------

@Composable
private fun DisplayOptionsDialog(current: DisplayOptions, onChange: (DisplayOptions) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("מה להציג ברשימה ובהמלצות") },
        text = {
            Column {
                OptionRow("סיבת ההמלצה", current.showReason) { onChange(current.copy(showReason = it)) }
                OptionRow("מתי נקנה לאחרונה", current.showLastBought) { onChange(current.copy(showLastBought = it)) }
                OptionRow("תדירות קנייה", current.showFrequency) { onChange(current.copy(showFrequency = it)) }
                OptionRow("סימון מועדפים ★", current.showFavorite) { onChange(current.copy(showFavorite = it)) }
                OptionRow("מי הוסיף", current.showAddedBy) { onChange(current.copy(showAddedBy = it)) }
                OptionRow("מקום למחיר ולמחיר ממוצע", current.showPrices) { onChange(current.copy(showPrices = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגירה") } },
    )
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Suppress("unused")
private fun unreadFor(itemId: String, all: List<ItemMessage>, me: String, readAt: Long) =
    all.count { it.itemId == itemId && it.authorId != me && it.atMillis > readAt }

/** Coloured badge: routine green, occasional blue, special purple, favourite gold, missing orange, recipe teal. */
@Composable
fun TagBadge(tag: ItemTag) {
    val (bg, fg) = when (tag.kind) {
        TagKind.ROUTINE -> Color(0xFFDDF4E8) to Color(0xFF05603A)
        TagKind.OCCASIONAL -> Color(0xFFE0EAFF) to Color(0xFF2D3FA8)
        TagKind.SPECIAL -> Color(0xFFF1E4FF) to Color(0xFF6B21A8)
        TagKind.FAVORITE -> Color(0xFFFFF0C2) to Color(0xFF7A4D00)
        TagKind.MISSING -> Color(0xFFFFE2D6) to Color(0xFF9A3412)
        TagKind.RECIPE -> Color(0xFFD5F5F0) to Color(0xFF0F5F55)
    }
    Text(
        tag.label,
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 1.dp),
        color = fg,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
    )
}
