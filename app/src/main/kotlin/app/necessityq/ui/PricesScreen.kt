package app.necessityq.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import app.necessityq.ui.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.BranchPriceStatus
import app.necessityq.domain.BranchProduct
import app.necessityq.domain.HebrewText
import app.necessityq.domain.NearbyBranch
import app.necessityq.domain.PriceBranch
import app.necessityq.domain.PriceData
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Product hits for [query]; [done] when every step of the search has finished. */
private data class SearchResults(val query: String, val items: List<app.necessityq.domain.ProductAcross>, val done: Boolean)

private const val PRODUCT_SEARCH_DELAY_MS = 120L

private enum class PricesTab(val label: String) { BRANCHES("סניפים קרובים"), PRODUCTS("מוצרים ומחירים") }

/** Branches near the user (from the price-transparency files) and product prices with ranges per chosen branch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricesScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val branches by vm.priceBranches.collectAsStateWithLifecycle()
    val branchesError by vm.priceBranchesError.collectAsStateWithLifecycle()
    val selected by vm.selectedPriceBranches.collectAsStateWithLifecycle()
    val status by vm.priceStatus.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (selected.isEmpty()) PricesTab.BRANCHES else PricesTab.PRODUCTS) }
    // searching for a product: the title and the tabs make room, so the search field sits at the top and the results get the screen
    var searching by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedVisibility(visible = !searching) {
            Column {
                TopAppBar(
                    navigationIcon = { BackNavButton() },
                    title = { Text("סניפים ומחירים", fontWeight = FontWeight.ExtraBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PricesTab.entries.forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) }) }
                }
            }
        }
        if (branches.isEmpty()) {
            Text(
                "עדיין אין נתוני סניפים. השרת קורא את קבצי השקיפות של הרשתות פעם ביום; " +
                    "אם המסך נשאר ריק, ייתכן שפונקציות המחירים עוד לא הופעלו בפרויקט." +
                    (branchesError?.let { "\n\nשגיאת קריאה (מנסה שוב): $it" } ?: ""),
                Modifier.padding(16.dp),
            )
            return@Column
        }
        when (tab) {
            PricesTab.BRANCHES -> BranchesTab(vm, branches, selected, status)
            PricesTab.PRODUCTS -> ProductsTab(
                vm, branches.filter { it.id in selected }, status,
                onPickBranches = { tab = PricesTab.BRANCHES },
                onSearching = { searching = it },
            )
        }
    }
}

@Composable
private fun BranchesTab(vm: AppViewModel, branches: List<PriceBranch>, selected: List<String>, status: Map<String, BranchPriceStatus>) {
    val position by vm.userPosition.collectAsStateWithLifecycle()
    val here by vm.hereBranch.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    var radiusKm by rememberSaveable { mutableStateOf(5.0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.refreshPosition()
    }
    LaunchedEffect(Unit) { if (vm.hasLocationPermission()) vm.locate() }

    val list = remember(branches, position, radiusKm, filter, selected) {
        val key = HebrewText.matchKey(filter)
        PriceData.nearby(branches, position?.first, position?.second, radiusKm)
            .filter { it.branch.id !in selected }
            .filter { key.isBlank() || HebrewText.matchKey("${it.branch.title} ${it.branch.address}").contains(key) }
    }
    // the followed branches stay at the top whatever the radius or the search, nearest first
    val pinned = remember(branches, position, selected) {
        PriceData.nearby(branches.filter { it.id in selected }, position?.first, position?.second, radiusKm = Double.MAX_VALUE)
    }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (position == null) {
            OutlinedButton(onClick = {
                if (vm.hasLocationPermission()) vm.refreshPosition()
                else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }) {
                Icon(Icons.Default.MyLocation, contentDescription = null)
                Text("  הצגה לפי המיקום שלי")
            }
        } else {
            Row(Modifier.fadingHorizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("רדיוס:")
                listOf(2.0, 5.0, 10.0, 25.0).forEach { r ->
                    FilterChip(selected = radiusKm == r, onClick = { radiusKm = r }, label = { Text("${r.toInt()} ק\"מ") })
                }
                IconButton(onClick = { vm.refreshPosition() }, enabled = !locating) { Icon(Icons.Default.MyLocation, contentDescription = "עדכון מיקום") }
            }
        }
        if (locating) LinearProgressIndicator(Modifier.fillMaxWidth())
        here?.let { nb ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("אתם כנראה ב${nb.branch.title}", fontWeight = FontWeight.Bold)
                        Text("לפי המיקום (${PriceData.formatDistance(nb.distanceMeters ?: 0.0)} מהכתובת)", style = MaterialTheme.typography.bodySmall)
                    }
                    if (nb.branch.id !in selected) OutlinedButton(onClick = vm::followHereBranch) { Text("מעקב") }
                    else Text("במעקב ✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        OutlinedTextField(
            filter, { filter = it }, label = { Text("חיפוש רשת או סניף") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = if (filter.isNotEmpty()) { { IconButton(onClick = { filter = "" }) { Icon(Icons.Default.Close, contentDescription = "מחיקת הטקסט") } } } else null,
        )
        Text(
            "סמנו את הסניפים שאתם קונים בהם (עד ${AppViewModel.MAX_BRANCHES}). המחירים שלהם נטענים פעם ביום, וגם כשנכנסים למצב \"בקנייה\".",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    val branchList = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().fadingEdges(branchList, horizontal = false), state = branchList, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        if (pinned.isNotEmpty()) {
            item(key = "pinned-title") { BranchSectionTitle("במעקב · ${pinned.size}") }
            items(pinned, key = { "pinned-" + it.branch.id }) { nb -> BranchRow(nb, true, status[nb.branch.id]) { vm.toggleBranch(nb.branch.id) } }
            item(key = "others-title") { BranchSectionTitle("סניפים נוספים") }
        }
        if (list.isEmpty()) item(key = "empty") { Text("אין סניפים נוספים ברדיוס הזה.") }
        items(list, key = { it.branch.id }) { nb -> BranchRow(nb, false, status[nb.branch.id]) { vm.toggleBranch(nb.branch.id) } }
    }
}

@Composable
private fun BranchSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun BranchRow(nb: NearbyBranch, checked: Boolean, status: BranchPriceStatus?, onToggle: () -> Unit) {
    val b = nb.branch
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(b.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(b.address, b.city).filter { it.isNotBlank() }.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            if (checked) Text(statusLine(status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        nb.distanceMeters?.let { Text((if (b.approximate) "~" else "") + PriceData.formatDistance(it), style = MaterialTheme.typography.labelLarge) }
    }
    HorizontalDivider()
}

/**
 * Product prices at the followed branches. Several branches (or "הכל") can be chosen: each product found then shows
 * its price at every chosen branch, the cheapest marked. With one branch, each product is compared with the same
 * barcode at the household's other branches. The search runs once typing pauses, off the main thread.
 */
