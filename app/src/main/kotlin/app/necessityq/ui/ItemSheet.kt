package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.ItemMessage
import app.necessityq.domain.LineStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val timeFormat = DateTimeFormatter.ofPattern("d.M HH:mm")
private fun fmt(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat)

/**
 * Expanded information about one product plus its conversation. Works for an item that is on the list
 * now (with quick actions) and for one that is not (opened from the messages tab).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ItemSheet(vm: AppViewModel, itemId: String, itemName: String, onDismiss: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val allMessages by vm.itemMessages.collectAsStateWithLifecycle()
    val names by vm.memberNames.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf(false) }

    val line = snapshot?.lines?.firstOrNull { it.itemId == itemId && it.status == LineStatus.NEEDED }
    val stats = snapshot?.stats?.get(itemId)
    val thread = allMessages.filter { it.itemId == itemId }
    val favorite = itemId in favorites
    val lastStore = purchases.firstOrNull { it.itemId == itemId && it.storeId != null }
        ?.storeId?.let { id -> stores.firstOrNull { it.id == id }?.name }

    // reading the sheet marks the conversation as read, also for messages that arrive while it is open
    LaunchedEffect(itemId, thread.size) { vm.markRead(itemId) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (favorite) Icon(Icons.Default.Star, "מועדף", tint = Color(0xFFC77700))
                Text(line?.displayName ?: itemName, style = ProductNameStyle, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                // ⋮ actions — on the left edge in Hebrew layout
                androidx.compose.foundation.layout.Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "פעולות") }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (favorite) "הסרה מהמועדפים" else "הוספה למועדפים") },
                            leadingIcon = { Icon(if (favorite) Icons.Default.StarBorder else Icons.Default.Star, null) },
                            onClick = { vm.toggleFavorite(itemId); menu = false },
                        )
                        if (line != null) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (line.missing) "ביטול סימון חסר" else "סימון כחסר") },
                                leadingIcon = { Icon(Icons.Default.ReportProblem, null) },
                                onClick = { vm.setMissing(line, !line.missing); menu = false },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("עריכת כמות והערה") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { editing = true; menu = false },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("הסרה מהרשימה") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { vm.delete(line); menu = false; onDismiss() },
                            )
                        }
                    }
                }
            }

            if (line != null) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.necessityq.domain.ItemTags.of(line, stats, favorite).forEach { TagBadge(it) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuantityStepper(line.quantity, onMinus = { vm.adjustQuantity(line, -1) }, onPlus = { vm.adjustQuantity(line, +1) })
                    PriceSlot(price = null, average = null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.checkOff(line); onDismiss() }) { Text("נקנה ✓") }
                    OutlinedButton(onClick = { vm.delete(line); onDismiss() }) {
                        Icon(Icons.Default.Delete, null, Modifier.padding(end = 4.dp))
                        Text("הסרה")
                    }
                }
                if (line.missing) {
                    Text(
                        "מסומן כחסר — בני הבית שנבחרו לקבל התראות יקבלו הודעה (בתפריט ← הבית ומשתתפים).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Button(onClick = { vm.addByName(itemName) }) { Text("הוספה לרשימה") }
            }

            HorizontalDivider()
            InfoLine("קטגוריה", (line?.category ?: stats?.identity?.category)?.label)
            line?.let { InfoLine("נוסף", "${names[it.addedBy] ?: "בן/בת בית"} · ${fmt(it.addedAtMillis)}") }
            line?.note?.let { InfoLine("הערה", it) }
            stats?.let { s ->
                val days = ChronoUnit.DAYS.between(s.lastPurchase, LocalDate.now())
                InfoLine("נקנה לאחרונה", if (days <= 0) "היום" else "לפני $days ימים")
                InfoLine("תדירות", s.cadenceDays?.let { "בערך כל ${it.roundToInt().coerceAtLeast(1)} ימים" } ?: "אין עדיין מספיק היסטוריה")
                InfoLine("כמות רגילה", s.typicalQuantity?.format())
                InfoLine("נקנה", "${s.purchaseDays} פעמים")
            }
            InfoLine("נקנה לאחרונה ב", lastStore)
            InfoLine("מחיר", "יוצג כשיחובר מאגר מחירים עדכני")

            HorizontalDivider()
            Text("שיחה על המוצר", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            vm.receipts.collectAsStateWithLifecycle()
            ChatThread(thread, me = vm.me, status = vm::deliveryStatus)
            var draft by remember(itemId) { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    draft, { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("למשל: לקנות את הגדול?") },
                    maxLines = 4,
                )
                IconButton(
                    enabled = draft.isNotBlank(),
                    onClick = { vm.sendMessage(itemId, line?.displayName ?: itemName, draft); draft = "" },
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "שליחה") }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    if (editing && line != null) {
        EditLineDialog(
            line = line,
            onDismiss = { editing = false },
            onSave = { quantity, note ->
                if (quantity != line.quantity) vm.changeQuantity(line, quantity)
                if (note != line.note) vm.changeNote(line, note)
                editing = false
            },
            onDelete = { vm.delete(line); editing = false; onDismiss() },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text("$label: ", fontWeight = FontWeight.Bold)
        Text(value)
    }
}

@Composable
private fun ChatThread(thread: List<ItemMessage>, me: String, status: (ItemMessage) -> app.necessityq.domain.DeliveryStatus) {
    if (thread.isEmpty()) {
        Text("אין עדיין הודעות. כתבו כאן שאלה או פרט על המוצר, וכל בני הבית יראו.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        thread.forEach { m ->
            val mine = m.authorId == me
            Column(
                Modifier.fillMaxWidth(0.85f)
                    .align(if (mine) Alignment.Start else Alignment.End)
                    .background(
                        if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("${if (mine) "אני" else m.authorName} · ${fmt(m.atMillis)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(m.text)
                if (mine) androidx.compose.foundation.layout.Box(Modifier.align(Alignment.End)) { DeliveryTicks(status(m)) }
            }
        }
    }
}

// ---------- messages hub ----------

/** All product conversations in one place, newest first, with unread counts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(vm: AppViewModel, modifier: Modifier = Modifier, onOpen: (itemId: String, itemName: String) -> Unit) {
    val all by vm.itemMessages.collectAsStateWithLifecycle()
    vm.unreadTotal.collectAsStateWithLifecycle() // recompose when read markers change
    val threads = all.groupBy { it.itemId }.values
        .map { it.maxBy { m -> m.atMillis } to it }
        .sortedByDescending { it.first.atMillis }

    Column(modifier.fillMaxSize()) {
        TopAppBar(navigationIcon = { DrawerButton() }, title = { Text("הודעות על מוצרים", fontWeight = FontWeight.ExtraBold) })
        if (threads.isEmpty()) {
            Text(
                "כאן יופיעו כל השיחות על מוצרים. פותחים שיחה בהקשה על מוצר ברשימה.",
                Modifier.padding(16.dp),
            )
            return
        }
        LazyColumn {
            items(threads, key = { it.first.itemId }) { (last, _) ->
                val unread = vm.unreadCount(last.itemId, all)
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(last.itemId, last.itemName) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(last.itemName, style = ProductNameStyle)
                        Text(
                            "${if (last.authorId == vm.me) "אני" else last.authorName}: ${last.text}",
                            maxLines = 2,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(fmt(last.atMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (unread > 0) Badge { Text("$unread") } else if (last.authorId == vm.me) DeliveryTicks(vm.deliveryStatus(last))
                }
                HorizontalDivider()
            }
        }
    }
}
