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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
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
import androidx.compose.material3.InputChip
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.Category
import app.necessityq.domain.Dish
import app.necessityq.domain.RecipeListSync
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.runtime.mutableIntStateOf
import app.necessityq.domain.GenerateMode
import app.necessityq.domain.LineStatus
import app.necessityq.domain.ListFilter
import app.necessityq.domain.QuickEntryParser
import app.necessityq.domain.ListReset
import app.necessityq.domain.ListSection
import app.necessityq.domain.PriceEstimate
import app.necessityq.domain.ResetScope
import app.necessityq.domain.SourceFilter
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
import app.necessityq.platform.TapMode
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** One row of the needed-items list: a group header or a product tile. */
private sealed interface ListRow {
    val key: String

    data class Header(val sectionKey: String, val title: String, val category: Category?, val count: Int, val collapsed: Boolean) : ListRow {
        override val key: String = "h_$sectionKey"
    }

    data class Item(val line: ListLine) : ListRow {
        override val key: String = line.id
    }
}

private fun buildSections(
    lines: List<ListLine>, sort: ListSort, ranks: Map<String, Double>, descending: Boolean, chilledLast: Boolean,
    filter: ListFilter, chatItemIds: Set<String>,
) = ListOrdering.arrange(filter.apply(lines, chatItemIds), sort, ranks, descending, chilledLast)