@Composable
private fun ProductsTab(
    vm: AppViewModel,
    mine: List<PriceBranch>,
    status: Map<String, BranchPriceStatus>,
    onPickBranches: () -> Unit,
    /** true while the search field is focused or holds a text: the screen's title and tabs then fold away. */
    onSearching: (Boolean) -> Unit = {},
) {
    if (mine.isEmpty()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("עוד לא נבחרו סניפים.")
            OutlinedButton(onClick = onPickBranches) { Text("בחירת סניפים קרובים") }
        }
        return
    }
    val mineIds = mine.map { it.id }
    // chosen branch ids, saved as one line (ids are "chain_store", no commas)
    var chosenRaw by rememberSaveable { mutableStateOf(mineIds.joinToString(",")) }
    // branches no longer followed drop out; with none left, all are chosen
    val chosen = chosenRaw.split(',').toSet().let { set -> mine.filter { it.id in set } }.ifEmpty { mine }
    val ids = chosen.map { it.id }
    val allChosen = chosen.size == mine.size
    val comparing = chosen.size > 1
    var query by rememberSaveable { mutableStateOf("") }
    var debounced by remember { mutableStateOf(query.trim()) }
    var focused by remember { mutableStateOf(false) }
    val searchActive = focused || query.isNotBlank()
    LaunchedEffect(searchActive) { onSearching(searchActive) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onSearching(false) } }
    // the first scroll through the results closes the keyboard (and takes the focus from the field)
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val closeKeyboardOnScroll = remember(focusManager, keyboard) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y != 0f) {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    LaunchedEffect(query) {
        // short: the results come in steps, so a search started too early only costs a cancelled step
        if (query.isNotBlank()) kotlinx.coroutines.delay(PRODUCT_SEARCH_DELAY_MS)
        debounced = query.trim()
    }
    val all by vm.branchPrices.collectAsStateWithLifecycle()
    val loading by vm.pricesLoading.collectAsStateWithLifecycle()
    // load every followed branch so products can be compared across them
    LaunchedEffect(mineIds, status.values.map { it.updatedAtMillis }) { mineIds.forEach { vm.loadPrices(it) } }

    val versions = ids.map { all[it]?.updatedAtMillis }
    val otherVersions = mineIds.map { all[it]?.updatedAtMillis }
    // the results with the text they answer, so "not found" is not shown while a search is still running
    // the hits come in steps (the quick pass first, more as the slower pass finishes); the earlier hits stay until the first step arrives
    val searched by produceState(SearchResults("", emptyList(), true), ids, versions, debounced) {
        value = value.copy(done = false)
        vm.searchProductsProgressive(ids, debounced).collect { value = SearchResults(debounced, it, false) }
        value = SearchResults(debounced, value.items.takeIf { value.query == debounced }.orEmpty(), true)
    }
    val results = searched.items
    // one branch: each product against the same barcode at the household's other branches
    val singleComparisons by produceState(emptyMap<String, app.necessityq.domain.PriceComparison>(), results, comparing, otherVersions) {
        value = if (comparing || results.isEmpty()) emptyMap() else vm.compareProducts(results.map { it.product }, ids.first())
    }
    val searching = query.trim() != debounced || !searched.done || searched.query != debounced
    val scan = rememberBarcodeScan(vm) { code -> query = app.necessityq.domain.Barcodes.normalize(code) ?: code }
    val anyLoading = ids.any { it in loading }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(visible = !searchActive) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fadingHorizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mine.size > 1) FilterChip(selected = allChosen, onClick = { chosenRaw = mineIds.joinToString(",") }, label = { Text("הכל") })
            mine.forEach { b ->
                val on = b.id in ids
                FilterChip(
                    selected = on && !(allChosen && mine.size > 1),
                    onClick = {
                        chosenRaw = when {
                            // from "הכל", tapping a branch picks just that one
                            allChosen && mine.size > 1 -> listOf(b.id)
                            on -> (ids - b.id).ifEmpty { ids }
                            else -> ids + b.id
                        }.joinToString(",")
                    },
                    label = { Text(b.title) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (comparing) "השוואה בין ${chosen.size} סניפים · " + (if (anyLoading) "טוען מחירים…" else "הזול בכל מוצר מסומן")
                else statusLine(status[ids.first()]),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { ids.forEach(vm::refreshPrices) }, enabled = !anyLoading) {
                Icon(Icons.Default.Refresh, contentDescription = if (comparing) "רענון המחירים של הסניפים שנבחרו" else "רענון המחירים של הסניף")
            }
        }
          }
        }
        if (anyLoading || searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(
            query, { query = it }, label = { Text("חיפוש מוצר או ברקוד") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "מחיקת הטקסט") }
                    IconButton(onClick = scan) { Icon(Icons.Default.QrCodeScanner, contentDescription = "סריקת ברקוד") }
                }
            },
        )
        val loadedCount = ids.count { all.containsKey(it) }
        if (loadedCount > 0 && query.isBlank()) {
            val products = ids.sumOf { all[it]?.products?.size ?: 0 }
            Text(
                (if (comparing) "$products מוצרים ב-$loadedCount סניפים. " else "$products מוצרים בסניף. ") + "הקלידו שם מוצר, למשל \"קוטג 5%\".",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (loadedCount == 0 && !anyLoading) Text("המחירים עוד לא נטענו. נסו לרענן בעוד כמה דקות.", style = MaterialTheme.typography.bodySmall)
        if (!searching && debounced.isNotBlank() && loadedCount > 0 && results.isEmpty()) Text("לא נמצאו מוצרים.", style = MaterialTheme.typography.bodySmall)
    }
    val productList = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().nestedScroll(closeKeyboardOnScroll).fadingEdges(productList, horizontal = false), state = productList, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(results, key = { r -> "${r.product.internalCode}:${r.at.keys.first()}:${r.product.code}" }) { r ->
            if (comparing) CompareCard(r, chosen, onAdd = { vm.addProductToList(r.product) })
            else ProductCard(r.product, all[ids.first()]?.rangeDays ?: 90, singleComparisons[r.product.code], mine, onAdd = { vm.addProductToList(r.product) })
        }
    }
}

