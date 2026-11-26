package app.necessityq.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import app.necessityq.ui.icons.Icons
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
import androidx.compose.runtime.mutableIntStateOf
import app.necessityq.domain.GenerateMode
import app.necessityq.domain.LineStatus
import app.necessityq.domain.ListFilter
import app.necessityq.domain.QuickEntryParser
import app.necessityq.domain.ListReset
import app.necessityq.domain.ListSection
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
import app.necessityq.domain.TileElement
import app.necessityq.domain.TileLayout
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
    /** Opens the "מחירים" tab (a suggestion to pick a branch). */
    onOpenPrices: () -> Unit = {},
    /** Opens the help page ("איך משתמשים"). */
    onOpenHelp: () -> Unit = {},
) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    // the list is narrowed only once typing pauses, not on every letter typed or deleted
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
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
    val storePromptAsked by vm.storePrompt.collectAsStateWithLifecycle()
    val advice by vm.advice.collectAsStateWithLifecycle()
    val followedForAdvice by vm.selectedPriceBranches.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val currentStoreId by vm.currentStoreId.collectAsStateWithLifecycle()
    val descending by vm.descending.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val prices by vm.prices.collectAsStateWithLifecycle()
    val itemPrices by vm.itemPriceInfo.collectAsStateWithLifecycle()
    val comparisons by vm.itemComparisons.collectAsStateWithLifecycle()
    val unreadTotal by vm.unreadTotal.collectAsStateWithLifecycle()
    val tripStops by vm.tripStops.collectAsStateWithLifecycle()
    val tripAssignment by vm.tripAssignment.collectAsStateWithLifecycle()
    val activeStop by vm.activeStop.collectAsStateWithLifecycle()
    val promoOffers by vm.promoOffers.collectAsStateWithLifecycle()
    val followedBranches by vm.selectedPriceBranches.collectAsStateWithLifecycle()
    val allBranches by vm.priceBranches.collectAsStateWithLifecycle()
    // kept up to date so "יוצאים לקנות" knows at once whether the regular supermarket is the cheapest
    val tripPlan by vm.tripPlan.collectAsStateWithLifecycle()
    // "לבדוק שוב": scanned with "תזכיר לי אחר כך" — all while preparing, the branch's own while shopping there
    val reminders by vm.scanReminders.collectAsStateWithLifecycle()
    val priceBranchId by vm.primaryBranchId.collectAsStateWithLifecycle()
    val pricesLoading by vm.pricesLoading.collectAsStateWithLifecycle()
    var showTrip by remember { mutableStateOf(false) }
    var openItem by remember { mutableStateOf<ListLine?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showGenerate by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showBin by remember { mutableStateOf(false) }
    val scan = rememberBarcodeScan(vm) { vm.onScanned(it) }
    var showStoreDialog by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    val shopping = mode == ListMode.SHOP
    // each stage of use has its own tile (set in the "תצוגה" popup)
    // features turned off (הגדרות ← יכולות) leave the list: their cards, buttons and parts of the tile
    val features by vm.features.collectAsStateWithLifecycle()
    // each stage has its own features (הגדרות ← פיצ'רים ← הכנת רשימה | בקנייה)
    val tile = features.tile(display.tile(mode), mode)
    val showTotal = display.showTotal && features.isOn(app.necessityq.domain.Feature.PRICES, mode)
    val chatOn = app.necessityq.domain.Feature.CHAT in features
    val tripPlanOn = features.isOn(app.necessityq.domain.Feature.TRIP_PLAN, ListMode.PLAN)
    val compareOn = features.isOn(app.necessityq.domain.Feature.BRANCH_COMPARE, mode)
    // a gift in the corner of a tile whose product has a promotion at one of the followed branches
    val promoItems = remember(promoOffers, features, mode) {
        if (features.isOn(app.necessityq.domain.Feature.PROMOS, mode)) promoOffers.map { it.itemId }.toSet() else emptySet()
    }
    // what a swipe to each side does at this stage (הגדרות ← הרשימה ← החלקה על פריט)
    val swipes = display.swipe(mode)
    // "איפה קונים היום?" is about the shop's prices: not asked while prices are off in the shop ("רשימה פשוטה")
    val storePrompt = storePromptAsked && features.isOn(app.necessityq.domain.Feature.PRICES, ListMode.SHOP)
    // the suggestion card is worked out again as the data it is based on comes in
    LaunchedEffect(snapshot?.stats?.size, features, followedForAdvice.size) { vm.refreshAdvice() }
    val showAdvice = !shopping && advice != null
    // the promotion's details in a strip under the tile: where the list is priced (in the shop: that shop only); while
    // preparing, also the biggest saving at another followed branch
    val itemPromos = remember(promoOffers, priceBranchId, shopping, features) {
        if (features.isOn(app.necessityq.domain.Feature.PROMOS, mode)) {
            app.necessityq.domain.PromoOffers.perItem(promoOffers, priceBranchId, onlyPreferred = shopping)
        } else emptyMap()
    }
    var showTileEditor by remember { mutableStateOf(false) }

    // Local copy of the rows so a drag can move items immediately; re-synced from the live data when idle.
    val rows = remember { mutableStateListOf<ListRow>() }
    var dragging by remember { mutableStateOf(false) }
    val chatItemIds = remember(messages) { messages.map { it.itemId }.toSet() }
    // the quick-add box also searches the list: what is typed narrows the open lines (on top of the filter)
    val searchText = remember(searchQuery) { if (searchQuery.isBlank()) "" else QuickEntryParser.parse(searchQuery)?.item?.displayName ?: searchQuery.trim() }
    val listFilter = remember(filter, searchText) { if (searchText.isBlank() || filter.text.isNotBlank()) filter else filter.copy(text = searchText) }
    // a split trip: each stop shows its own list (items added since, not in the split, show at every stop)
    val splitting = shopping && tripStops.size > 1 && tripAssignment.isNotEmpty()
    val shownLines = remember(snapshot?.lines, splitting, tripAssignment, activeStop) {
        val all = snapshot?.lines.orEmpty()
        if (!splitting || activeStop == null) all else all.filter { tripAssignment[it.itemId]?.let { b -> b == activeStop } ?: true }
    }
    val stopCounts = remember(snapshot?.lines, tripAssignment) {
        snapshot?.lines.orEmpty().filter { it.status == LineStatus.NEEDED }.mapNotNull { tripAssignment[it.itemId] }.groupingBy { it }.eachCount()
    }
    val sections = remember(shownLines, sort, pickRanks, descending, display.chilledLast, listFilter, chatItemIds) {
        buildSections(shownLines, sort, pickRanks, descending, display.chilledLast, listFilter, chatItemIds)
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
    val showLow = showSuggestions && features.isOn(app.necessityq.domain.Feature.RUNNING_LOW, mode)
    val showFrequent = showSuggestions && features.isOn(app.necessityq.domain.Feature.FREQUENT, mode)
    // "כנראה נגמר בקרוב" opens by itself only while the list is empty; with items on the list it is one folded
    // line ("5 מוצרים כנראה נגמרים · הוספה ›") so the list starts near the top. Once tapped, the choice holds.
    var lowExpandedChoice by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Boolean?>(null) }
    val hasOpenLines = snapshot?.lines?.any { it.status == LineStatus.NEEDED } == true
    val lowExpanded = lowExpandedChoice ?: !hasOpenLines
    var frequentExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    // recipes whose ingredients are on the list: their servings can be changed (or the recipe removed) from here
    val mealData by vm.meals.collectAsStateWithLifecycle()
    val showRecipes = !shopping && searchText.isBlank() && features.isOn(app.necessityq.domain.Feature.RECIPES_ON_LIST, mode)
    val recipesOnList = remember(mealData.dishes, snapshot?.lines) {
        val lines = snapshot?.lines.orEmpty()
        mealData.dishes.mapNotNull { d -> RecipeListSync.onList(lines, d).size.takeIf { it > 0 }?.let { d to it } }
    }

    // "משכו שמאלה…" is shown the first three times the list opens with items, then it is learned
    val showSwipeHint = remember { vm.swipeHintDue() }
    var swipeHintCounted by remember { mutableStateOf(false) }
    val hasRows = rows.isNotEmpty()
    LaunchedEffect(hasRows) {
        if (showSwipeHint && hasRows && !swipeHintCounted) {
            swipeHintCounted = true
            vm.countSwipeHint()
        }
    }

    val shownReminders = remember(reminders, shopping, priceBranchId) {
        app.necessityq.domain.ScanReminder.shown(reminders, shopping, priceBranchId).reversed()
    }
    val showReminders = searchText.isBlank() && shownReminders.isNotEmpty()

    val listState = rememberLazyListState()
    // LazyColumn items before the product rows (store prompt, reminders, recipes, suggestions, hint) — to find a row's index
    val leadingItems = snapshot?.let { snap ->
        (if (shopping && storePrompt) 1 else 0) +
            (if (showAdvice) 1 else 0) +
            (if (showReminders) 1 else 0) +
            (if (showRecipes && recipesOnList.isNotEmpty()) 1 else 0) +
            (if (showLow && snap.runningLow.isNotEmpty()) 1 else 0) +
            (if (showFrequent && snap.frequent.isNotEmpty()) 1 else 0) + 1
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
    // moving an item by hand only makes sense in the grouped order, with nothing filtered out
    val dragAvailable = sort == ListSort.GROUPS && !filter.isActive

    val messagesButton: @Composable (Color) -> Unit = { tint ->
        // new messages, visible from the list itself
        IconButton(onClick = onOpenMessages) {
            BadgedBox(badge = { if (unreadTotal > 0) Badge { Text("$unreadTotal") } }) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = if (unreadTotal > 0) "צ'אט פוד: $unreadTotal הודעות חדשות" else "צ'אט פוד",
                    tint = tint,
                )
            }
        }
    }
    val menuButton: @Composable (Color) -> Unit = { tint ->
        Box {
            IconButton(onClick = { showMore = true }) { Icon(Icons.Default.MoreVert, contentDescription = "פעולות על הרשימה", tint = tint) }
            DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                // in groups: filling the list · what it shows · clearing it · settings and help
                DropdownMenuItem(
                    text = { Text("יצירת רשימה מקניות עבר") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                    onClick = { showGenerate = true; showMore = false },
                )
                if (!shopping && tripPlanOn && followedBranches.size >= 2) {
                    DropdownMenuItem(
                        text = { Text("איפה הכי משתלם לקנות?") },
                        leadingIcon = { Icon(Icons.Default.Storefront, null) },
                        onClick = { showMore = false; showTrip = true },
                    )
                }
                HorizontalDivider()
                // in the shop: what was already bought on this trip, at the bottom (the same setting as in הגדרות ← הרשימה)
                if (shopping) {
                    DropdownMenuItem(
                        text = { Text(if (display.showBought) "הסתרת מה שכבר נקנה" else "הצגת מה שכבר נקנה (בתחתית)") },
                        leadingIcon = { Icon(if (display.showBought) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) },
                        onClick = { showMore = false; vm.setDisplay(display.copy(showBought = !display.showBought)) },
                    )
                }
                // what a tile shows, at this stage of use (the same sheet as in the settings)
                DropdownMenuItem(
                    text = { Text("מה מוצג על כל פריט") },
                    leadingIcon = { Icon(Icons.Default.Tune, null) },
                    onClick = { showMore = false; showTileEditor = true },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("סל מחזור" + (snapshot?.removed?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: "")) },
                    leadingIcon = { Icon(Icons.Default.RestoreFromTrash, null) },
                    onClick = { showBin = true; showMore = false },
                )
                // one way to clear the list: the whole list, or only what was added automatically / by hand
                DropdownMenuItem(
                    text = { Text("ניקוי הרשימה…") },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                    onClick = { showReset = true; showMore = false },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("הגדרות") },
                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                    onClick = { showMore = false; onOpenDisplaySettings() },
                )
                DropdownMenuItem(
                    text = { Text("עזרה: איך משתמשים") },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = { showMore = false; onOpenHelp() },
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
                remaining = if (showTotal && headerSnap != null) {
                    app.necessityq.domain.ListCost.of(headerSnap.lines, prices, itemPrices)
                } else null,
                onBack = { vm.setMode(ListMode.PLAN) },
                onStore = { showStoreDialog = true },
            ) {
                IconButton(onClick = { addOpen = !addOpen }) {
                    Icon(Icons.Default.Add, contentDescription = "הוספת פריט", tint = NqTheme.colors.onShopHeader)
                }
                if (chatOn) messagesButton(NqTheme.colors.onShopHeader)
                menuButton(NqTheme.colors.onShopHeader)
            }
        } else {
            // one row: the household's name, the "הכנת רשימה | בקנייה" switch, chat and the ⋮ menu —
            // so the list itself starts higher (the members' pictures are on the home tab)
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    householdName,
                    Modifier.widthIn(max = 84.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                ModeSwitch(shopping = false, onSelect = vm::setMode, modifier = Modifier.weight(1f), compact = true)
                if (chatOn) messagesButton(MaterialTheme.colorScheme.onSurface)
                menuButton(MaterialTheme.colorScheme.onSurface)
            }
        }

        if (splitting) {
            TripStopsRow(tripStops, activeStop, stopCounts, allBranches, onSelect = vm::selectStop)
        }

        // standing in a followed branch while preparing: into shopping there in one tap
        val shopHere by vm.shopHereOffer.collectAsStateWithLifecycle()
        if (!shopping) shopHere?.let { branch ->
            ShopHereBanner(
                name = app.necessityq.domain.BranchLocator.storeName(branch),
                onShop = vm::shopHere,
                onDismiss = vm::dismissShopHere,
            )
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

        // list toolbar: sort (with direction), filter, collapse/expand all ("תצוגה" is in the ⋮ menu)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                AssistChip(
                    onClick = { showSortMenu = true },
                    label = { Text(sort.label + if (!sort.hasDirection) "" else if (descending) " ↓" else " ↑", maxLines = 1) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)) },
                )
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    sortOptions.forEach { option ->
                        val recommended = shopping && option == ListSort.CART_LOAD
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text((if (option == sort) "✓ " else "") + option.label + if (recommended) " (מומלץ)" else "")
                                    // what the order does, in a line under it
                                    option.hint?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            onClick = { vm.setSort(option); showSortMenu = false },
                        )
                    }
                    // ascending / descending only where it means something: by name and by the order of adding
                    if (sort.hasDirection) {
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
            }
            FilterChip(
                selected = filter.isActive,
                onClick = { showFilter = true },
                label = { Text(if (filter.isActive) "סינון · ${filter.activeCount}" else "סינון") },
                leadingIcon = { Icon(Icons.Default.FilterList, null, Modifier.size(18.dp)) },
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
        // the drag handle is on the tile, but dragging works only in the grouped order (unfiltered)
        if (TileElement.DRAG in tile && !dragAvailable) {
            Text(
                "גרירה זמינה במיון לפי קבוצות" + if (filter.isActive) " ובלי סינון" else "",
                Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val snap = snapshot
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).fadingEdges(listState, horizontal = false),
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

            // a feature that would probably help, told from the household's own data (at most one, once a week)
            advice?.takeIf { showAdvice }?.let { a ->
                item(key = "advice") {
                    AdviceCard(
                        a,
                        onAccept = { vm.acceptAdvice(a, onOpenPrices) },
                        onLater = { vm.laterAdvice(a) },
                        onDecline = { vm.declineAdvice(a) },
                    )
                }
            }

            if (showReminders) {
                item(key = "reminders") {
                    RemindersCard(shownReminders, onAdd = vm::reminderToList, onRemove = vm::dismissReminder)
                }
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

            if (showLow && snap.runningLow.isNotEmpty()) {
                item(key = "low") {
                    if (!lowExpanded) {
                        RunningLowLine(snap.runningLow.size, onOpen = { lowExpandedChoice = true })
                    } else Column(Modifier.padding(bottom = 6.dp)) {
                        SuggestionHeader(
                            title = "כנראה נגמר בקרוב",
                            icon = Icons.Default.Schedule,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            count = snap.runningLow.size,
                            expanded = true,
                            onToggle = { lowExpandedChoice = false },
                            onAddAll = { snap.runningLow.forEach { vm.accept(it) } },
                        )
                        val lowRow = androidx.compose.foundation.lazy.rememberLazyListState()
                        LazyRow(
                            Modifier.fadingEdges(lowRow, horizontal = true),
                            state = lowRow,
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

            if (showFrequent && snap.frequent.isNotEmpty()) {
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
                // the slot stays (empty) after the hint is learned, so row indexes do not shift
                item(key = "hint") {
                    val hint = swipes.hint
                    if (showSwipeHint && hint != null) Text(
                        "$hint — והחזיקו עד שהטבעת מהבהבת. ההסבר המלא: ⋮ ← עזרה",
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
                                subline = tileInfo(line, stats, names, tile, today),
                                tags = ItemTags.of(line, stats, favorite = line.itemId in favorites, compact = true)
                                    .filter { tile.showsTag(it.kind) },
                                chat = if (thread.isEmpty() || TileElement.CHAT !in tile) null else {
                                    val last = thread.maxBy { it.atMillis }
                                    ChatState(
                                        unread = vm.unreadCount(line.itemId, messages),
                                        lastIsMine = last.authorId == vm.me,
                                        status = if (last.authorId == vm.me) vm.deliveryStatus(last) else null,
                                    )
                                },
                                tile = tile,
                                tapMode = display.tapMode,
                                price = prices[line.itemId],
                                priceInfo = itemPrices[line.itemId],
                                // only from the gap (₪) chosen in the settings
                                comparison = comparisons[line.itemId]?.takeIf { compareOn && it.notable && it.reaches(display.compareMinShekels) },
                                promo = line.itemId in promoItems,
                                promoText = itemPromos[line.itemId]?.let { promoLabel(it, priceBranchId, allBranches) },
                                lifted = isDragging,
                                dragHandle = if (dragAvailable && TileElement.DRAG in tile) {
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
                                left = swipeAction(swipes.left, line, chatOn, vm),
                                right = swipeAction(swipes.right, line, chatOn, vm),
                                onPlus = { vm.adjustQuantity(line, +1) },
                                onMinus = { vm.adjustQuantity(line, -1) },
                                onSetQuantity = { q, approved -> vm.changeQuantity(line, q, approved) },
                                quantityCheck = { vm.quantityCheck(line, it) },
                                packs = mealData.packs,
                                onOpen = { openItem = line },
                            )
                        }
                    }
                }
            }

            // what was bought shows only in the shop (while making the list nothing is marked bought)
            if (shopping && display.showBought && snap.bought.isNotEmpty()) {
                item(key = "bought_header") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("נקנו (${snap.bought.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearBought() }) { Text("נקה לסל המחזור") }
                    }
                }
                items(snap.bought, key = { "b_" + it.id }) { line ->
                    BoughtRow(line, onReopen = { vm.reopen(line) })
                }
            }
        }

        if (shopping) {
            ShopBottomBar(onScan = scan, onFinish = vm::finishShopping)
        } else if (snap != null) {
            // one clear way into the store, with the estimated total of the open lines
            val open = snap.lines.count { it.status == LineStatus.NEEDED }
            if (open > 0) {
                GoShoppingBar(
                    open = open,
                    estimate = if (showTotal) app.necessityq.domain.ListCost.of(snap.lines, prices, itemPrices) else null,
                    storeName = storeName,
                    resume = tripStops.isNotEmpty(),
                    // a trip that was left with "back" (not finished) goes on as it was, split included;
                    // otherwise, with two branches or more: where it pays to shop. When the answer is the regular
                    // supermarket (or there is nothing to compare) we go at once, without the sheet.
                    onGo = {
                        val plan = tripPlan
                        when {
                            tripStops.isNotEmpty() -> vm.setMode(ListMode.SHOP)
                            !tripPlanOn || followedBranches.size < 2 -> vm.startTrip(null)
                            plan != null && plan.advice == app.necessityq.domain.TripAdvice.STAY -> {
                                vm.startTrip(plan)
                                vm.showMessage("הסופר הרגיל שלך הוא הזול לרשימה הזאת")
                            }
                            plan == null && pricesLoading.isEmpty() -> vm.startTrip(null)
                            else -> showTrip = true
                        }
                    },
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
    if (showTileEditor) {
        TileEditorSheet(
            mode = mode,
            // the stored tile (a feature turned off hides parts of it, but does not erase them)
            // as the list shows it: a part whose feature is off shows as hidden, and showing it turns the feature on
            layout = features.tile(display.tile(mode), mode),
            onSave = { vm.saveTile(mode, it); showTileEditor = false },
            onDismiss = { showTileEditor = false },
            onMoreSettings = { showTileEditor = false; onOpenDisplaySettings() },
        )
    }
    if (showGenerate) GenerateSheet(vm, onDismiss = { showGenerate = false })
    if (showTrip) TripPlanSheet(vm, onDismiss = { showTrip = false })
    if (showReset) ResetDialog(snapshot?.lines.orEmpty(), onReset = { vm.resetList(it); showReset = false }, onDismiss = { showReset = false })

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
            Text("לא חובה. מציג את המחירים של החנות ולומד את סדר האיסוף בה. אפשר לשנות בהמשך מהכותרת.", style = MaterialTheme.typography.bodySmall)
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

    // one list of stores: every followed branch is a store (with prices); a store without a branch is a manual one
    // (it still learns the order of picking, but has no prices). Where we stand, by location, comes first.
    val followedIds by vm.selectedPriceBranches.collectAsStateWithLifecycle()
    val allBranches by vm.priceBranches.collectAsStateWithLifecycle()
    val position by vm.userPosition.collectAsStateWithLifecycle()
    val followed = followedIds.mapNotNull { id -> allBranches.firstOrNull { it.id == id } }
        .map { b ->
            val lat = b.latitude
            val lon = b.longitude
            b to position?.let { (la, lo) -> if (lat != null && lon != null) app.necessityq.domain.Stores.distanceMeters(la, lo, lat, lon) else null }
        }
        .sortedBy { it.second ?: Double.MAX_VALUE }
    val branchStoreIds = followed.mapNotNull { (b, _) -> vm.storeOfBranch(b, stores)?.id }.toSet()
    val hereStore = nearby ?: hereBranch?.branch?.let { vm.storeOfBranch(it, stores) }
    val hereBranchNew = hereBranch?.branch?.takeIf { nearby == null && vm.storeOfBranch(it, stores) == null }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        hereStore?.let { near ->
            AssistChip(
                onClick = { onChosen(near.id) },
                label = { Text("כאן: ${near.name}", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)) },
            )
        }
        // the coordinates match a chain branch that is not one of the household's stores yet
        hereBranchNew?.let { b ->
            AssistChip(
                onClick = { vm.addStoreFromBranch(b)?.let(onChosen) },
                label = { Text("כאן: ${app.necessityq.domain.BranchLocator.storeName(b)}", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)) },
            )
        }
        if (hasPermission && hereStore == null && hereBranchNew == null) {
            AssistChip(
                onClick = { vm.locate(quiet = false) },
                enabled = !locating,
                label = { Text(if (locating) "מאתר מיקום…" else "איתור לפי מיקום") },
                leadingIcon = { Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)) },
            )
        }
        // the followed branches, nearest first
        followed.forEach { (b, meters) ->
            val store = vm.storeOfBranch(b, stores)
            FilterChip(
                selected = store != null && store.id == currentId,
                onClick = { vm.addStoreFromBranch(b)?.let(onChosen) },
                label = { Text(app.necessityq.domain.BranchLocator.storeName(b) + (meters?.let { " · " + distanceLabel(it) } ?: "")) },
                leadingIcon = { Icon(Icons.Default.Storefront, null, Modifier.size(18.dp)) },
            )
        }
        // stores without a followed branch: manual, without prices
        stores.filter { it.id !in branchStoreIds && it.id != hereStore?.id }.forEach { store ->
            FilterChip(selected = store.id == currentId, onClick = { onChosen(store.id) }, label = { Text(store.name + " · בלי מחירים") })
        }
        AssistChip(onClick = { adding = true }, label = { Text("חנות אחרת") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
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
            title = { Text("חנות אחרת") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("שם (למשל: רמי לוי תלפיות)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StoreType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
                    }
                    Text(
                        "חנות שאינה סניף במעקב נשמרת בלי מחירים (סניף עם מחירים מוסיפים בלשונית המחירים). " +
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
            val binList = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp).fadingEdges(binList, horizontal = false), state = binList) {
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

private fun tileInfo(line: ListLine, stats: ItemStats?, names: Map<String, String>, tile: TileLayout, today: LocalDate): String =
    listOfNotNull(
        if (TileElement.NOTE in tile) line.note else null,
        if (TileElement.LAST_BOUGHT in tile) daysAgo(stats, today) else null,
        if (TileElement.FREQUENCY in tile) cadence(stats) else null,
        if (TileElement.ADDED_BY in tile) names[line.addedBy]?.let { (if (line.isAuto) "נוצר אוטומטית עבור " else "הוסיף/ה: ") + it } else null,
    ).joinToString(" · ")

private fun recommendationInfo(s: Suggestion, stats: ItemStats?, favorites: Set<String>, display: DisplayOptions, today: LocalDate): String =
    listOfNotNull(
        if (display.recommendationDetail == app.necessityq.platform.RecommendationDetail.REASON) s.reason else null,
        if (display.recommendationDetail == app.necessityq.platform.RecommendationDetail.HISTORY) daysAgo(stats, today) else null,
        if (display.recommendationDetail == app.necessityq.platform.RecommendationDetail.HISTORY) cadence(stats) else null,
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
 * What [choice] does to [line] (null = nothing: the tile does not move that way). צ'אט פוד opens the product's
 * conversation; when צ'אט פוד is turned off, a swipe set to it does nothing.
 */
private fun swipeAction(choice: app.necessityq.domain.SwipeChoice, line: ListLine, chatOn: Boolean, vm: AppViewModel): SwipeAction? =
    when (choice) {
        app.necessityq.domain.SwipeChoice.NONE -> null
        app.necessityq.domain.SwipeChoice.BOUGHT -> SwipeAction("נקנה", Icons.Default.Check, NqPalette.SwipeBought) { vm.checkOff(line) }
        app.necessityq.domain.SwipeChoice.MISSING ->
            SwipeAction(if (line.missing) "ביטול חסר" else "חסר", Icons.Default.ReportProblem, NqPalette.SwipeMissing) { vm.setMissing(line, !line.missing) }
        app.necessityq.domain.SwipeChoice.REMOVE -> SwipeAction("הסרה", Icons.Default.Delete, NqPalette.SwipeDelete) { vm.delete(line) }
        app.necessityq.domain.SwipeChoice.CHAT -> if (!chatOn) null else {
            SwipeAction("צ'אט פוד", Icons.AutoMirrored.Filled.Chat, NqPalette.SwipeChat) { vm.requestOpenChat(line.itemId, line.displayName) }
        }
    }

/**
 * Physical-direction swipe: what each side does is chosen in the settings (by default, in the shop left = bought and
 * right = missing; while making the list right = remove). A side with no action ([left] / [right] null) does not
 * move. The icon and the action's name sit at the edge of the phone the tile was pulled away from, with the
 * instruction under the name: pull on → hold → release. The action runs when the tile is held past the
 * threshold until the ring fills; a forceful push runs it at once. Pulling back cancels.
 */
@Composable
private fun SwipeableTile(left: SwipeAction?, right: SwipeAction?, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    var width by remember { mutableFloatStateOf(1f) }
    // the drag writes the offset synchronously; once the finger lifts (or the gesture is cancelled) a single
    // spring brings the tile home. Launching a coroutine per drag delta let late snaps cancel that return,
    // leaving tiles stuck half-way.
    var offset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // bumped on every release so a quick flick (start + stop within one frame) still triggers the return
    var releases by remember { mutableIntStateOf(0) }
    LaunchedEffect(dragging, releases) {
        if (!dragging && offset != 0f) {
            animate(
                initialValue = offset,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> offset = value }
            offset = 0f
        }
    }
    val ring = remember { Animatable(0f) }
    var armedAt by remember { mutableStateOf<Long?>(null) }
    val threshold = width * 0.3f
    val past = abs(offset) >= threshold
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
        val toRight = offset > 0
        val action = if (offset == 0f) null else if (toRight) right else left
        if (action != null) {
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
                .absoluteOffset { IntOffset(offset.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    // a tile with no action on either side does not move at all
                    enabled = left != null || right != null,
                    state = rememberDraggableState { delta ->
                        // only towards a side that has an action
                        offset = (offset + delta).coerceIn(if (left != null) -width else 0f, if (right != null) width else 0f)
                    },
                    onDragStarted = { dragging = true },
                    // also called when the gesture is cancelled (e.g. the list takes over to scroll)
                    onDragStopped = { velocity ->
                        val dir = if (offset > 0) 1f else -1f
                        val pushed = offset != 0f && velocity * dir >= with(density) { PUSH_SPEED_DP.dp.toPx() } &&
                            abs(offset) >= width * 0.12f
                        val held = armedAt?.let { System.currentTimeMillis() - it >= HOLD_MS } == true
                        val stillPast = abs(offset) >= width * 0.3f
                        dragging = false
                        releases++
                        if (pushed || (held && stillPast)) (if (offset > 0) right else left)?.run()
                    },
                ),
        ) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ProductTile(
    line: ListLine,
    shopping: Boolean,
    subline: String,
    tags: List<ItemTag>,
    chat: ChatState?,
    tile: TileLayout,
    tapMode: TapMode,
    price: Double?,
    priceInfo: app.necessityq.domain.ItemPriceInfo? = null,
    /** significantly dearer / cheaper here than at the household's other branches */
    comparison: app.necessityq.domain.PriceComparison? = null,
    /** a promotion on the product at one of the followed branches: a gift in the tile's top-right corner */
    promo: Boolean = false,
    /** The promotion's details ("מבצע: 2 ב-10 ₪ · …"), in a strip under the tile; null = none. */
    promoText: String? = null,
    lifted: Boolean,
    dragHandle: (@Composable () -> Unit)?,
    /** the check circle (in the shop only) */
    onBought: () -> Unit,
    left: SwipeAction?,
    right: SwipeAction?,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onSetQuantity: ((Quantity?, Boolean) -> Unit)? = null,
    quantityCheck: (Quantity) -> app.necessityq.domain.QuantityCheck? = { null },
    /** The pack size the household buys per item ([app.necessityq.domain.MealData.packs]), for "לקנות". */
    packs: Map<String, Long> = emptyMap(),
    /** Opens the item's page: on a tap or a double tap, as chosen in the settings. */
    onOpen: () -> Unit,
) {
    // the quantity buttons are a part of the tile ("תצוגה"), whatever a tap does
    val showStepper = TileElement.STEPPER in tile
    val showQuantityText = TileElement.QUANTITY in tile && !showStepper && line.quantity != null
    // produce counted in pieces: estimated weight (the price next to it is per kilo / 100 g)
    val weight = if (TileElement.WEIGHT in tile) app.necessityq.domain.PieceWeights.estimate(line.name, line.quantity) else null

    Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) {
        SwipeableTile(left = left, right = right) {
          Box {
            Card(
                shape = NqShapes.Tile,
                border = when {
                    line.missing -> BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
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
                    val open = if (tapMode == TapMode.DOUBLE) {
                        Modifier.combinedClickable(onClick = {}, onDoubleClick = onOpen)
                    } else {
                        Modifier.clickable(onClickLabel = "פתיחת דף הפריט", onClick = onOpen)
                    }
                    Column(Modifier.weight(1f).then(open).padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            dragHandle?.invoke()
                            // marking bought is for the shop only
                            if (shopping && TileElement.CHECK in tile) {
                                val checkSize = 48.dp
                                Box(Modifier.size(checkSize).clip(CircleShape).clickable(onClick = onBought), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(checkSize - 18.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
                                }
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Spacer(Modifier.width(10.dp))
                            }
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
                                // a recipe's exact need, rounded up to what the shop sells ("לקנות 1.5 ליטר · צריך 1.1 ליטר")
                                val toBuy = line.toBuy(packs)
                                val need = line.quantity
                                if (toBuy != null && need != null && toBuy != need) {
                                    Text(
                                        "לקנות ${toBuy.format()} · צריך ${need.format()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
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
                            if (TileElement.PRICE in tile) {
                                PriceSlot(price = price, average = null, info = priceInfo, comparison = comparison)
                            }
                        }
                        if (subline.isNotBlank() || showStepper) {
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
                            }
                        }
                    }
                }
            }
            // a promotion on the product: a gift on the tile's top-right corner (its details are on the product page)
            if (promo) PromoGift(Modifier.align(AbsoluteAlignment.TopRight).absoluteOffset(x = 5.dp, y = (-5).dp))
          }
        }
        // the promotion's details, right under the tile (while preparing and in the shop)
        PromoStrip(promo = promoText)
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
    val chipRow = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyRow(
        Modifier.fillMaxWidth().fadingEdges(chipRow, horizontal = true),
        state = chipRow,
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
            val proposalList = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).fadingEdges(proposalList, horizontal = false), state = proposalList) {
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
        icon = { Icon(Icons.Default.DeleteSweep, null) },
        title = { Text("ניקוי הרשימה") },
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
            TextButton(enabled = ListReset.linesFor(scope, lines).isNotEmpty(), onClick = { onReset(scope) }) { Text("ניקוי") }
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
internal fun ChatIndicator(state: ChatState) {
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

/**
 * The item's price for its basic amount — always per unit for packaged goods, and per kilo or per 100 g (as chosen in
 * the display settings) for goods sold by weight; never multiplied by the quantity on the list (the estimated total at
 * the bottom of the list does that).
 */
@Composable
fun PriceSlot(
    price: Double?,
    average: Double?,
    info: app.necessityq.domain.ItemPriceInfo? = null,
    /** a notable gap with the household's other branches: "▲ 12%" (dearer) or "▼ 12%" (cheaper) under the price */
    comparison: app.necessityq.domain.PriceComparison? = null,
) {
    val unit = LocalWeightPriceUnit.current
    val none = "\u200E—\u00A0₪\u200E"
    // the household's own price wins; otherwise the branch data: chosen product, or the typical range
    val basic = info?.let { app.necessityq.domain.UnitPrices.of(it, unit) }
    val (main, sub) = when {
        price != null -> app.necessityq.domain.PriceData.formatPrice(price) to app.necessityq.domain.UnitPrices.PER_UNIT
        basic != null -> app.necessityq.domain.PriceData.formatRange(basic.low, basic.high) to basic.per
        // sold by weight, but per what weight is not known: no price rather than a wrong one
        info != null -> none to "במשקל"
        else -> none to "ממוצע " + (average?.let { app.necessityq.domain.PriceData.formatPrice(it) } ?: "—")
    }
    Column(
        Modifier.widthIn(min = 68.dp).clip(NqShapes.Panel)
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // always two lines, so a tile with a comparison is the same height as all the others:
        // the "▲ 12%" sits on the price's own line
        Row(verticalAlignment = Alignment.CenterVertically) {
            comparison?.takeIf { it.notable }?.let { c ->
                Text(
                    c.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = comparisonInk(c.level),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(end = 4.dp).semantics { contentDescription = c.label },
                )
            }
            Text(main, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

/** Text colour of a price comparison: green for cheaper, amber for dearer (as on the scan sheet). */
@Composable
fun comparisonInk(level: app.necessityq.domain.PriceLevel): androidx.compose.ui.graphics.Color = when (level) {
    app.necessityq.domain.PriceLevel.CHEAP -> MaterialTheme.colorScheme.primary
    app.necessityq.domain.PriceLevel.EXPENSIVE -> MaterialTheme.colorScheme.secondary
    app.necessityq.domain.PriceLevel.FAIR -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** "יקר ב-12% מהסניפים שלך" as a filled pill (cheaper green, dearer amber, about the same muted). */
@Composable
fun PriceComparisonPill(c: app.necessityq.domain.PriceComparison, modifier: Modifier = Modifier) {
    val fill = comparisonInk(c.level)
    val ink = when (c.level) {
        app.necessityq.domain.PriceLevel.CHEAP -> MaterialTheme.colorScheme.onPrimary
        app.necessityq.domain.PriceLevel.EXPENSIVE -> MaterialTheme.colorScheme.onSecondary
        app.necessityq.domain.PriceLevel.FAIR -> MaterialTheme.colorScheme.surface
    }
    Text(
        c.label,
        modifier.clip(NqShapes.Pill).background(fill).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = ink,
    )
}

/**
 * The text of an item's promotion: what it is, the price with it, where (when it is not the branch the list shows)
 * and a stock warning when it depends on stock.
 */
private fun promoLabel(o: app.necessityq.domain.PromoOffer, here: String?, all: List<app.necessityq.domain.PriceBranch>): String {
    val price = " · ${app.necessityq.domain.PriceData.formatPrice(o.promoUnit)} במקום ${app.necessityq.domain.PriceData.formatPrice(o.regular)}"
    val where = if (o.branchId == here) "" else all.firstOrNull { it.id == o.branchId }?.let { " · ב" + app.necessityq.domain.BranchLocator.storeName(it) }.orEmpty()
    val stock = o.promotion.stockWarning?.let { " · $it" }.orEmpty()
    return "מבצע: ${o.promotion.shortLabel}$price$where$stock"
}

/** A product with a promotion: a small gift in the promotions' orange, on the tile's corner. */
@Composable
fun PromoGift(modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary)
            .semantics { contentDescription = "יש מבצע על המוצר" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(14.dp))
    }
}

/** Promotion line right under a product tile; renders nothing when there is no promotion. */
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
    // only the ✓ brings an item back to the list: a touch elsewhere on the row (while scrolling) does nothing
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onReopen) {
            Icon(Icons.Default.CheckCircle, contentDescription = "החזרה לרשימה: ${line.displayName}", tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(2.dp))
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
fun ModeSwitch(shopping: Boolean, onSelect: (ListMode) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(
        modifier.fillMaxWidth().clip(NqShapes.Field).background(NqTheme.colors.segmentTrack).padding(if (compact) 3.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
    ) {
        listOf(ListMode.PLAN, ListMode.SHOP).forEach { m ->
            val selected = (m == ListMode.SHOP) == shopping
            Row(
                Modifier.weight(1f).height(if (compact) 34.dp else 40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(m) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // in the narrow one-row header the cart icon would squeeze the label
                if (m == ListMode.SHOP && !compact) {
                    Icon(
                        Icons.Default.ShoppingCart, null, Modifier.size(16.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    m.label,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

/** "נראה שאתם ב… — לעבור למצב קנייה?": the location shows a followed branch while the list is being prepared. */
@Composable
private fun ShopHereBanner(name: String, onShop: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp).clip(NqShapes.Panel)
            .background(MaterialTheme.colorScheme.primaryContainer).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "נראה שאתם ב$name — לעבור למצב קנייה?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onShop) { Text("לקנייה", fontWeight = FontWeight.SemiBold) }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "לא עכשיו", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** "לבדוק שוב": products saved from a scan, with the price seen then; each goes on the list or away. */
@Composable
private fun RemindersCard(
    reminders: List<app.necessityq.domain.ScanReminder>,
    onAdd: (app.necessityq.domain.ScanReminder) -> Unit,
    onRemove: (app.necessityq.domain.ScanReminder) -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    val shown = if (showAll) reminders else reminders.take(3)
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(NqShapes.Tile)
            .background(MaterialTheme.colorScheme.surface).border(1.dp, NqTheme.colors.hairline, NqShapes.Tile)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("לבדוק שוב", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (reminders.size > 3) {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "פחות" else "הכל (${reminders.size})") }
            }
        }
        shown.forEach { r ->
            androidx.compose.runtime.key(r.code) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        r.price?.let {
                            Text(
                                "${app.necessityq.domain.PriceData.formatPrice(it)} בסריקה",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { onAdd(r) }) { Text("לרשימה", fontWeight = FontWeight.SemiBold) }
                    IconButton(onClick = { onRemove(r) }) {
                        Icon(Icons.Default.Close, contentDescription = "להסיר: ${r.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** "כנראה נגמר בקרוב" folded into one line: how many, and a tap opens the cards to add them. */
@Composable
private fun RunningLowLine(count: Int, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(NqShapes.Panel)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = Role.Button, onClickLabel = "הצגת המוצרים שכנראה נגמרים", onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            (if (count == 1) "מוצר אחד כנראה נגמר" else "$count מוצרים כנראה נגמרים") + " · ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            "הוספה ›",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
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
private fun GoShoppingBar(
    open: Int,
    estimate: app.necessityq.domain.ListCostEstimate?,
    storeName: String?,
    resume: Boolean = false,
    onGo: () -> Unit,
) {
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
                Text(if (resume) "חזרה לקנייה" else "יוצאים לקנות", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
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

/**
 * A suggestion from the app ([app.necessityq.domain.FeatureAdvisor]): what would probably help and why — told from
 * the household's own data. "לא תודה" never offers it again; "אולי אחר כך" brings it back in two weeks.
 */
@Composable
private fun AdviceCard(
    advice: app.necessityq.domain.FeatureSuggestion,
    onAccept: () -> Unit,
    onLater: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(NqShapes.Tile)
            .background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                advice.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
        Text(advice.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onAccept) { Text(advice.action) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLater) { Text("אולי אחר כך") }
            TextButton(onClick = onDecline) { Text("לא תודה") }
        }
    }
}
