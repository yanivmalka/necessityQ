package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.PriceBranch
import app.necessityq.domain.PriceData
import app.necessityq.domain.PromoOffer
import app.necessityq.domain.TripAdvice
import app.necessityq.domain.TripOption
import app.necessityq.domain.TripPlan

/** A branch's short name: "רמי לוי תלפיות" (the chain and the branch). */
private fun branchName(id: String, branches: List<PriceBranch>): String =
    branches.firstOrNull { it.id == id }?.let { b -> if (b.name.contains(b.chainName)) b.name else "${b.chainName} ${b.name}" } ?: id

private fun shekels(v: Double): String = PriceData.formatPrice(v, if (v >= 100) 0 else 2)

private fun optionText(o: TripOption, branches: List<PriceBranch>): String =
    o.branchIds.joinToString(" + ") { branchName(it, branches) }

/**
 * Between the list and the shopping: where it pays to buy it. The user says from what saving another supermarket is
 * worth it and over how many supermarkets the list may be split; the app recommends the regular supermarket, another
 * one, or a split (and makes the split itself), with the reasons and the promotions on the list's items.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripPlanSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val settings by vm.tripSettings.collectAsStateWithLifecycle()
    val plan by vm.tripPlan.collectAsStateWithLifecycle()
    val offers by vm.promoOffers.collectAsStateWithLifecycle()
    val followed by vm.selectedPriceBranches.collectAsStateWithLifecycle()
    val all by vm.priceBranches.collectAsStateWithLifecycle()
    val loading by vm.pricesLoading.collectAsStateWithLifecycle()
    var thresholdText by remember { mutableStateOf(settings.threshold.toString()) }
    var homeMenu by remember { mutableStateOf(false) }
    val home = vm.homeBranchId(settings)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("איפה הכי משתלם לקנות?", style = MaterialTheme.typography.titleLarge)

            // the regular supermarket
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("הסופר הרגיל שלי:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(4.dp))
                androidx.compose.foundation.layout.Box {
                    TextButton(onClick = { homeMenu = true }) { Text(home?.let { branchName(it, all) } ?: "—", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    DropdownMenu(expanded = homeMenu, onDismissRequest = { homeMenu = false }) {
                        followed.forEach { id ->
                            DropdownMenuItem(
                                text = { Text((if (id == home) "✓ " else "") + branchName(id, all)) },
                                onClick = { vm.setTripSettings(settings.copy(homeBranchId = id)); homeMenu = false },
                            )
                        }
                    }
                }
            }

            // from what saving another supermarket (or one more stop) is worth it
            Text("כדאי לשנות סופר אם החיסכון לפחות:", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { t ->
                        val digits = t.filter { it.isDigit() }.take(5)
                        thresholdText = digits
                        digits.toIntOrNull()?.let { vm.setTripSettings(settings.copy(threshold = it)) }
                    },
                    suffix = { Text("₪") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(20, 50, 100, 200)) { v ->
                        FilterChip(
                            selected = settings.threshold == v,
                            onClick = { thresholdText = v.toString(); vm.setTripSettings(settings.copy(threshold = v)) },
                            label = { Text("$v") },
                        )
                    }
                }
            }

            // over how many supermarkets the list may be split
            Text("לכמה סופרים מוכנים ללכת?", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..minOf(3, maxOf(1, followed.size))).forEach { n ->
                    FilterChip(
                        selected = settings.maxStores == n,
                        onClick = { vm.setTripSettings(settings.copy(maxStores = n)) },
                        label = { Text(if (n == 1) "סופר אחד" else "עד $n (פיצול)") },
                    )
                }
            }
            Row(Modifier.clickable { vm.setTripSettings(settings.copy(includeMembers = !settings.includeMembers)) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = settings.includeMembers, onCheckedChange = { vm.setTripSettings(settings.copy(includeMembers = it)) })
                Text("לכלול מבצעי מועדון, כרטיס אשראי וקופונים", style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()

            val p = plan
            when {
                followed.size < 2 -> Text(
                    "כדי להשוות צריך לעקוב אחרי שני סניפים לפחות (בלשונית המחירים).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                p == null -> Text(
                    if (loading.isNotEmpty()) "טוען מחירים של הסניפים…" else "אין עדיין פריטים שאפשר להשוות בין הסניפים.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> PlanResult(p, all)
            }

            if (offers.isNotEmpty()) {
                HorizontalDivider()
                PromoOfferList(offers.take(12), all, title = "מבצעים לפריטים ברשימה")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.startTrip(p); onDismiss() }, modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            p != null && p.advice == TripAdvice.SPLIT -> "יוצאים: ${p.chosen.stops.size} רשימות"
                            p != null && p.advice == TripAdvice.SWITCH -> "יוצאים ל${branchName(p.chosen.branchIds.first(), all)}"
                            else -> "יוצאים לקנות"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = { vm.startTrip(null); onDismiss() }) { Text("בלי תכנון") }
            }
        }
    }
}

@Composable
private fun PlanResult(p: TripPlan, all: List<PriceBranch>) {
    val homeName = branchName(p.homeBranchId, all)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (p.advice == TripAdvice.STAY) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = NqShapes.Tile,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (p.advice) {
                TripAdvice.STAY -> {
                    Text("ההמלצה: $homeName, הסופר הרגיל שלך", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (p.biggestSaving >= 0.5) {
                        Text(
                            "ההפרש הכי גבוה הוא ${shekels(p.biggestSaving)} ב${optionText(p.biggest, all)}, " +
                                "פחות מ-${shekels(p.threshold)} שהגדרת" +
                                if (p.biggest.stops.size > 1) " (כל סופר נוסף צריך לחסוך את הסכום הזה)." else ".",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text("הוא הזול ביותר לרשימה הזאת.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                TripAdvice.SWITCH -> {
                    Text("ההמלצה: ${optionText(p.chosen, all)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("חוסך ${shekels(p.saving)} לעומת $homeName.", style = MaterialTheme.typography.bodyMedium)
                }
                TripAdvice.SPLIT -> {
                    Text(
                        "ההמלצה: פיצול ל-${p.chosen.stops.size} רשימות",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "אם תלכו ל${optionText(p.chosen, all)} תחסכו ${shekels(p.saving)} לעומת $homeName. האפליקציה מחלקת את הרשימה בעצמה:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    p.chosen.stops.forEach { stop ->
                        Text(
                            "• ${branchName(stop.branchId, all)}: ${stop.itemIds.size} פריטים · ${shekels(stop.total)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            // why: the items that are cheaper at the chosen stops
            if (p.reasons.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text("למה:", style = MaterialTheme.typography.labelLarge)
                p.reasons.take(5).forEach { r ->
                    Text(
                        "${r.itemName} זול יותר ב${branchName(r.branchId, all)} ב-${shekels(r.saving)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    // every option side by side
    Text("כמה תעלה הרשימה", style = MaterialTheme.typography.labelLarge)
    p.singles.forEach { o ->
        val diff = o.total - p.home.total
        PlanRow(
            optionText(o, all) + if (o.branchIds.first() == p.homeBranchId) " (הרגיל)" else "",
            shekels(o.total),
            when {
                o.branchIds.first() == p.homeBranchId -> ""
                diff < -0.005 -> "חוסך ${shekels(-diff)}"
                diff > 0.005 -> "יקר ב-${shekels(diff)}"
                else -> "אותו מחיר"
            },
            chosen = o == p.chosen,
        )
    }
    p.splits.forEach { o ->
        PlanRow(
            "פיצול: ${optionText(o, all)}",
            shekels(o.total),
            (p.home.total - o.total).takeIf { it > 0.005 }?.let { "חוסך ${shekels(it)}" } ?: "",
            chosen = o == p.chosen,
        )
    }
    val note = buildList {
        add("ההשוואה על ${p.compared} מתוך ${p.open} פריטים שיש להם מחיר בכל הסניפים, כולל מבצעים.")
        if (p.notCompared.isNotEmpty()) add("לא הושוו: ${p.notCompared.take(6).joinToString(", ")}${if (p.notCompared.size > 6) "…" else ""}.")
    }.joinToString(" ")
    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PlanRow(title: String, total: String, note: String, chosen: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(
            if (chosen) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent,
            NqShapes.Panel,
        ).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text((if (chosen) "✓ " else "") + title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Column(horizontalAlignment = Alignment.End) {
            Text(total, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "יש למוצרים אלו מבצעים ב…": the list's items with a promotion, grouped by branch. */
