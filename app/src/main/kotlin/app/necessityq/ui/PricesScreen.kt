package app.necessityq.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.BranchPriceStatus
import app.necessityq.domain.BranchPrices
import app.necessityq.domain.BranchProduct
import app.necessityq.domain.HebrewText
import app.necessityq.domain.NearbyBranch
import app.necessityq.domain.PriceBranch
import app.necessityq.domain.PriceData
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { DrawerButton() },
            title = { Text("סניפים ומחירים", fontWeight = FontWeight.ExtraBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PricesTab.entries.forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) }) }
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
            PricesTab.PRODUCTS -> ProductsTab(vm, branches.filter { it.id in selected }, status, onPickBranches = { tab = PricesTab.BRANCHES })
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

    val list = remember(branches, position, radiusKm, filter) {
        val key = HebrewText.matchKey(filter)
        PriceData.nearby(branches, position?.first, position?.second, radiusKm)
            .filter { key.isBlank() || HebrewText.matchKey("${it.branch.title} ${it.branch.address}").contains(key) }
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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        OutlinedTextField(filter, { filter = it }, label = { Text("חיפוש רשת או סניף") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(
            "סמנו את הסניפים שאתם קונים בהם (עד ${AppViewModel.MAX_BRANCHES}). המחירים שלהם נטענים פעם ביום, וגם כשנכנסים למצב \"בקנייה\".",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        if (list.isEmpty()) item { Text("אין סניפים ברדיוס הזה.") }
        items(list, key = { it.branch.id }) { nb -> BranchRow(nb, nb.branch.id in selected, status[nb.branch.id]) { vm.toggleBranch(nb.branch.id) } }
    }
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

@Composable
private fun ProductsTab(vm: AppViewModel, mine: List<PriceBranch>, status: Map<String, BranchPriceStatus>, onPickBranches: () -> Unit) {
    if (mine.isEmpty()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("עוד לא נבחרו סניפים.")
            OutlinedButton(onClick = onPickBranches) { Text("בחירת סניפים קרובים") }
        }
        return
    }
    var chosenId by rememberSaveable { mutableStateOf(mine.first().id) }
    val branchId = chosenId.takeIf { id -> mine.any { it.id == id } } ?: mine.first().id
    var query by rememberSaveable { mutableStateOf("") }
    val all by vm.branchPrices.collectAsStateWithLifecycle()
    val loading by vm.pricesLoading.collectAsStateWithLifecycle()
    // load every followed branch so each product can show the range across them
    LaunchedEffect(mine.map { it.id }, status.values.map { it.updatedAtMillis }) { mine.forEach { vm.loadPrices(it.id) } }

    val current: BranchPrices? = all[branchId]
    val mineLoaded = remember(all, mine) { all.filterKeys { id -> mine.any { it.id == id } } }
    val results = remember(current, query) { current?.let { PriceData.search(it.products, query) }.orEmpty() }
    val scan = rememberBarcodeScan(vm) { code -> query = app.necessityq.domain.Barcodes.normalize(code) ?: code }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            mine.forEach { b -> FilterChip(selected = b.id == branchId, onClick = { chosenId = b.id }, label = { Text(b.title) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(statusLine(status[branchId]), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.refreshPrices(branchId) }, enabled = branchId !in loading) {
                Icon(Icons.Default.Refresh, contentDescription = "רענון המחירים של הסניף")
            }
        }
        if (branchId in loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(
            query, { query = it }, label = { Text("חיפוש מוצר או ברקוד") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton(onClick = scan) { Icon(Icons.Default.QrCodeScanner, contentDescription = "סריקת ברקוד") } },
        )
        if (current != null && query.isBlank()) Text("${current.products.size} מוצרים בסניף. הקלידו שם מוצר, למשל \"קוטג 5%\".", style = MaterialTheme.typography.bodySmall)
        if (current == null && branchId !in loading) Text("המחירים של הסניף עוד לא נטענו. נסו לרענן בעוד כמה דקות.", style = MaterialTheme.typography.bodySmall)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(results, key = { it.code }) { p ->
            ProductCard(p, current?.rangeDays ?: 90, if (mineLoaded.size > 1) PriceData.acrossBranches(p.code, mineLoaded) else null, mine)
        }
    }
}

@Composable
private fun ProductCard(p: BranchProduct, rangeDays: Int, across: app.necessityq.domain.CrossBranchRange?, mine: List<PriceBranch>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.Bold)
                    val details = listOfNotNull(
                        p.brand.takeIf { it.isNotBlank() },
                        p.packageSize?.let { q -> "${trim(q)} ${p.unit}".trim() }?.takeIf { !p.soldByWeight },
                        if (p.soldByWeight) "במשקל" else null,
                    )
                    if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                Text(PriceData.formatPrice(p.price), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
            Text("טווח $rangeDays יום בסניף: ${PriceData.formatRange(p.low, p.high)}", style = MaterialTheme.typography.bodyMedium)
            p.unitPrice?.let { Text("מחיר ליחידת מידה: ${PriceData.formatPrice(it)}", style = MaterialTheme.typography.bodySmall) }
            if (p.since.isNotBlank()) Text("במחיר הזה מאז ${p.since.split('-').reversed().joinToString("/")}", style = MaterialTheme.typography.bodySmall)
            if (across != null && across.branches > 1) {
                val cheapest = mine.filter { it.id in across.cheapestBranchIds }.joinToString(", ") { it.title }
                Text(
                    "בסניפים שלך (${across.branches}): ${PriceData.formatRange(across.low, across.high)}" +
                        if (across.high > across.low) " · הזול: $cheapest" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (p.internalCode) Text("קוד פנימי של הרשת — לא מושווה לרשתות אחרות", style = MaterialTheme.typography.labelSmall)
        }
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