/**
 * One product at each chosen branch: the cheapest marked, the others with how much dearer they are. Prices are in
 * the price unit of the listed product (per 100 g or per kg alike); a branch whose unit can't be matched shows its
 * own price, unmarked.
 */
@Composable
private fun CompareCard(r: app.necessityq.domain.ProductAcross, chosen: List<PriceBranch>, onAdd: () -> Unit) {
    val p = r.product
    val unit = LocalWeightPriceUnit.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Bold)
                    productDetails(p)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                Text(shownProductPrice(p, r.cheapest, unit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
            val cheapestIds = r.cheapestBranchIds
            chosen.forEach { b ->
                val at = r.at[b.id]
                val price = r.prices[b.id]
                val cheapest = price != null && b.id in cheapestIds && r.prices.size > 1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        b.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (cheapest) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    when {
                        at == null -> Text("לא נמכר", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // sold there, but priced per a unit that can't be matched: its own price, not compared
                        price == null -> {
                            Text("ביחידה אחרת  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(shownProductPrice(at, at.price, unit), style = MaterialTheme.typography.bodyMedium)
                        }
                        else -> {
                            if (cheapest) Text("הזול  ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            else if (r.prices.size > 1 && r.cheapest > 0) {
                                val diff = price / r.cheapest - 1
                                if (diff >= 0.005) Text(
                                    "+${app.necessityq.domain.PriceComparisons.percent(diff)}  ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (diff >= app.necessityq.domain.PriceComparisons.SIGNIFICANT) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                shownProductPrice(p, price, unit),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (cheapest) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (cheapest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            if (p.internalCode) Text("קוד פנימי של הרשת — לא מושווה לרשתות אחרות", style = MaterialTheme.typography.labelSmall)
            AddToListButton(onAdd)
        }
    }
}

private fun productDetails(p: BranchProduct): String? = listOfNotNull(
    p.brand.takeIf { it.isNotBlank() },
    p.packageSize?.let { q -> "${trim(q)} ${p.unit}".trim() }?.takeIf { !p.soldByWeight },
    if (p.soldByWeight) "במשקל" else null,
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
private fun ProductCard(p: BranchProduct, rangeDays: Int, comparison: app.necessityq.domain.PriceComparison?, mine: List<PriceBranch>, onAdd: () -> Unit) {
    val unit = LocalWeightPriceUnit.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Bold)
                    productDetails(p)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                Text(shownProductPrice(p, p.price, unit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                "טווח $rangeDays יום בסניף: " + if (p.high > p.low) "${shownProductPrice(p, p.low, unit)}–${shownProductPrice(p, p.high, unit)}" else shownProductPrice(p, p.low, unit),
                style = MaterialTheme.typography.bodyMedium,
            )
            p.unitPrice?.let { Text("מחיר ליחידת מידה: ${PriceData.formatPrice(it)}", style = MaterialTheme.typography.bodySmall) }
            if (p.since.isNotBlank()) Text("במחיר הזה מאז ${p.since.split('-').reversed().joinToString("/")}", style = MaterialTheme.typography.bodySmall)
            if (comparison != null) {
                val quotes = (listOf(comparison.here) + comparison.others).map { comparison.shown(it, unit) }
                val low = quotes.minOf { it.low }
                val high = quotes.maxOf { it.high }
                val cheapest = mine.firstOrNull { it.id == comparison.cheapestBranchId }?.title
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        "בסניפים שלך (${quotes.size}): ${PriceData.formatRange(low, high)}${if (comparison.unitKilos != null) " " + unit.label else ""}" + if (high > low && cheapest != null) " · הזול: $cheapest" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (comparison.notable) PriceComparisonPill(comparison)
                }
            }
            if (p.internalCode) Text("קוד פנימי של הרשת — לא מושווה לרשתות אחרות", style = MaterialTheme.typography.labelSmall)
            AddToListButton(onAdd)
        }
    }
}

/** "לרשימה": adds the generic item ("קוטג' 5%"), and the exact product is remembered for it. */
@Composable
private fun AddToListButton(onAdd: () -> Unit) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onAdd,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        modifier = Modifier.padding(top = 4.dp).height(36.dp),
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(" לרשימה")
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())

private fun statusLine(s: BranchPriceStatus?): String = when {
    s == null -> "המחירים ממתינים לטעינה הראשונה"
    s.updatedAtMillis == null && s.error != null -> "הטעינה נכשלה: ${s.error}"
    s.updatedAtMillis == null -> "המחירים ממתינים לטעינה הראשונה"
    else -> "עודכן ${timeFormat.format(Instant.ofEpochMilli(s.updatedAtMillis ?: 0L))} · ${s.items} מוצרים" +
        if (s.error != null) " (הטעינה האחרונה נכשלה)" else ""
}

private fun trim(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
