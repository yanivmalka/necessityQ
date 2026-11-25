package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import app.necessityq.ui.icons.Icons
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
import app.necessityq.domain.Promotion
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
    val basketOffers by vm.basketOffers.collectAsStateWithLifecycle()
    val followed by vm.selectedPriceBranches.collectAsStateWithLifecycle()
    val all by vm.priceBranches.collectAsStateWithLifecycle()
    val loading by vm.pricesLoading.collectAsStateWithLifecycle()
    var thresholdText by remember { mutableStateOf(settings.threshold.toString()) }
    var homeMenu by remember { mutableStateOf(false) }
    val home = vm.homeBranchId(settings)

    var settingsOpen by remember { mutableStateOf(false) }
    val p = plan

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("איפה הכי משתלם לקנות?", style = MaterialTheme.typography.titleLarge)

            // the recommendation first, with one button
            when {
                followed.size < 2 -> Text(
                    "כדי להשוות צריך לעקוב אחרי שני סניפים לפחות (בלשונית המחירים).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                p == null -> Text(
                    if (loading.isNotEmpty()) "טוען מחירים של הסניפים…" else "אין עדיין פריטים שאפשר להשוות בין הסניפים.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> PlanCard(p, all)
            }
            Button(onClick = { vm.startTrip(p); onDismiss() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = NqShapes.Field) {
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

            // the settings behind the recommendation, folded
            Row(
                Modifier.fillMaxWidth().clip(NqShapes.Panel).clickable { settingsOpen = !settingsOpen }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("שינוי הגדרות", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (settingsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (settingsOpen) "סגירה" else "פתיחה")
            }
            if (settingsOpen) {
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
                OutlinedButton(onClick = { vm.startTrip(null); onDismiss() }) { Text("יציאה בלי תכנון") }
            }

            if (p != null) {
                HorizontalDivider()
                PlanDetails(p, all)
            }

            if (offers.isNotEmpty() || basketOffers.isNotEmpty()) {
                HorizontalDivider()
                PromoOfferList(offers.take(12), basketOffers, all, title = "מבצעים לרשימה")
            }
        }
    }
}

/** The recommendation: the regular supermarket, another one, or a split — and why. */
@Composable
private fun PlanCard(p: TripPlan, all: List<PriceBranch>) {
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
            // whole-basket promotions the chosen stops reach
            p.chosen.stops.filter { it.basketPromo != null }.forEach { stop ->
                Text(
                    "כולל הנחה על הסל ב${branchName(stop.branchId, all)}: ${shekels(stop.basketDiscount)} (${stop.basketPromo!!.shortLabel})",
                    style = MaterialTheme.typography.bodySmall,
                )
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
}

/** Every option side by side, and what was compared. */
@Composable
private fun PlanDetails(p: TripPlan, all: List<PriceBranch>) {
    // every option side by side
    Text("כמה תעלה הרשימה", style = MaterialTheme.typography.labelLarge)
    p.singles.forEach { o ->
        val diff = o.total - p.home.total
        val basket = o.stops.sumOf { it.basketDiscount }
        PlanRow(
            optionText(o, all) + if (o.branchIds.first() == p.homeBranchId) " (הרגיל)" else "",
            shekels(o.total),
            listOfNotNull(
                when {
                    o.branchIds.first() == p.homeBranchId -> null
                    diff < -0.005 -> "חוסך ${shekels(-diff)}"
                    diff > 0.005 -> "יקר ב-${shekels(diff)}"
                    else -> "אותו מחיר"
                },
                if (basket > 0.005) "כולל ${shekels(basket)} הנחה על הסל" else null,
            ).joinToString(" · "),
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
        add("ההשוואה על ${p.compared} מתוך ${p.open} פריטים שיש להם מחיר בכל הסניפים, כולל מבצעים (בלי מבצעים \"עד גמר המלאי\", שאי אפשר לסמוך עליהם).")
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

/**
 * "יש למוצרים אלו מבצעים ב…": the list's items with a promotion, and the promotions on the whole basket, grouped by
 * branch.
 */
@Composable
fun PromoOfferList(offers: List<PromoOffer>, basket: List<Pair<String, Promotion>>, all: List<PriceBranch>, title: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.labelLarge)
        val byBranch = offers.groupBy { it.branchId }
        val basketByBranch = basket.groupBy({ it.first }, { it.second })
        (byBranch.keys + basketByBranch.keys).distinct().forEach { branchId ->
            Text("ב${branchName(branchId, all)}:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            basketByBranch[branchId].orEmpty().forEach { BasketPromoRow(it) }
            byBranch[branchId].orEmpty().forEach { o -> PromoOfferRow(o) }
        }
    }
}

/** A promotion on the whole basket: "על כל הסל: 10 ₪ הנחה · בקנייה מעל 300 ₪ · בקופון". */
@Composable
private fun BasketPromoRow(p: Promotion) {
    PromoRowFrame(
        title = "על כל הסל: ${p.shortLabel}",
        details = p.conditions.joinToString(" · "),
        warning = p.stockWarning,
    )
}

@Composable
private fun PromoRowFrame(title: String, details: String, warning: String?) {
    Row(
        Modifier.fillMaxWidth().background(NqTheme.colors.promoBackground, NqShapes.Panel).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.LocalOffer, null, Modifier.size(16.dp), tint = NqTheme.colors.promoInk)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = NqTheme.colors.promoInk, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (details.isNotBlank()) {
                Text(
                    details,
                    color = NqTheme.colors.promoInk.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // "עד גמר המלאי": shown, but it may be gone by the time one gets there
            if (warning != null) {
                Text("⚠ $warning", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PromoOfferRow(o: PromoOffer) {
    val conditions = o.promotion.conditions
    PromoRowFrame(
        title = "${o.itemName}: ${o.promotion.shortLabel}",
        details = listOfNotNull(
            o.product.name.takeIf { it.isNotBlank() },
            "${shekels(o.promoUnit)} במקום ${shekels(o.regular)}",
            conditions.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        ).joinToString(" · "),
        warning = o.promotion.stockWarning,
    )
}

/** In the list while preparing: the list's items that have a promotion, and basket promotions, at the followed branches. */
@Composable
fun PromoOffersCard(offers: List<PromoOffer>, basket: List<Pair<String, Promotion>>, all: List<PriceBranch>) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.LocalOffer, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            val branches = (offers.map { it.branchId } + basket.map { it.first }).distinct()
            val items = offers.map { it.itemId }.distinct().size
            Text(
                (if (items > 0) "יש למוצרים ברשימה מבצעים ($items)" else "מבצעים על כל הסל (${basket.size})") +
                    if (branches.size == 1) " ב${branchName(branches.first(), all)}" else "",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "סגירה" else "פתיחה")
        }
        if (open) PromoOfferList(offers, basket, all, title = null)
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
