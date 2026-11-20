package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.LineStatus
import app.necessityq.domain.PriceData
import app.necessityq.domain.ScanResult
import app.necessityq.platform.BarcodeScanner
import kotlinx.coroutines.launch

/**
 * Opens the camera barcode scanner and hands the code to [onCode] (not called when the user backs
 * out). Errors — no Play services, scanner module still downloading — become a message.
 */
@Composable
fun rememberBarcodeScan(vm: AppViewModel, onCode: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onCode)
    return remember(context) {
        {
            scope.launch {
                runCatching { BarcodeScanner.scan(context) }
                    .onSuccess { code -> code?.let { latest(it) } }
                    .onFailure {
                        vm.showMessage(
                            "הסורק לא זמין כרגע" + (it.message?.let { m -> ": $m" } ?: "") +
                                ". בהפעלה הראשונה רכיב הסריקה של Google עשוי לרדת ברקע — נסו שוב בעוד דקה.",
                        )
                    }
            }
            Unit
        }
    }
}

/** Result of a scan: price at the branch you are in, other branches, and the list line it belongs to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(vm: AppViewModel) {
    val result by vm.scanResult.collectAsStateWithLifecycle()
    val busy by vm.scanBusy.collectAsStateWithLifecycle()
    if (busy && result == null) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.QrCodeScanner, null) },
            title = { Text("מחפש את המוצר…") },
            text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } },
            confirmButton = {},
        )
    }
    val r = result ?: return
    val scanAgain = rememberBarcodeScan(vm) { vm.onScanned(it) }
    ModalBottomSheet(onDismissRequest = vm::closeScan, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScanContent(vm, r)
            OutlinedButton(onClick = scanAgain, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.QrCodeScanner, null)
                Text("  סריקה נוספת")
            }
        }
    }
}

@Composable
private fun ScanContent(vm: AppViewModel, r: ScanResult) {
    val branches by vm.priceBranches.collectAsStateWithLifecycle()
    val selected by vm.selectedPriceBranches.collectAsStateWithLifecycle()
    val here by vm.hereBranch.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    fun title(id: String?) = branches.firstOrNull { it.id == id }?.title ?: "סניף"
    val atHere = here?.branch?.id != null && here?.branch?.id == r.branchId

    Text("ברקוד ${r.code}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

    // where we are, by coordinates
    here?.let { nb ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
            Text(" אתם ב${nb.branch.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        if (nb.branch.id !in selected) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("הסניף הזה עוד לא במעקב, ולכן אין לו מחירים. במעקב המחירים שלו נטענים תוך כמה דקות ומתעדכנים כל יום.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = vm::followHereBranch) { Text("מעקב אחרי הסניף") }
                }
            }
        }
    }

    val p = r.product
    if (p == null) {
        Text("המוצר לא נמצא", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            if (selected.isEmpty()) "עוד לא נבחרו סניפים במסך \"סניפים ומחירים\", ולכן אין מחירים לחפש בהם."
            else "הברקוד לא מופיע בקבצי המחירים של הסניפים שאתם עוקבים אחריהם.",
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val details = listOfNotNull(
            p.brand.takeIf { it.isNotBlank() },
            p.packageSize?.let { q -> "${if (q == Math.floor(q)) q.toLong().toString() else q.toString()} ${p.unit}".trim() }?.takeIf { !p.soldByWeight },
            if (p.soldByWeight) "במשקל" else null,
        )
        if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)

        val hereProduct = r.here
        if (hereProduct != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (atHere) "המחיר כאן" else "ב${title(r.branchId)}", style = MaterialTheme.typography.bodyMedium)
                    if (hereProduct.high > hereProduct.low) {
                        Text("טווח בסניף: ${PriceData.formatRange(hereProduct.low, hereProduct.high)}", style = MaterialTheme.typography.bodySmall)
                    }
                    hereProduct.unitPrice?.let { Text("ליחידת מידה: ${PriceData.formatPrice(it)}", style = MaterialTheme.typography.bodySmall) }
                }
                Text(PriceData.formatPrice(hereProduct.price), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
        } else if (r.branchId != null) {
            Text("לא נמצא ב${title(r.branchId)}", style = MaterialTheme.typography.bodyMedium)
        }
        r.verdict?.let { VerdictCard(it) }
        if (r.elsewhere.isNotEmpty()) {
            HorizontalDivider()
            Text("בסניפים האחרים שלך", style = MaterialTheme.typography.titleSmall)
            // in the unit of the product shown (per 100 g or per kg alike); a branch whose unit can't be matched shows its own
            val inUnit = r.prices
            r.elsewhere.forEach { hit ->
                val price = inUnit[hit.branchId]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title(hit.branchId), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (price == null) Text("ביחידה אחרת  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(PriceData.formatPrice(price ?: hit.product.price), fontWeight = FontWeight.Bold)
                }
            }
            r.range?.takeIf { it.high - it.low >= 0.005 && hereProduct != null && hereProduct.price > it.low }?.let { range ->
                Text(
                    "זול יותר ב${PriceData.formatPrice(hereProduct!!.price - range.low)} ב${range.cheapestBranchIds.joinToString(", ") { title(it) }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    // the list
    HorizontalDivider()
    val line = r.line
    when {
        line != null && line.status == LineStatus.NEEDED -> {
            Text("ברשימה: ${line.displayName}", style = MaterialTheme.typography.titleMedium)
            if (mode == app.necessityq.domain.ListMode.SHOP) {
                Button(onClick = { vm.scanCheckOff(r) }, modifier = Modifier.fillMaxWidth()) { Text("סימון כנקנה") }
            }
        }
        line != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Text("  ${line.displayName} כבר סומן כנקנה", style = MaterialTheme.typography.titleMedium)
        }
        else -> {
            var text by remember(r.code) { mutableStateOf(r.identity?.displayName.orEmpty()) }
            var rememberProduct by remember(r.code) { mutableStateOf(false) }
            Text("לא ברשימה", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(text, { text = it }, label = { Text("שם כללי לרשימה") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (p != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rememberProduct, { rememberProduct = it })
                    Text("לזכור שזה המוצר שאנחנו קונים (מחיר מדויק במקום טווח)", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.scanAdd(r, text, rememberProduct) }, enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text("הוספה לרשימה")
                }
                OutlinedButton(onClick = { vm.remindLater(r) }, modifier = Modifier.weight(1f)) { Text("תזכיר לי אחר כך") }
            }
        }
    }
    if (line != null && p != null && r.identity != null) {
        if (r.alreadyPreferred) {
            Text("זה המוצר שבחרתם ל${r.identity!!.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = { vm.scanRemember(r) }) { Text("לזכור את המוצר הזה ל${r.identity!!.displayName}") }
        }
    }
}

/**
 * Is the price reasonable here: a filled verdict pill, a three-step scale (זול · רגיל · יקר) with the
 * verdict's step lit, then the reasons (the household's branches, its own recent range, similar products).
 */