@Composable
fun PromoOfferList(offers: List<PromoOffer>, all: List<PriceBranch>, title: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.labelLarge)
        offers.groupBy { it.branchId }.forEach { (branchId, list) ->
            Text("ב${branchName(branchId, all)}:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            list.forEach { o -> PromoOfferRow(o) }
        }
    }
}

@Composable
private fun PromoOfferRow(o: PromoOffer) {
    val conditions = o.promotion.conditions
    Row(
        Modifier.fillMaxWidth().background(NqTheme.colors.promoBackground, NqShapes.Panel).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.LocalOffer, null, Modifier.size(16.dp), tint = NqTheme.colors.promoInk)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${o.itemName}: ${o.promotion.shortLabel}",
                color = NqTheme.colors.promoInk,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    o.product.name.takeIf { it.isNotBlank() },
                    "${shekels(o.promoUnit)} במקום ${shekels(o.regular)}",
                    conditions.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                ).joinToString(" · "),
                color = NqTheme.colors.promoInk.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** In the list while preparing: the list's items that have a promotion at the followed branches. Collapsible. */
@Composable
fun PromoOffersCard(offers: List<PromoOffer>, all: List<PriceBranch>) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.LocalOffer, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            val branches = offers.map { it.branchId }.distinct()
            Text(
                "יש למוצרים ברשימה מבצעים (${offers.map { it.itemId }.distinct().size})" +
                    if (branches.size == 1) " ב${branchName(branches.first(), all)}" else "",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "סגירה" else "פתיחה")
        }
        if (open) PromoOfferList(offers, all, title = null)
    }
}

/** While shopping a split trip: one chip per stop (its items), and "הכל". */
@Composable
fun TripStopsRow(stops: List<String>, active: String?, counts: Map<String, Int>, all: List<PriceBranch>, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(stops) { id ->
            FilterChip(
                selected = active == id,
                onClick = { onSelect(id) },
                label = { Text("${branchName(id, all)} · ${counts[id] ?: 0}", maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Storefront, null, Modifier.size(16.dp)) },
            )
        }
        item {
            FilterChip(selected = active == null, onClick = { onSelect(null) }, label = { Text("כל הרשימה") })
        }
    }
}