private fun buildRows(sections: List<ListSection>, collapsed: Set<String>): List<ListRow> =
    sections.flatMap { section ->
        val title = section.title
        if (title == null) {
            section.lines.map { ListRow.Item(it) }
        } else {
            val isCollapsed = section.key in collapsed
            listOf(ListRow.Header(section.key, title, section.category, section.lines.size, isCollapsed)) +
                if (isCollapsed) emptyList() else section.lines.map { ListRow.Item(it) }
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListScreen(
    vm: AppViewModel,
    householdName: String,
    modifier: Modifier = Modifier,
    onOpenMessages: () -> Unit = {},
    onOpenDisplaySettings: () -> Unit = {},
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    val onlineCompletions by vm.onlineCompletions.collectAsStateWithLifecycle()
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
    val descending by vm.descending.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val prices by vm.prices.collectAsStateWithLifecycle()
    val itemPrices by vm.itemPriceInfo.collectAsStateWithLifecycle()
    val unreadTotal by vm.unreadTotal.collectAsStateWithLifecycle()
    var openItem by remember { mutableStateOf<ListLine?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showGenerate by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showEmpty by remember { mutableStateOf(false) }
    /** Tiles whose controls are open (tap mode "controls"). */
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var showBin by remember { mutableStateOf(false) }
    val scan = rememberBarcodeScan(vm) { vm.onScanned(it) }
    var showStoreDialog by remember { mutableStateOf(false) }
    var openOther by remember { mutableStateOf<Pair<String, String>?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    val shopping = mode == ListMode.SHOP

    // Local copy of the rows so a drag can move items immediately; re-synced from the live data when idle.
    val rows = remember { mutableStateListOf<ListRow>() }
    var dragging by remember { mutableStateOf(false) }
    val chatItemIds = remember(messages) { messages.map { it.itemId }.toSet() }
    // the quick-add box also searches the list: what is typed narrows the open lines (on top of the filter)
    val searchText = remember(query) { if (query.isBlank()) "" else QuickEntryParser.parse(query)?.item?.displayName ?: query.trim() }
    val listFilter = remember(filter, searchText) { if (searchText.isBlank() || filter.text.isNotBlank()) filter else filter.copy(text = searchText) }
    val sections = remember(snapshot?.lines, sort, pickRanks, descending, display.chilledLast, listFilter, chatItemIds) {
        buildSections(snapshot?.lines.orEmpty(), sort, pickRanks, descending, display.chilledLast, listFilter, chatItemIds)
    }
    val searchMatches = if (searchText.isBlank()) 0 else sections.sumOf { sec -> sec.lines.count { it.status == LineStatus.NEEDED } }
    // while filtering (by tag, group or search) every group starts open, so matches are never hidden in a
    // collapsed group; groups closed meanwhile are closed for this filter only — the saved state is untouched
    val filtering = listFilter.isActive
    var filterCollapsed by remember(listFilter) { mutableStateOf(emptySet<String>()) }
    val shownCollapsed = if (filtering) filterCollapsed else collapsed
    val built = remember(sections, shownCollapsed) { buildRows(sections, shownCollapsed) }
    val titledKeys = sections.filter { it.title != null }.map { it.key }.toSet()
    val allCollapsed = titledKeys.isNotEmpty() && titledKeys.all { it in shownCollapsed }
    LaunchedEffect(built, dragging) {
        if (!dragging) {
            rows.clear()
            rows.addAll(built)
        }
    }

    // suggestion sections: collapsed by default, hidden while typing so the list itself stays visible
    val showSuggestions = !shopping && searchText.isBlank()
    var lowExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var frequentExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    // recipes whose ingredients are on the list: their servings can be changed (or the recipe removed) from here
    val mealData by vm.meals.collectAsStateWithLifecycle()
    val showRecipes = !shopping && searchText.isBlank()
    val recipesOnList = remember(mealData.dishes, snapshot?.lines) {
        val lines = snapshot?.lines.orEmpty()
        mealData.dishes.mapNotNull { d -> RecipeListSync.onList(lines, d).size.takeIf { it > 0 }?.let { d to it } }
    }

    val listState = rememberLazyListState()
    // LazyColumn items before the product rows (store prompt, recipes, suggestions, hint) — to find a row's index
    val leadingItems = snapshot?.let { snap ->
        (if (shopping && storePrompt) 1 else 0) +
            (if (showRecipes && recipesOnList.isNotEmpty()) 1 else 0) +
            (if (showSuggestions && snap.runningLow.isNotEmpty()) 1 else 0) +
            (if (showSuggestions && snap.frequent.isNotEmpty()) 1 else 0) + 1
    } ?: 0
    val currentLeading by androidx.compose.runtime.rememberUpdatedState(leadingItems)
    val currentSections by androidx.compose.runtime.rememberUpdatedState(sections)
    // a product was just added: open its group if collapsed and scroll the list to it
    LaunchedEffect(Unit) {
        vm.added.collect { itemId ->
            val line = kotlinx.coroutines.withTimeoutOrNull(4_000) {
                androidx.compose.runtime.snapshotFlow { snapshot?.lines?.firstOrNull { it.itemId == itemId && it.status == LineStatus.NEEDED } }
                    .first { it != null }
            } ?: return@collect
            currentSections.firstOrNull { sec -> sec.lines.any { it.id == line.id } }?.let { sec ->
                if (sec.title != null && sec.key in collapsed) vm.toggleCollapsed(sec.key)
            }
            val rowIndex = kotlinx.coroutines.withTimeoutOrNull(2_000) {
                androidx.compose.runtime.snapshotFlow { rows.indexOfFirst { it.key == line.id } }.first { it >= 0 }
            } ?: return@collect
            kotlinx.coroutines.delay(50) // let the list lay out the new row
            val target = listState.layoutInfo.totalItemsCount.let { total ->
                // rows are followed by the bought section; find the row by key among the laid-out items first
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == line.id }?.index
                    ?: (currentLeading + rowIndex).coerceAtMost(maxOf(0, total - 1))
            }
            listState.animateScrollToItem(maxOf(0, target - 1))
        }
    }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
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
    val sortOptions = if (shopping) {
        listOf(ListSort.CART_LOAD, ListSort.GROUPS, ListSort.ALPHA, ListSort.PICK)
    } else {
        listOf(ListSort.GROUPS, ListSort.ALPHA, ListSort.ADDED, ListSort.CART_LOAD)
    }
    val storeName = stores.firstOrNull { it.id == currentStoreId }?.name

    val messagesButton: @Composable (Color) -> Unit = { tint ->
        // new messages, visible from the list itself
        IconButton(onClick = onOpenMessages) {
            BadgedBox(badge = { if (unreadTotal > 0) Badge { Text("$unreadTotal") } }) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = if (unreadTotal > 0) "$unreadTotal הודעות חדשות" else "הודעות",
                    tint = tint,
                )
            }
        }
    }
    val menuButton: @Composable (Color) -> Unit = { tint ->
        Box {
            IconButton(onClick = { showMore = true }) { Icon(Icons.Default.MoreVert, contentDescription = "פעולות על הרשימה", tint = tint) }
            DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                DropdownMenuItem(
                    text = { Text("יצירת רשימה מקניות עבר") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                    onClick = { showGenerate = true; showMore = false },
                )
                DropdownMenuItem(
                    text = { Text("סל מחזור" + (snapshot?.removed?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: "")) },
                    leadingIcon = { Icon(Icons.Default.RestoreFromTrash, null) },
                    onClick = { showBin = true; showMore = false },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("ריקון הרשימה") },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                    onClick = { showEmpty = true; showMore = false },
                )
                DropdownMenuItem(
                    text = { Text("איפוס הרשימה") },
                    leadingIcon = { Icon(Icons.Default.RestartAlt, null) },
                    onClick = { showReset = true; showMore = false },
                )
                DropdownMenuItem(
                    text = { Text("הגדרות תצוגה") },
                    leadingIcon = { Icon(Icons.Default.Tune, null) },
                    onClick = { showMore = false; onOpenDisplaySettings() },
                )
                DropdownMenuItem(
                    text = { Text(if (display.showBought) "הסתרת מוצרים שסומנו/נמחקו" else "הצגת מוצרים שסומנו/נמחקו") },
                    leadingIcon = { Icon(if (display.showBought) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) },
                    onClick = { showMore = false; vm.setDisplay(display.copy(showBought = !display.showBought)) },
                )
                DropdownMenuItem(
                    text = { Text("סריקת ברקוד: מחיר והוספה") },
                    leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) },
                    onClick = { showMore = false; scan() },
                )
            }
        }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val headerSnap = snapshot
        if (shopping) {
            ShopHeader(
                storeName = storeName,
                done = headerSnap?.bought?.size ?: 0,
                open = headerSnap?.lines?.count { it.status == LineStatus.NEEDED } ?: 0,
                remaining = if (display.showTotal && headerSnap != null) {
                    app.necessityq.domain.ListCost.of(headerSnap.lines, prices, itemPrices)
                } else null,
                onBack = { vm.setMode(ListMode.PLAN) },
                onStore = { showStoreDialog = true },
            ) {
                IconButton(onClick = { addOpen = !addOpen }) {
                    Icon(Icons.Default.Add, contentDescription = "הוספת פריט", tint = NqTheme.colors.onShopHeader)
                }
                messagesButton(NqTheme.colors.onShopHeader)
                menuButton(NqTheme.colors.onShopHeader)
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            householdName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("הרשימה", style = MaterialTheme.typography.titleLarge)
                    }
                    if (names.size > 1) MemberAvatars(names.values.toList())
                    messagesButton(MaterialTheme.colorScheme.onSurface)
                    menuButton(MaterialTheme.colorScheme.onSurface)
                }
                ModeSwitch(shopping = false, onSelect = vm::setMode, modifier = Modifier.padding(end = 12.dp))
            }
        }

        // Quick add: always open while preparing; behind the + button while shopping
        if (!shopping || addOpen || query.isNotEmpty()) {
            QuickAddField(
                query = query,
                onChange = { vm.query.value = it },
                onSubmit = vm::submitQuery,
                onClear = { vm.query.value = "" },
                onScan = scan,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            if (searchText.isNotBlank()) {
                QuickAddPreview(
                    parsed = remember(query) { QuickEntryParser.parse(query) },
                    matches = searchMatches,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
        }
        if (completions.isNotEmpty() || onlineCompletions.isNotEmpty()) {
            // what the household buys first, then generic items from the branch's products — two scrolling rows
            val entries = buildList<Pair<String?, app.necessityq.domain.ItemIdentity?>> {
                if (completions.isNotEmpty() && onlineCompletions.isNotEmpty()) add("שלי" to null)
                completions.forEach { add(null to it) }
                if (onlineCompletions.isNotEmpty()) add("מהסניף" to null)
                onlineCompletions.forEach { add("" to it) }
            }
            TwoRowStrip(entries, fullHeight = { it.second == null }) { (label, item) ->
                when {
                    item == null -> CompletionLabel(label.orEmpty())
                    label == null -> SuggestionChip(onClick = { vm.pickCompletion(item) }, label = { Text(item.displayName) })
                    else -> SuggestionChip(
                        onClick = { vm.pickCompletion(item) },
                        label = { Text(item.displayName) },
                        icon = { Icon(Icons.Default.Storefront, null, Modifier.size(16.dp)) },
                    )
                }
            }
        }

        // list toolbar: sort (with direction), filter, collapse/expand all
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                AssistChip(
                    onClick = { showSortMenu = true },
                    label = { Text(sort.label + if (descending) " ↓" else " ↑", maxLines = 1) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)) },
                )
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    sortOptions.forEach { option ->
                        val recommended = shopping && option == ListSort.CART_LOAD
                        DropdownMenuItem(
                            text = { Text((if (option == sort) "✓ " else "") + option.label + if (recommended) " (מומלץ)" else "") },
                            onClick = { vm.setSort(option); showSortMenu = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text((if (!descending) "✓ " else "") + "סדר עולה ↑") },
                        onClick = { vm.setDescending(false); showSortMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text((if (descending) "✓ " else "") + "סדר יורד ↓") },
                        onClick = { vm.setDescending(true); showSortMenu = false },
                    )
                }
            }
            FilterChip(
                selected = filter.isActive,
                onClick = { showFilter = true },
                label = { Text(if (filter.isActive) "סינון · ${filter.activeCount}" else "סינון") },
                leadingIcon = { Icon(Icons.Default.FilterList, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = false,
                onClick = onOpenDisplaySettings,
                label = { Text("תצוגה") },
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = "הגדרות תצוגה", modifier = Modifier.size(18.dp)) },
            )
            Spacer(Modifier.weight(1f))
            if (titledKeys.isNotEmpty()) {
                IconButton(onClick = {
                    if (filtering) filterCollapsed = if (allCollapsed) emptySet() else titledKeys
                    else vm.setAllCollapsed(if (allCollapsed) null else titledKeys)
                }) {
                    Icon(
                        if (allCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                        contentDescription = if (allCollapsed) "פתיחת כל הקבוצות" else "כיווץ כל הקבוצות",
                    )
                }
            }
        }
        // what is filtered, one removable chip per criterion
        if (filter.isActive) ActiveFilterRow(filter, onChange = vm::setFilter)

        val snap = snapshot
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
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

            if (showRecipes && recipesOnList.isNotEmpty()) {
                item(key = "recipes") {
                    RecipesOnListCard(
                        recipesOnList,
                        onServings = { dish, n -> vm.addRecipeToList(dish.id, n) },
                        onRemove = { dish -> vm.removeRecipeFromList(dish.id) },
                    )
                }
            }

            if (showSuggestions && snap.runningLow.isNotEmpty()) {
                item(key = "low") {
                    Column(Modifier.padding(bottom = 6.dp)) {
                        SuggestionHeader(
                            title = "כנראה נגמר בקרוב",
                            icon = Icons.Default.Schedule,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            count = snap.runningLow.size,
                            expanded = lowExpanded,
                            onToggle = { lowExpanded = !lowExpanded },
                            onAddAll = { snap.runningLow.forEach { vm.accept(it) } },
                        )
                        if (lowExpanded) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(snap.runningLow, key = { "low_" + it.itemId }) { s ->
                                    RunningLowCard(
                                        s,
                                        info = recommendationInfo(s, snap.stats[s.itemId], favorites, display, today),
                                        onAdd = { vm.accept(s) },
                                        onDismiss = { vm.dismiss(s) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showSuggestions && snap.frequent.isNotEmpty()) {
                item(key = "frequent") {
                    Column {
                        SuggestionHeader(
                            title = "קונים בדרך כלל",
                            icon = Icons.Default.Autorenew,
                            iconTint = MaterialTheme.colorScheme.primary,
                            count = snap.frequent.size,
                            expanded = frequentExpanded,
                            onToggle = { frequentExpanded = !frequentExpanded },
                            onAddAll = null,
                        )
                        if (frequentExpanded) {
                            TwoRowStrip(snap.frequent) { s ->
                                val star = display.showFavorite && s.itemId in favorites
                                AssistChip(
                                    onClick = { vm.accept(s) },
                                    label = { Text((if (star) "★ " else "") + s.identity.displayName) },
                                    leadingIcon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                                    shape = NqShapes.Pill,
                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, NqTheme.colors.hairline),
                                )
                            }
                        }
                    }
                }
            }

            if (rows.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            when {
                                searchText.isNotBlank() && !filter.isActive -> "\"$searchText\" לא ברשימה. ➤ או Enter יוסיפו אותו."
                                filter.isActive -> "אין פריטים שמתאימים לסינון."
                                shopping -> "אין מה לקנות — הרשימה ריקה."
                                else -> "הרשימה ריקה. הוסיפו מוצרים בשורה למעלה, בהקשה על אחת ההצעות, או צרו רשימה מקניות עבר."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (filter.isActive) {
                            TextButton(onClick = { vm.setFilter(ListFilter()) }) { Text("ניקוי הסינון") }
                        } else if (snap.stats.isNotEmpty()) {
                            FilledTonalButton(onClick = { showGenerate = true }) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("יצירת רשימה מקניות עבר")
                            }
                        }
                    }
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
                        is ListRow.Header -> GroupHeader(row, onToggle = {
                            if (filtering) {
                                filterCollapsed = if (row.sectionKey in filterCollapsed) filterCollapsed - row.sectionKey else filterCollapsed + row.sectionKey
                            } else {
                                vm.toggleCollapsed(row.sectionKey)
                            }
                        })
                        is ListRow.Item -> {
                            val line = row.line
                            val stats = snap.stats[line.itemId]
                            val thread = messages.filter { it.itemId == line.itemId }
                            ProductTile(
                                line = line,
                                shopping = shopping,
                                subline = tileInfo(line, stats, names, display, today),
                                tags = ItemTags.of(line, stats, favorite = line.itemId in favorites, compact = true)
                                    .filter { display.showFavorite || it.kind != TagKind.FAVORITE },
                                chat = if (thread.isEmpty()) null else {
                                    val last = thread.maxBy { it.atMillis }
                                    ChatState(
                                        unread = vm.unreadCount(line.itemId, messages),
                                        lastIsMine = last.authorId == vm.me,
                                        status = if (last.authorId == vm.me) vm.deliveryStatus(last) else null,
                                    )
                                },
                                display = display,
                                price = prices[line.itemId],
                                priceInfo = itemPrices[line.itemId],
                                controlsOpen = line.id in expanded,
                                lifted = isDragging,
                                dragHandle = if (sort == ListSort.GROUPS && !descending && !filter.isActive) {
                                    {
                                        Icon(
                                            Icons.Default.DragIndicator,
                                            contentDescription = "גרירה",
                                            tint = MaterialTheme.colorScheme.outline,
                                            // a long press first, so a scroll that starts on the handle scrolls the list
                                            modifier = Modifier.longPressDraggableHandle(
                                                onDragStarted = {
                                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    dragging = true
                                                },
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
                                onSetQuantity = { q, approved -> vm.changeQuantity(line, q, approved) },
                                quantityCheck = { vm.quantityCheck(line, it) },
                                onTap = {
                                    if (display.tapMode == TapMode.INFO) openItem = line
                                    else expanded = if (line.id in expanded) expanded - line.id else expanded + line.id
                                },
                                onInfo = { openItem = line },
                            )
                        }
                    }
                }
            }

            if (display.showBought && snap.bought.isNotEmpty()) {
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

        if (shopping) {
            ShopBottomBar(onScan = scan, onFinish = { vm.setMode(ListMode.PLAN) })
        } else if (snap != null) {
            // one clear way into the store, with the estimated total of the open lines
            val open = snap.lines.count { it.status == LineStatus.NEEDED }
            if (open > 0) {
                GoShoppingBar(
                    open = open,
                    estimate = if (display.showTotal) app.necessityq.domain.ListCost.of(snap.lines, prices, itemPrices) else null,
                    storeName = storeName,
                    onGo = { vm.setMode(ListMode.SHOP) },
                )
            }
        }
    }

    if (showFilter) {
        FilterSheet(
            filter = filter,
            lines = snapshot?.lines.orEmpty(),
            resultCount = sections.sumOf { sec -> sec.lines.count { it.status == LineStatus.NEEDED } },
            onChange = vm::setFilter,
            onDismiss = { showFilter = false },
        )
    }
    if (showGenerate) GenerateSheet(vm, onDismiss = { showGenerate = false })
    if (showEmpty) {
        val count = ListReset.linesFor(ResetScope.ALL, snapshot?.lines.orEmpty()).size
        AlertDialog(
            onDismissRequest = { showEmpty = false },
            title = { Text("לרוקן את כל הרשימה?") },
            text = { Text(if (count == 0) "הרשימה כבר ריקה." else "$count פריטים (גם אלה שסומנו כנקנו) יעברו לסל המחזור, ואפשר לבטל מיד אחרי הפעולה.") },
            confirmButton = {
                TextButton(enabled = count > 0, onClick = { vm.resetList(ResetScope.ALL); showEmpty = false }) { Text("ריקון") }
            },
            dismissButton = { TextButton(onClick = { showEmpty = false }) { Text("ביטול") } },
        )
    }
    if (showReset) ResetDialog(snapshot?.lines.orEmpty(), onReset = { vm.resetList(it); showReset = false }, onDismiss = { showReset = false })

    openItem?.let { line ->
        ItemSheet(
            vm, itemId = line.itemId, itemName = line.displayName, onDismiss = { openItem = null },
            onOpenItem = { id, n -> openItem = null; openOther = id to n },
        )
    }
    // another product's conversation, reached from its "typing…" banner
    openOther?.let { (id, n) ->
        ItemSheet(vm, itemId = id, itemName = n, onDismiss = { openOther = null }, onOpenItem = { id2, n2 -> openOther = id2 to n2 })
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
}

// ---------- store: once at the start of a trip, optional, editable from the title ----------

@Composable
private fun StorePromptCard(vm: AppViewModel) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = NqShapes.Tile,
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
    val hereBranch by vm.hereBranch.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(vm.hasLocationPermission()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result.values.any { it }
        if (hasPermission) vm.refreshLocation()
    }
    LaunchedEffect(hasPermission) { if (hasPermission) vm.refreshLocation() }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        nearby?.let { near ->
            AssistChip(
                onClick = { onChosen(near.id) },
                label = { Text("ליד: ${near.name}", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)) },
            )
        }
        // not a household store yet, but the coordinates match a chain branch: offer it by name
        val branchHere = hereBranch?.branch?.takeIf { nearby == null }
        branchHere?.let { b ->
            AssistChip(
                onClick = { vm.addStoreFromBranch(b)?.let(onChosen) },
                label = { Text("כאן: ${app.necessityq.domain.BranchLocator.storeName(b)}", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)) },
            )
        }
        if (hasPermission && nearby == null && branchHere == null) {
            AssistChip(
                onClick = { vm.locate(quiet = false) },
                enabled = !locating,
                label = { Text(if (locating) "מאתר מיקום…" else "איתור לפי מיקום") },
                leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)) },
            )
        }
        // the branches followed on the prices screen, nearest first: one tap saves it as a household store
        val followedIds by vm.selectedPriceBranches.collectAsStateWithLifecycle()
        val allBranches by vm.priceBranches.collectAsStateWithLifecycle()
        val position by vm.userPosition.collectAsStateWithLifecycle()
        val storeKeys = stores.map { app.necessityq.domain.HebrewText.matchKey(it.name) }.toSet()
        val followed = followedIds.mapNotNull { id -> allBranches.firstOrNull { it.id == id } }
            .filter { it.id != branchHere?.id && app.necessityq.domain.HebrewText.matchKey(app.necessityq.domain.BranchLocator.storeName(it)) !in storeKeys }
            .map { b ->
                val lat = b.latitude
                val lon = b.longitude
                b to position?.let { (la, lo) -> if (lat != null && lon != null) app.necessityq.domain.Stores.distanceMeters(la, lo, lat, lon) else null }
            }
            .sortedBy { it.second ?: Double.MAX_VALUE }
        followed.forEachIndexed { i, (b, meters) ->
            val close = i == 0 && meters != null && meters <= 500
            AssistChip(
                onClick = { vm.addStoreFromBranch(b)?.let(onChosen) },
                label = {
                    Text(
                        app.necessityq.domain.BranchLocator.storeName(b) + (meters?.let { " · " + distanceLabel(it) } ?: ""),
                        fontWeight = if (close) FontWeight.Bold else null,
                    )
                },
                leadingIcon = { Icon(Icons.Default.Storefront, null, Modifier.size(18.dp)) },
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

/** "350 מ'" / "1.4 ק"מ" */
private fun distanceLabel(meters: Double): String =
    if (meters < 1000) "${(meters / 10).toInt() * 10} מ'"
    else String.format(java.util.Locale.US, "%.1f ק\"מ", meters / 1000)

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

// ---------- info lines ----------

private fun daysAgo(stats: ItemStats?, today: LocalDate): String? {
    val s = stats ?: return null
    val last = s.estimatedLastPurchase
    val days = ChronoUnit.DAYS.between(last, today)
    val text = when {
        days <= 0 -> "נקנה היום"
        days == 1L -> "נקנה אתמול"
        else -> "נקנה לפני $days ימים"
    }
    return if (s.lastPurchaseEstimated) "כנראה $text" else text
}

private fun cadence(stats: ItemStats?): String? =
    stats?.cadenceDays?.let { "בערך כל ${it.roundToInt().coerceAtLeast(1)} ימים" }

private fun tileInfo(line: ListLine, stats: ItemStats?, names: Map<String, String>, display: DisplayOptions, today: LocalDate): String =
    listOfNotNull(
        line.note,
        if (display.showStats && display.showLastBought) daysAgo(stats, today) else null,
        if (display.showStats && display.showFrequency) cadence(stats) else null,
        if (display.showAddedBy) names[line.addedBy]?.let { (if (line.isAuto) "נוצר אוטומטית עבור " else "הוסיף/ה: ") + it } else null,
    ).joinToString(" · ")

private fun recommendationInfo(s: Suggestion, stats: ItemStats?, favorites: Set<String>, display: DisplayOptions, today: LocalDate): String =
    listOfNotNull(
        if (display.showReason) s.reason else null,
        if (display.showLastBought && !display.showReason) daysAgo(stats, today) else null,
        if (display.showFrequency && !display.showReason) cadence(stats) else null,
        if (display.showFavorite && s.itemId in favorites) "★ במועדפים" else null,
    ).joinToString(" · ")

// ---------- building blocks ----------

/** Category header; tap to collapse or expand the group. */
@Composable
private fun GroupHeader(row: ListRow.Header, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).clickable(onClick = onToggle)
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        row.category?.let {
            Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(it)))
            Spacer(Modifier.width(8.dp))
        }
        Text(row.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text("${row.count}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Icon(
            if (row.collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (row.collapsed) "הרחבת הקבוצה" else "כיווץ הקבוצה",
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Conversation state of an item shown on its tile. */
data class ChatState(val unread: Int, val lastIsMine: Boolean, val status: DeliveryStatus?)

private const val HOLD_MS = 650

/** A pull faster than this (dp per second) in the direction of the pull counts as a push: no waiting. */
private const val PUSH_SPEED_DP = 1500f

private class SwipeAction(val label: String, val icon: ImageVector, val color: Color, val run: () -> Unit)

/**
 * Physical-direction swipe: left = bought, right = missing (shopping) / delete (preparing).
 * The icon and the action's name sit at the edge of the phone the tile was pulled away from, with the
 * instruction under the name: pull on → hold → release. The action runs when the tile is held past the
 * threshold until the ring fills; a forceful push runs it at once. Pulling back cancels.
 */
@Composable
private fun SwipeableTile(left: SwipeAction, right: SwipeAction, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
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
                Modifier.matchParentSize().clip(NqShapes.Tile).background(action.color).padding(horizontal = 18.dp),
                contentAlignment = if (toRight) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight,
            ) {
                // ring pinned to the phone edge, the action's name beside it and the instruction below the name
                CompositionLocalProvider(LocalLayoutDirection provides if (toRight) LayoutDirection.Ltr else LayoutDirection.Rtl) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.alpha(if (ready) blink else 1f)) {
                            CircularProgressIndicator(
                                progress = { if (past) ring.value else 0f },
                                modifier = Modifier.size(44.dp),
                                color = NqPalette.OnAction,
                                trackColor = NqPalette.OnAction.copy(alpha = 0.3f),
                                strokeWidth = 3.dp,
                            )
                            Icon(action.icon, contentDescription = null, tint = NqPalette.OnAction, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(action.label, color = NqPalette.OnAction, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    !past -> "משכו עוד"
                                    !ready -> "החזיקו…"
                                    else -> "שחררו"
                                },
                                color = NqPalette.OnAction.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
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
                    onDragStopped = { velocity ->
                        val dir = if (offset.value > 0) 1f else -1f
                        val pushed = offset.value != 0f && velocity * dir >= with(density) { PUSH_SPEED_DP.dp.toPx() } &&
                            abs(offset.value) >= width * 0.12f
                        val held = armedAt?.let { System.currentTimeMillis() - it >= HOLD_MS } == true
                        val stillPast = abs(offset.value) >= width * 0.3f
                        if (pushed || (held && stillPast)) (if (offset.value > 0) right else left).run()
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
    display: DisplayOptions,
    price: Double?,
    priceInfo: app.necessityq.domain.ItemPriceInfo? = null,
    controlsOpen: Boolean,
    lifted: Boolean,
    dragHandle: (@Composable () -> Unit)?,
    onBought: () -> Unit,
    onRightSwipe: () -> Unit,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onSetQuantity: ((Quantity?, Boolean) -> Unit)? = null,
    quantityCheck: (Quantity) -> app.necessityq.domain.QuantityCheck? = { null },
    onTap: () -> Unit,
    onInfo: () -> Unit,
) {
    val right = if (shopping) {
        SwipeAction(if (line.missing) "ביטול חסר" else "חסר", Icons.Default.ReportProblem, NqPalette.SwipeMissing, onRightSwipe)
    } else {
        SwipeAction("מחיקה", Icons.Default.Delete, NqPalette.SwipeDelete, onRightSwipe)
    }
    val left = SwipeAction("נקנה", Icons.Default.Check, NqPalette.SwipeBought, onBought)
    val controlsMode = display.tapMode == TapMode.CONTROLS
    // stepper: always (info mode, if enabled) or only after a tap (controls mode)
    val showStepper = if (controlsMode) controlsOpen else display.showStepper
    val showQuantityText = display.showQuantity && !showStepper && line.quantity != null
    // produce counted in pieces: estimated weight, and the price for that weight
    val weight = if (display.showWeightEstimate) app.necessityq.domain.PieceWeights.estimate(line.name, line.quantity) else null
    val weightedCost = priceInfo?.let { app.necessityq.domain.LineCosts.weighted(line, it) }

    Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) {
        SwipeableTile(left = left, right = right) {
            Card(
                shape = NqShapes.Tile,
                border = when {
                    line.missing -> BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                    controlsOpen && controlsMode -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    else -> BorderStroke(1.dp, NqTheme.colors.hairline)
                },
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        lifted -> MaterialTheme.colorScheme.primaryContainer
                        line.missing -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (lifted) 10.dp else 0.dp),
            ) {
                Row {
                    // the category is shown by the group header's dot; auto-generated lines carry an "אוטומטי" tag
                    Column(Modifier.weight(1f).clickable(onClick = onTap).padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            dragHandle?.invoke()
                            val checkSize = if (shopping) 48.dp else 40.dp
                            Box(Modifier.size(checkSize).clip(CircleShape).clickable(onClick = onBought), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(checkSize - 18.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        line.displayName,
                                        style = ProductNameStyle,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (showQuantityText) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            line.quantity!!.format() + (weight?.let { "  " + app.necessityq.domain.PieceWeights.label(it) } ?: ""),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.clip(NqShapes.Pill)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(horizontal = 8.dp, vertical = 1.dp),
                                        )
                                    }
                                }
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
                            if (display.showPrices) {
                                PriceSlot(price = price?.let { it * PriceEstimate.units(line.quantity) }, average = null, info = priceInfo, weighted = weightedCost, quantity = line.quantity)
                            }
                        }
                        if (subline.isNotBlank() || showStepper || (controlsMode && controlsOpen)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    subline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (showStepper && weight != null) {
                                    Text(
                                        app.necessityq.domain.PieceWeights.label(weight),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                }
                                if (showStepper) QuantityStepper(line.quantity, onMinus, onPlus, onSetQuantity, line.displayName, quantityCheck)
                                if (controlsMode && controlsOpen) {
                                    IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, contentDescription = "דף מידע") }
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

/** The estimated cost as one short text: "184 ₪", "~184 ₪" when not every line has a price, or "~150–184 ₪". */
private fun costText(estimate: app.necessityq.domain.ListCostEstimate): String {
    val prefix = if (estimate.complete) "" else "~"
    return when {
        estimate.pricedLines == 0 -> "\u200E—\u00A0₪\u200E"
        estimate.isRange -> app.necessityq.domain.PriceData.formatRange(estimate.low, estimate.high, decimals = 0, prefix = prefix)
        else -> app.necessityq.domain.PriceData.formatRange(estimate.low, estimate.low, decimals = 0, prefix = prefix)
    }
}

// ---------- filter ----------

/** The active filter as small removable chips under the toolbar, so the list keeps its room. */
@Composable
private fun ActiveFilterRow(filter: ListFilter, onChange: (ListFilter) -> Unit) {
    val chips = buildList<Pair<String, ListFilter>> {
        if (filter.text.isNotBlank()) add("\"${filter.text.trim()}\"" to filter.copy(text = ""))
        if (filter.source != SourceFilter.ALL) add(filter.source.label to filter.copy(source = SourceFilter.ALL))
        if (filter.missingOnly) add("חסרים" to filter.copy(missingOnly = false))
        if (filter.withChatOnly) add("עם שיחה" to filter.copy(withChatOnly = false))
        filter.categories.sortedBy { it.order }.forEach { c -> add(c.label to filter.copy(categories = filter.categories - c)) }
    }
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(chips, key = { it.first }) { (label, without) ->
            InputChip(
                selected = true,
                onClick = { onChange(without) },
                label = { Text(label, maxLines = 1) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "הסרת $label", Modifier.size(16.dp)) },
                shape = NqShapes.Pill,
            )
        }
        if (chips.size > 1) {
            item(key = "clear_all") { TextButton(onClick = { onChange(ListFilter()) }) { Text("ניקוי הכל") } }
        }
    }
}

/** Filter controls in a sheet, grouped by kind; changes apply live and the button shows how many remain. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filter: ListFilter,
    lines: List<ListLine>,
    resultCount: Int,
    onChange: (ListFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val present = lines.filter { it.status == LineStatus.NEEDED }.map { it.category }.toSet().sortedBy { it.order }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("סינון הרשימה", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (filter.isActive) TextButton(onClick = { onChange(ListFilter()) }) { Text("ניקוי") }
            }
            FilterSection("מקור") {
                SourceFilter.entries.forEach { option ->
                    FilterChip(selected = filter.source == option, onClick = { onChange(filter.copy(source = option)) }, label = { Text(option.label) })
                }
            }
            FilterSection("מצב") {
                FilterChip(selected = filter.missingOnly, onClick = { onChange(filter.copy(missingOnly = !filter.missingOnly)) }, label = { Text("חסרים") })
                FilterChip(selected = filter.withChatOnly, onClick = { onChange(filter.copy(withChatOnly = !filter.withChatOnly)) }, label = { Text("עם שיחה") })
            }
            if (present.size > 1) {
                FilterSection("קטגוריות") {
                    present.forEach { c ->
                        val on = c in filter.categories
                        FilterChip(
                            selected = on,
                            onClick = { onChange(filter.copy(categories = if (on) filter.categories - c else filter.categories + c)) },
                            label = { Text(c.label) },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(c))) },
                        )
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(if (filter.isActive) "הצגת $resultCount פריטים" else "סגירה")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

// ---------- list from past purchases, reset ----------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GenerateSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(GenerateMode.DUE) }
    val proposal = remember(mode) { vm.proposeList(mode) }
    var chosen by remember(proposal) { mutableStateOf(proposal.filter { it.preselected }.map { it.identity.id }.toSet()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("רשימה מקניות עבר", style = MaterialTheme.typography.titleLarge)
            Text(
                "הפריטים יסומנו \"אוטומטי\" כדי שיהיה ברור מה נוצר מההיסטוריה ומה נוסף ידנית.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GenerateMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
            }
            Text(mode.hint, style = MaterialTheme.typography.bodySmall)
            if (proposal.isEmpty()) {
                Text("אין מה להציע — כל הפריטים כבר ברשימה, או שאין עדיין מספיק היסטוריה.", Modifier.padding(vertical = 16.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${chosen.size} מתוך ${proposal.size} נבחרו", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { chosen = proposal.map { it.identity.id }.toSet() }) { Text("בחר הכל") }
                    TextButton(onClick = { chosen = emptySet() }) { Text("נקה") }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(proposal, key = { it.identity.id }) { item ->
                    val on = item.identity.id in chosen
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { chosen = if (on) chosen - item.identity.id else chosen + item.identity.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(item.identity.category)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.identity.displayName + (item.quantity?.let { " · " + it.format() } ?: ""), fontWeight = FontWeight.Bold)
                            Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Button(
                onClick = { vm.addGenerated(proposal.filter { it.identity.id in chosen }); onDismiss() },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) { Text("הוספת ${chosen.size} פריטים לרשימה") }
        }
    }
}

@Composable
private fun ResetDialog(lines: List<ListLine>, onReset: (ResetScope) -> Unit, onDismiss: () -> Unit) {
    var scope by remember { mutableStateOf(ResetScope.ALL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.RestartAlt, null) },
        title = { Text("איפוס הרשימה") },
        text = {
            Column {
                ResetScope.entries.forEach { option ->
                    val count = ListReset.linesFor(option, lines).size
                    Row(
                        Modifier.fillMaxWidth().clickable { scope = option }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = scope == option, onClick = { scope = option })
                        Text("${option.label} ($count)")
                    }
                }
                Text(
                    "הפריטים עוברים לסל המחזור ואפשר להחזיר אותם. היסטוריית הקניות לא נפגעת." +
                        if (scope == ResetScope.ALL) " גם הפריטים שסומנו כנקנו מנוקים." else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = ListReset.linesFor(scope, lines).isNotEmpty(), onClick = { onReset(scope) }) { Text("איפוס") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** Ticks for my last message: o written · ✓ sent · ✓✓ received (grey) · ✓✓ read (blue). */
@Composable
fun DeliveryTicks(status: DeliveryStatus) {
    val readBlue = NqTheme.colors.readBlue
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
        Modifier.clip(NqShapes.Pill)
            .background(if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 7.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(13.dp), tint = if (active) NqPalette.OnAction else MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            active -> {
                Spacer(Modifier.width(3.dp))
                Text("${state.unread}", color = NqPalette.OnAction, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
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
fun PriceSlot(
    price: Double?,
    average: Double?,
    info: app.necessityq.domain.ItemPriceInfo? = null,
    /** produce sold by weight: the cost of the quantity on the list (estimated weight when counted) */
    weighted: app.necessityq.domain.LineCost? = null,
    /** the amount on the list, shown instead of the number of matching products */
    quantity: app.necessityq.domain.Quantity? = null,
) {
    // the household's own price wins; otherwise the branch data: chosen product, or the typical range
    val (main, sub) = when {
        price != null -> app.necessityq.domain.PriceData.formatPrice(price) to "ממוצע " + (average?.let { app.necessityq.domain.PriceData.formatPrice(it) } ?: "—")
        weighted != null -> app.necessityq.domain.PriceData.formatRange(weighted.low, weighted.high) to
            listOfNotNull(quantity?.format(), weighted.estimatedWeight?.let { app.necessityq.domain.PieceWeights.label(it) })
                .joinToString(" ").ifEmpty { "לפי משקל" }
        info?.preferred != null -> app.necessityq.domain.PriceData.formatPrice(info.preferred!!.price) to "המוצר שבחרת"
        // produce priced per kilo with no amount on the list: the price is per kilo
        info != null && info.candidates.isNotEmpty() && info.candidates.all { it.soldByWeight } ->
            app.necessityq.domain.PriceData.formatRange(info.low, info.high) to "לק\"ג"
        info != null -> {
            // packaged goods: the price of the amount on the list ("2 יח'"), not a count of matching products
            val n = app.necessityq.domain.ListCost.packages(quantity, false) ?: 1.0
            app.necessityq.domain.PriceData.formatRange(info.low * n, info.high * n) to
                (quantity?.format() ?: if (info.candidates.size == 1) "מוצר אחד" else "${info.candidates.size} מוצרים")
        }
        else -> "\u200E—\u00A0₪\u200E" to "ממוצע " + (average?.let { app.necessityq.domain.PriceData.formatPrice(it) } ?: "—")
    }
    Column(
        Modifier.widthIn(min = 68.dp).clip(NqShapes.Panel)
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(main, fontWeight = FontWeight.SemiBold)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Promotion line right under a product tile; renders nothing until there is a promotion. */
@Composable
fun PromoStrip(promo: String?) {
    if (promo == null) return
    Text(
        "🏷 $promo",
        Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            .background(NqTheme.colors.promoBackground, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        fontWeight = FontWeight.Bold,
        color = NqTheme.colors.promoInk,
    )
}

/**
 * Tapping the amount opens a field to type it, so a child's thousand taps on "+" is one edit, not a thousand on "−".
 * An amount above what the item is usually bought in is allowed, but only after saying so: the warning shows
 * under the field and the button becomes "כן, זו הכמות" (the line is then not flagged as "כמות חריגה").
 */
@Composable
private fun QuantityEditDialog(
    quantity: Quantity?,
    itemName: String,
    check: (Quantity) -> app.necessityq.domain.QuantityCheck?,
    onSet: (Quantity?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val unit = quantity?.unit ?: app.necessityq.domain.QuantityUnit.PIECE
    var text by remember { mutableStateOf(quantity?.let { Quantity.formatNumber(it.amount) } ?: "") }
    val amount = text.trim().replace(',', '.').toDoubleOrNull()
    val blank = text.isBlank()
    val valid = blank || (amount != null && amount > 0.0)
    val verdict = if (!blank && valid) check(Quantity(amount!!, unit))?.takeIf { it.isUnusual } else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("כמות (${unit.label})") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = !valid || verdict?.level == app.necessityq.domain.QuantityLevel.ABSURD,
                supportingText = {
                    when {
                        !valid -> Text("צריך מספר גדול מאפס")
                        verdict != null -> Text(verdict.message(itemName), color = NqTheme.colors.tag(TagKind.LARGE_AMOUNT).second)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSet(if (blank) null else Quantity(amount!!, unit), verdict != null); onDismiss() },
            ) { Text(if (verdict != null) "כן, זו הכמות" else "אישור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
fun QuantityStepper(
    quantity: Quantity?,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    /** (amount, confirmed as more than usual) */
    onSet: ((Quantity?, Boolean) -> Unit)? = null,
    itemName: String = "",
    check: (Quantity) -> app.necessityq.domain.QuantityCheck? = { null },
) {
    var editing by remember { mutableStateOf(false) }
    if (editing && onSet != null) QuantityEditDialog(quantity, itemName, check, onSet) { editing = false }
    Row(
        Modifier.clip(NqShapes.Pill).background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Hebrew layout: the first child is on the right — "+" sits next to the product text
        IconButton(onClick = onPlus, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Add, contentDescription = "יותר", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        // the whole height of the pill between the buttons opens the keyboard, not just the glyphs
        Box(
            Modifier.heightIn(min = 38.dp).widthIn(min = 44.dp)
                .let { if (onSet != null) it.clickable(onClickLabel = "הקלדת כמות") { editing = true } else it },
            contentAlignment = Alignment.Center,
        ) {
            Text(quantity?.format() ?: "—", fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
        IconButton(onClick = onMinus, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Remove, contentDescription = "פחות", Modifier.size(20.dp)) }
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
    val (bg, fg) = NqTheme.colors.tag(tag.kind)
    Text(
        tag.label,
        Modifier.clip(NqShapes.Pill).background(bg).padding(horizontal = 8.dp, vertical = 1.dp),
        color = fg,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
    )
}

@Composable
private fun CompletionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Chips in two rows that scroll sideways, so suggestions never push the list off the screen. */
@Composable
private fun <T> TwoRowStrip(
    entries: List<T>,
    height: androidx.compose.ui.unit.Dp = 100.dp,
    fullHeight: (T) -> Boolean = { false },
    chip: @Composable (T) -> Unit,
) {
    androidx.compose.foundation.lazy.staggeredgrid.LazyHorizontalStaggeredGrid(
        rows = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(height),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalItemSpacing = 6.dp,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            entries.size,
            span = { i ->
                if (fullHeight(entries[i])) androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine
                else androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.SingleLane
            },
        ) { i ->
            Box(contentAlignment = Alignment.Center) { chip(entries[i]) }
        }
    }
}

// ---------- redesign building blocks ----------

/** "הכנת רשימה | בקנייה" as one segmented switch: the two stages of using the list. */
@Composable
fun ModeSwitch(shopping: Boolean, onSelect: (ListMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(NqShapes.Field).background(NqTheme.colors.segmentTrack).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(ListMode.PLAN, ListMode.SHOP).forEach { m ->
            val selected = (m == ListMode.SHOP) == shopping
            Row(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(m) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (m == ListMode.SHOP) {
                    Icon(
                        Icons.Default.ShoppingCart, null, Modifier.size(16.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    m.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Green header while shopping: the store (tap to change), how much is already in the cart and what is left to pay. */
@Composable
private fun ShopHeader(
    storeName: String?,
    done: Int,
    open: Int,
    remaining: app.necessityq.domain.ListCostEstimate?,
    onBack: () -> Unit,
    onStore: () -> Unit,
    actions: @Composable () -> Unit,
) {
    val c = NqTheme.colors
    val total = done + open
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(c.shopHeader)
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה להכנת רשימה", tint = c.onShopHeader)
            }
            Column(
                Modifier.weight(1f).clip(NqShapes.Panel).clickable(onClickLabel = "החלפת חנות", onClick = onStore)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    storeName ?: "בקנייה",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.onShopHeader,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (storeName == null) "בחירת חנות ✎" else "החלפת חנות ✎",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onShopHeaderMuted,
                )
            }
            actions()
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("$done", color = c.onShopHeader, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            Text(
                " / $total בעגלה",
                color = c.onShopHeaderMuted,
                fontSize = 17.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            if (remaining != null && remaining.pricedLines > 0 && open > 0) {
                Text(
                    "נשארו " + costText(remaining),
                    color = c.onShopHeaderMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
        // progress of the trip; drawn with two boxes so it reads the same on every Material version
        Box(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).height(8.dp)
                .clip(NqShapes.Pill).background(Color.White.copy(alpha = 0.2f))
                .semantics { contentDescription = "$done מתוך $total בעגלה" },
        ) {
            val fraction = if (total == 0) 0f else done.toFloat() / total
            if (fraction > 0f) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(NqShapes.Pill).background(c.shopProgress))
            }
        }
    }
}

/** The quick-add box: white, rounded, a scan button when empty and a clear "הוספה" button once something is typed. */
@Composable
private fun QuickAddField(
    query: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("מה חסר? למשל: 2 קוטג' 5%", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            if (query.isBlank()) {
                IconButton(onClick = onScan) {
                    Box(
                        Modifier.size(40.dp).clip(NqShapes.Panel).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "הוספה בסריקת ברקוד", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
                    IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "מחיקת הטקסט") }
                    Button(
                        onClick = onSubmit,
                        shape = NqShapes.Panel,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.height(40.dp),
                    ) { Text("הוספה") }
                }
            }
        },
        singleLine = true,
        shape = NqShapes.Field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = NqTheme.colors.hairline,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
    )
}

/** Under the quick-add box: what will be added (name · amount · group), or that it is already on the list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddPreview(parsed: app.necessityq.domain.ParsedEntry?, matches: Int, modifier: Modifier = Modifier) {
    if (matches > 0) {
        Text(
            "כבר ברשימה ($matches). \"הוספה\" תוסיף רק כמות שכתבתם.",
            modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (parsed == null) return
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "יתווסף:",
            Modifier.padding(vertical = 3.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreviewPill(parsed.item.displayName, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, bold = true)
        parsed.quantity?.let { PreviewPill(it.format(), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface) }
        Text(
            "· " + parsed.item.category.label,
            Modifier.padding(vertical = 3.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewPill(text: String, background: Color, ink: Color, bold: Boolean = false) {
    Text(
        text,
        Modifier.clip(NqShapes.Pill).background(background).border(1.dp, NqTheme.colors.hairline, NqShapes.Pill)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = ink,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
    )
}

/** Title of a suggestion section: tap to fold it; "להוסיף הכל" when the section offers it. */
@Composable
private fun SuggestionHeader(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddAll: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clip(NqShapes.Panel).clickable(onClick = onToggle).padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(6.dp))
            Text("$count", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(2.dp))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "כיווץ" else "הרחבה",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded && onAddAll != null) {
            TextButton(onClick = onAddAll) { Text("להוסיף הכל", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/**
 * "Running out soon" card: name and usual amount, the reason in an amber note, a clear "לרשימה" button,
 * and ✕ as its own full-size target so dismissing never adds by mistake.
 */
@Composable
private fun RunningLowCard(s: Suggestion, info: String, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val name = s.identity.displayName
    Column(
        Modifier.width(196.dp).clip(NqShapes.Tile).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, NqTheme.colors.hairline, NqShapes.Tile)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row {
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                s.quantity?.let {
                    Text(it.format(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Close, contentDescription = "לא עכשיו: $name", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (info.isNotBlank()) {
                Text(
                    info,
                    Modifier.fillMaxWidth().clip(NqShapes.Inset).background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(NqShapes.Panel).background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(role = Role.Button, onClickLabel = "הוספת $name לרשימה", onClick = onAdd),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(6.dp))
                Text("לרשימה", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Bottom of the list while preparing: one button into the store, with the count and the estimated total. */
@Composable
private fun GoShoppingBar(open: Int, estimate: app.necessityq.domain.ListCostEstimate?, storeName: String?, onGo: () -> Unit) {
    val details = listOfNotNull(
        if (open == 1) "פריט אחד" else "$open פריטים",
        estimate?.takeIf { it.pricedLines > 0 }?.let { costText(it) },
        storeName?.let { "ב$it" },
    ).joinToString(" · ")
    Box(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(NqShapes.Field).background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button, onClick = onGo).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("יוצאים לקנות", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    details,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Bottom of the list while shopping: scan a product, or finish and go back to preparing. */
@Composable
private fun ShopBottomBar(onScan: () -> Unit, onFinish: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = onScan,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = NqShapes.Field,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("סריקת מוצר")
        }
        Button(onClick = onFinish, modifier = Modifier.weight(1f).height(56.dp), shape = NqShapes.Field) {
            Text("סיום קנייה")
        }
    }
}

/**
 * "מתכונים ברשימה": each recipe whose ingredients are on the list, with its servings. Changing them updates the
 * amounts on the list (after a short pause between taps); ✕ takes the recipe's ingredients off the list.
 */
@Composable
private fun RecipesOnListCard(recipes: List<Pair<Dish, Int>>, onServings: (Dish, Int) -> Unit, onRemove: (Dish) -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(NqShapes.Tile)
            .background(MaterialTheme.colorScheme.surface).border(1.dp, NqTheme.colors.hairline, NqShapes.Tile)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("מתכונים ברשימה", style = MaterialTheme.typography.titleSmall)
        }
        recipes.forEach { (dish, count) ->
            androidx.compose.runtime.key(dish.id) { RecipeOnListRow(dish, count, onServings, onRemove) }
        }
    }
}

@Composable
private fun RecipeOnListRow(dish: Dish, count: Int, onServings: (Dish, Int) -> Unit, onRemove: (Dish) -> Unit) {
    val listed = dish.listServings ?: dish.plannedServings
    var pending by remember(dish.id, listed) { mutableIntStateOf(listed) }
    LaunchedEffect(pending) {
        if (pending != listed) {
            kotlinx.coroutines.delay(700)
            onServings(dish, pending)
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(dish.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (count == 1) "פריט אחד ברשימה" else "$count פריטים ברשימה",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier.clip(NqShapes.Panel).background(MaterialTheme.colorScheme.surfaceVariant),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { pending = (pending + 1).coerceAtMost(40) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = "עוד מנה: ${dish.name}", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text("$pending מנות", fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 56.dp), textAlign = TextAlign.Center)
            IconButton(onClick = { pending = (pending - 1).coerceAtLeast(1) }, modifier = Modifier.size(40.dp), enabled = pending > 1) {
                Icon(Icons.Default.Remove, contentDescription = "מנה פחות: ${dish.name}", Modifier.size(18.dp))
            }
        }
        IconButton(onClick = { onRemove(dish) }) {
            Icon(Icons.Default.Close, contentDescription = "הסרת ${dish.name} מהרשימה", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
