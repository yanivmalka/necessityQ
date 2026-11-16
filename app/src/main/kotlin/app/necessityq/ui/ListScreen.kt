package app.necessityq.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.Category
import app.necessityq.domain.DeliveryStatus
import app.necessityq.domain.ItemStats
import app.necessityq.domain.ItemTag
import app.necessityq.domain.ItemTags
import app.necessityq.domain.ListLine
import app.necessityq.domain.ListMode
import app.necessityq.domain.ListOrdering
import app.necessityq.domain.ListSort
import app.necessityq.domain.Quantity
import app.necessityq.domain.StoreType
import app.necessityq.domain.Suggestion
import app.necessityq.domain.TagKind
import app.necessityq.platform.DisplayOptions
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** One row of the needed-items list: a group header or a product tile. */
private sealed interface ListRow {
    val key: String

    data class Header(val category: Category, val count: Int, val collapsed: Boolean) : ListRow {
        override val key: String = "h_" + category.name
    }

    data class Item(val line: ListLine) : ListRow {
        override val key: String = line.id
    }
}

private fun buildRows(lines: List<ListLine>, sort: ListSort, ranks: Map<String, Double>, collapsed: Set<String>): List<ListRow> =
    ListOrdering.arrange(lines, sort, ranks).flatMap { (category, items) ->
        if (category == null) {
            items.map { ListRow.Item(it) }
        } else {
            val isCollapsed = category.name in collapsed
            listOf(ListRow.Header(category, items.size, isCollapsed)) + if (isCollapsed) emptyList() else items.map { ListRow.Item(it) }
        }
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
    vm.receipts.collectAsStateWithLifecycle() // recompose ticks when receipts change
    vm.unreadTotal.collectAsStateWithLifecycle() // recompose unread counts after reading
    val names by vm.memberNames.collectAsStateWithLifecycle()
    val pickRanks by vm.pickRanks.collectAsStateWithLifecycle()
    val collapsed by vm.collapsed.collectAsStateWithLifecycle()
    val storePrompt by vm.storePrompt.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val currentStoreId by vm.currentStoreId.collectAsStateWithLifecycle()
    var openItem by remember { mutableStateOf<ListLine?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBin by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var showStoreDialog by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    val shopping = mode == ListMode.SHOP

    // Local copy of the rows so a drag can move items immediately; re-synced from the live data when idle.
    val rows = remember { mutableStateListOf<ListRow>() }
    var dragging by remember { mutableStateOf(false) }
    val built = remember(snapshot?.lines, sort, pickRanks, collapsed) {
        buildRows(snapshot?.lines.orEmpty(), sort, pickRanks, collapsed)
    }
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
    val storeName = stores.firstOrNull { it.id == currentStoreId }?.name

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { DrawerButton() },
            title = {
                Column {
                    Text(householdName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (shopping) {
                        Text(
                            (storeName?.let { "קונים ב$it" } ?: "בקנייה") + " ✎",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showStoreDialog = true },
                        )
                    } else {
                        Text(mode.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            actions = {
                if (shopping) {
                    IconButton(onClick = { addOpen = !addOpen }) { Icon(Icons.Default.Add, contentDescription = "הוספת פריט") }
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
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        // Quick add: always open while preparing; behind the + button while shopping
        if (!shopping || addOpen || query.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
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

            if (shopping && storePrompt) {
                item(key = "store_prompt") { StorePromptCard(vm) }
            }

            if (!shopping && snap.runningLow.isNotEmpty()) {
                item(key = "low_title") { SimpleTitle("כנראה נגמר בקרוב") }
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
                        SimpleTitle("קונים בדרך כלל")
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
                        "משכו שמאלה: נקנה · ימינה: " + (if (shopping) "חסר" else "מחיקה") + " — והחזיקו עד שהטבעת מהבהבת",
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(rows, key = { it.key }) { row ->
                ReorderableItem(reorderState, key = row.key) { isDragging ->
                    when (row) {
                        is ListRow.Header -> GroupHeader(row, onToggle = { vm.toggleCollapsed(row.category.name) })
                        is ListRow.Item -> {
                            val line = row.line
                            val stats = snap.stats[line.itemId]
                            val thread = messages.filter { it.itemId == line.itemId }
                            ProductTile(
                                line = line,
                                shopping = shopping,
                                subline = tileInfo(line, stats, names, display, today),
                                tags = ItemTags.of(line, stats, favorite = line.itemId in favorites)
                                    .filter { display.showFavorite || it.kind != TagKind.FAVORITE },
                                chat = if (thread.isEmpty()) null else {
                                    val last = thread.maxBy { it.atMillis }
                                    ChatState(
                                        unread = vm.unreadCount(line.itemId, messages),
                                        lastIsMine = last.authorId == vm.me,
                                        status = if (last.authorId == vm.me) vm.deliveryStatus(last) else null,
                                    )
                                },
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
                                onRightSwipe = { if (shopping) vm.setMissing(line, !line.missing) else vm.delete(line) },
                                onPlus = { vm.adjustQuantity(line, +1) },
                                onMinus = { vm.adjustQuantity(line, -1) },
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
    if (showBin) RecycleBinSheet(snapshot?.removed.orEmpty(), vm, onDismiss = { showBin = false })
    if (showStoreDialog) {
        AlertDialog(
            onDismissRequest = { showStoreDialog = false },
            title = { Text("איפה קונים?") },
            text = { StoreChooser(vm, allowNone = true, onChosen = { id -> vm.answerStorePrompt(id); showStoreDialog = false }) },
            confirmButton = { TextButton(onClick = { showStoreDialog = false }) { Text("סגירה") } },
        )
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

// ---------- store: once at the start of a trip, optional, editable from the title ----------

@Composable
private fun StorePromptCard(vm: AppViewModel) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, null)
                Spacer(Modifier.width(8.dp))
                Text("איפה קונים היום?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.answerStorePrompt(null, skipped = true) }) { Text("דלג") }
            }
            Text("לא חובה. עוזר ללמוד את סדר האיסוף בכל חנות. אפשר לשנות בהמשך מהכותרת.", style = MaterialTheme.typography.bodySmall)
            StoreChooser(vm, allowNone = false, onChosen = { vm.answerStorePrompt(it) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreChooser(vm: AppViewModel, allowNone: Boolean, onChosen: (String?) -> Unit) {
    val stores by vm.stores.collectAsStateWithLifecycle()
    val currentId by vm.currentStoreId.collectAsStateWithLifecycle()
    val nearby by vm.nearbyStore.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(vm.hasLocationPermission()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result.values.any { it }
        if (hasPermission) vm.refreshLocation()
    }
    LaunchedEffect(stores, hasPermission) { if (hasPermission) vm.refreshLocation() }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        nearby?.let { near ->
            AssistChip(
                onClick = { onChosen(near.id) },
                label = { Text("ליד: ${near.name}", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)) },
            )
        }
        stores.filter { it.id != nearby?.id }.forEach { store ->
            FilterChip(selected = store.id == currentId, onClick = { onChosen(store.id) }, label = { Text(store.name) })
        }
        AssistChip(onClick = { adding = true }, label = { Text("חנות חדשה כאן") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
        if (allowNone && currentId != null) AssistChip(onClick = { onChosen(null) }, label = { Text("ללא חנות") })
        if (!hasPermission) {
            TextButton(onClick = {
                permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }) { Text("הצעה לפי מיקום") }
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
                        StoreType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
                    }
                    Text(
                        if (hasPermission) "המיקום הנוכחי יישמר כדי להציע את החנות כשתגיעו אליה."
                        else "בלי הרשאת מיקום החנות תישמר בלי מיקום.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.addStore(name, type)
                    vm.answerStorePrompt(vm.currentStoreId.value, skipped = true)
                    adding = false
                }) { Text("שמירה") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("ביטול") } },
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
            if (removed.isEmpty()) Text("הסל ריק.", Modifier.padding(vertical = 24.dp))
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(removed, key = { "r_" + it.id }) { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.displayName, fontWeight = FontWeight.Bold)
                            Text(
                                listOfNotNull(line.removedReason?.label, line.removedAtMillis?.let { binTime(it) }, line.quantity?.format())
                                    .joinToString(" · "),
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

private fun tileInfo(line: ListLine, stats: ItemStats?, names: Map<String, String>, display: DisplayOptions, today: LocalDate): String =
    listOfNotNull(
        line.note,
        if (display.showLastBought) daysAgo(stats, today) else null,
        if (display.showFrequency) cadence(stats) else null,
        if (display.showAddedBy) names[line.addedBy]?.let { "הוסיף/ה: $it" } else null,
    ).joinToString(" · ")

private fun recommendationInfo(s: Suggestion, stats: ItemStats?, favorites: Set<String>, display: DisplayOptions, today: LocalDate): String =
    listOfNotNull(
        if (display.showReason) s.reason else null,
        if (display.showLastBought && !display.showReason) daysAgo(stats, today) else null,
        if (display.showFrequency && !display.showReason) cadence(stats) else null,
        if (display.showFavorite && s.itemId in favorites) "★ במועדפים" else null,
    ).joinToString(" · ")

// ---------- building blocks ----------

@Composable
private fun SimpleTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Category header; tap to collapse or expand the group. */
@Composable
private fun GroupHeader(row: ListRow.Header, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).clickable(onClick = onToggle)
            .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(row.category)))
        Spacer(Modifier.width(8.dp))
        Text(row.category.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text("${row.count}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.weight(1f))
        Icon(
            if (row.collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (row.collapsed) "הרחבת הקבוצה" else "כיווץ הקבוצה",
            tint = MaterialTheme.colorScheme.outline,
        )
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

/** Conversation state of an item shown on its tile. */
data class ChatState(val unread: Int, val lastIsMine: Boolean, val status: DeliveryStatus?)

private const val HOLD_MS = 650

private class SwipeAction(val label: String, val icon: ImageVector, val color: Color, val run: () -> Unit)

/**
 * Physical-direction swipe: left = bought, right = missing (shopping) / delete (preparing).
 * The icon and label sit at the edge of the phone the tile was pulled away from. The action only
 * runs if the tile is held past the threshold until the ring fills and blinks; pulling back cancels.
 */
@Composable
private fun SwipeableTile(left: SwipeAction, right: SwipeAction, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1f) }
    val offset = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    var armedAt by remember { mutableStateOf<Long?>(null) }
    val threshold = width * 0.3f
    val past = abs(offset.value) >= threshold
    LaunchedEffect(past) {
        if (past) {
            armedAt = System.currentTimeMillis()
            ring.snapTo(0f)
            ring.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
        } else {
            armedAt = null
            ring.snapTo(0f)
        }
    }
    val ready = past && ring.value >= 1f
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(260), RepeatMode.Reverse),
        label = "blinkAlpha",
    )

    Box(Modifier.onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
        if (offset.value != 0f) {
            val toRight = offset.value > 0
            val action = if (toRight) right else left
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(20.dp)).background(action.color).padding(horizontal = 18.dp),
                contentAlignment = if (toRight) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight,
            ) {
                // ring pinned to the phone edge, label beside it
                CompositionLocalProvider(LocalLayoutDirection provides if (toRight) LayoutDirection.Ltr else LayoutDirection.Rtl) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.alpha(if (ready) blink else 1f)) {
                            CircularProgressIndicator(
                                progress = { if (past) ring.value else 0f },
                                modifier = Modifier.size(44.dp),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f),
                                strokeWidth = 3.dp,
                            )
                            Icon(action.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                !past -> action.label
                                !ready -> "החזיקו…"
                                else -> "שחררו: ${action.label}"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .absoluteOffset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-width, width)) }
                    },
                    onDragStopped = {
                        val held = armedAt?.let { System.currentTimeMillis() - it >= HOLD_MS } == true
                        val stillPast = abs(offset.value) >= width * 0.3f
                        if (held && stillPast) (if (offset.value > 0) right else left).run()
                        offset.animateTo(0f, tween(220))
                    },
                ),
        ) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductTile(
    line: ListLine,
    shopping: Boolean,
    subline: String,
    tags: List<ItemTag>,
    chat: ChatState?,
    showPrices: Boolean,
    lifted: Boolean,
    dragHandle: (@Composable () -> Unit)?,
    onBought: () -> Unit,
    onRightSwipe: () -> Unit,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onOpen: () -> Unit,
) {
    val right = if (shopping) {
        SwipeAction(if (line.missing) "ביטול חסר" else "חסר", Icons.Default.ReportProblem, Color(0xFFE8590C), onRightSwipe)
    } else {
        SwipeAction("מחיקה", Icons.Default.Delete, Color(0xFFD92D20), onRightSwipe)
    }
    val left = SwipeAction("נקנה", Icons.Default.Check, Color(0xFF00875A), onBought)

    Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) {
        SwipeableTile(left = left, right = right) {
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
                    Box(Modifier.width(6.dp).fillMaxHeight().background(categoryColor(line.category)))
                    Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            dragHandle?.invoke()
                            val checkSize = if (shopping) 48.dp else 40.dp
                            Box(Modifier.size(checkSize).clip(CircleShape).clickable(onClick = onBought), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(checkSize - 16.dp).border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape))
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(line.displayName, style = ProductNameStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (tags.isNotEmpty() || chat != null) {
                                    FlowRow(
                                        Modifier.padding(top = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        tags.forEach { TagBadge(it) }
                                        chat?.let { ChatIndicator(it) }
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
                            QuantityStepper(line.quantity, onMinus, onPlus)
                        }
                    }
                }
            }
        }
        // Reserved strip under the tile for a promotion from the price database (none yet).
        PromoStrip(promo = null)
    }
}

/** Ticks for my last message: o written · ✓ sent · ✓✓ received (grey) · ✓✓ read (blue). */
@Composable
fun DeliveryTicks(status: DeliveryStatus) {
    val readBlue = Color(0xFF1570EF)
    val grey = MaterialTheme.colorScheme.outline
    val (text, color) = when (status) {
        DeliveryStatus.PENDING -> "o" to grey
        DeliveryStatus.SENT -> "✓" to grey
        DeliveryStatus.DELIVERED -> "✓✓" to grey
        DeliveryStatus.READ -> "✓✓" to readBlue
    }
    Text(text, color = color, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodySmall)
}

/** On the tile only when the item has a conversation: new-message count, or ticks of my last message. */
@Composable
private fun ChatIndicator(state: ChatState) {
    val active = state.unread > 0
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 7.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(13.dp), tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            active -> {
                Spacer(Modifier.width(3.dp))
                Text("${state.unread}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            state.lastIsMine && state.status != null -> {
                Spacer(Modifier.width(3.dp))
                DeliveryTicks(state.status)
            }
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
        Text(quantity?.format() ?: "—", fontWeight = FontWeight.ExtraBold, modifier = Modifier.widthIn(min = 36.dp), textAlign = TextAlign.Center)
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

/** Coloured pill: routine green, occasional blue, special purple, favourite gold, missing orange, recipe teal. */
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