@Composable
private fun VerdictCard(v: app.necessityq.domain.PriceVerdict) {
    val levels = app.necessityq.domain.PriceLevel.entries
    val tone = when (v.level) {
        app.necessityq.domain.PriceLevel.CHEAP -> MaterialTheme.colorScheme.primary
        app.necessityq.domain.PriceLevel.FAIR -> MaterialTheme.colorScheme.onSurfaceVariant
        app.necessityq.domain.PriceLevel.EXPENSIVE -> MaterialTheme.colorScheme.secondary
    }
    val onTone = when (v.level) {
        app.necessityq.domain.PriceLevel.CHEAP -> MaterialTheme.colorScheme.onPrimary
        app.necessityq.domain.PriceLevel.FAIR -> MaterialTheme.colorScheme.surface
        app.necessityq.domain.PriceLevel.EXPENSIVE -> MaterialTheme.colorScheme.onSecondary
    }
    Column(
        Modifier.fillMaxWidth().clip(NqShapes.Tile).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, NqTheme.colors.hairline, NqShapes.Tile).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.clip(NqShapes.Pill).background(tone).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (v.level == app.necessityq.domain.PriceLevel.CHEAP) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = onTone, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(v.level.label, color = onTone, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
        // RTL: cheap on the right, expensive on the left
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            levels.forEach { level ->
                Box(
                    Modifier.weight(1f).height(8.dp).clip(NqShapes.Pill)
                        .background(if (level == v.level) tone else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("זול", "רגיל", "יקר").forEach { label ->
                Text(
                    label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        v.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
}
