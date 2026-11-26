package app.necessityq.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import app.necessityq.ui.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.Account
import app.necessityq.domain.Classification
import app.necessityq.domain.Household
import app.necessityq.domain.ListLine
import app.necessityq.domain.Quantity
import app.necessityq.domain.QuantityUnit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ---------- Onboarding ----------

/**
 * The welcome page of a new user: what the app is, in three lines, then a new household or joining one. With [demo]
 * (the replay of the first run, "הדגמת התחלה"), nothing is created or joined: both buttons go on to the questions.
 */
@Composable
fun OnboardingScreen(vm: AppViewModel, account: Account, modifier: Modifier = Modifier, demo: Boolean = false) {
    var name by remember { mutableStateOf("הבית שלנו") }
    var code by remember { mutableStateOf("") }
    Column(
        modifier.fillMaxSize().fadingVerticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (demo) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("הדגמת התחלה", fontWeight = FontWeight.Bold)
                    Text(
                        "כך רואה את האפליקציה משתמש חדש. שום בית לא ייווצר ושום דבר לא יימחק: הרשימה, הבית וההיסטוריה שלך נשמרים. " +
                            "בסוף השאלות ייקבעו היכולות לפי התשובות.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { vm.endFirstRunReplay(restore = true) }) { Text("יציאה מההדגמה") }
                }
            }
        }
        Text("necessityQ", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text("רשימת הקניות של הבית, שלומדת מה אתם צורכים", style = MaterialTheme.typography.titleMedium)
        listOf(
            "כל בני הבית רואים ומעדכנים את אותה רשימה, בזמן אמת.",
            "האפליקציה לומדת כל כמה זמן אתם קונים כל מוצר, ומזכירה לפני שהוא נגמר.",
            "אם תרצו: מחירים בסופר שלכם, מבצעים ותפריט שבועי.",
        ).forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("בית חדש", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text("שם הבית") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { if (demo) vm.replayOnboardingDone() else vm.createHousehold(name) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("יצירת בית") }

        Spacer(Modifier.height(12.dp))
        Text("הצטרפות לבית קיים", style = MaterialTheme.typography.titleMedium)
        if (vm.isShared) {
            Text(
                "מישהו מהבית שלח לכם קישור? פתחו אותו והצטרפות תהיה בנגיעה. אפשר גם להקליד את הקוד שבהודעה.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                code, { code = it.uppercase().take(6) },
                label = { Text("קוד הצטרפות (6 תווים)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { if (demo) vm.replayOnboardingDone() else vm.joinHousehold(code) },
                enabled = demo || code.length == 6,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("הצטרפות") }
        } else {
            Text(
                "גרסה זו נבנתה ללא שרת סנכרון, ולכן הרשימה נשמרת במכשיר זה בלבד.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (vm.supportsAccounts && !demo) {
            Spacer(Modifier.height(12.dp))
            Text(
                "כבר הייתה לכם רשימה בטלפון אחר? התחברו עם אותו חשבון Google כדי לחזור אליה.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AccountCard(vm, account)
        }
    }
}

// ---------- Edit line ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLineDialog(
    line: ListLine,
    onDismiss: () -> Unit,
    /** name as the user wrote it, quantity, note */
    onSave: (String, Quantity?, String?) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(line.id) { mutableStateOf(line.displayName) }
    var amount by remember(line.id) { mutableStateOf(line.quantity?.amount?.let(Quantity::formatNumber) ?: "") }
    var unit by remember(line.id) { mutableStateOf(line.quantity?.unit ?: QuantityUnit.PIECE) }
    var note by remember(line.id) { mutableStateOf(line.note ?: "") }
    var unitMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text("שם המוצר") },
                    supportingText = { Text("המוצר שנבחר לו (בסריקה או מהרשימה) והמחיר נשמרים") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        amount, { amount = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("כמות") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    ExposedDropdownMenuBox(unitMenu, { unitMenu = it }, Modifier.weight(1f)) {
                        OutlinedTextField(
                            unit.label, {}, readOnly = true, label = { Text("יחידה") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitMenu) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(unitMenu, { unitMenu = false }) {
                            QuantityUnit.entries.forEach { u ->
                                DropdownMenuItem(text = { Text(u.label) }, onClick = { unit = u; unitMenu = false })
                            }
                        }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("הערה (למשל: פרוסה דק)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { Quantity(it, unit) }
                onSave(name.trim().ifBlank { line.displayName }, q, note.trim().ifBlank { null })
            }) { Text("שמירה") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("מחיקה", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("ביטול") }
            }
        },
    )
}

// ---------- History & statistics ----------

private val dateFormat = DateTimeFormatter.ofPattern("dd/MM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val stats = snapshot?.stats?.values.orEmpty()
        .sortedWith(compareBy({ it.classification.ordinal }, { -it.frequencyScore }))
    val zone = ZoneId.systemDefault()

    val removed = snapshot?.removed.orEmpty()
    var showRemoved by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(navigationIcon = { BackNavButton() }, title = { Text("היסטוריה") })
        TabRow(selectedTabIndex = if (showRemoved) 1 else 0) {
            Tab(selected = !showRemoved, onClick = { showRemoved = false }, text = { Text("נקנו") })
            Tab(
                selected = showRemoved, onClick = { showRemoved = true },
                text = { Text("הוסרו" + if (removed.isNotEmpty()) " (${removed.size})" else "") },
            )
        }
        val historyList = androidx.compose.foundation.lazy.rememberLazyListState()
        if (showRemoved) {
            LazyColumn(Modifier.fillMaxSize().fadingEdges(historyList, horizontal = false), state = historyList) {
                item {
                    Text(
                        "פריטים שנמחקו או נוקו מהרשימה. פריט שנמחק לא נספר בסטטיסטיקה.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (removed.isEmpty()) item { Text("אין פריטים שהוסרו.", Modifier.padding(horizontal = 16.dp)) }
                items(removed, key = { "r_" + it.id }) { line ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    line.removedReason?.label,
                                    line.removedAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("d.M HH:mm")) },
                                    line.quantity?.format(),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { vm.restoreToList(line) }) { Text("לרשימה") }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().fadingEdges(historyList, horizontal = false), state = historyList) {
            if (stats.isEmpty()) {
                item {
                    Text(
                        "עוד אין נתונים. כל פריט שמסומן כ\"נקנה\" נרשם, ואחרי כמה קניות האפליקציה לומדת את הקצב.",
                        Modifier.padding(16.dp),
                    )
                }
            }
            items(stats, key = { it.itemId }) { s ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.identity.displayName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(
                                when (s.classification) {
                                    Classification.ROUTINE -> "קבוע"
                                    Classification.OCCASIONAL -> "מזדמן"
                                    Classification.ONE_OFF -> "חד-פעמי"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        val parts = buildList {
                            add("נקנה ${s.purchaseDays} פעמים")
                            s.cadenceDays?.let { add("כל ~${it.roundToInt()} ימים") }
                            s.typicalQuantity?.let { add("בדרך כלל ${it.format()}") }
                            add("לאחרונה ${s.lastPurchase.format(dateFormat)}")
                            if (s.lastPurchaseEstimated) add("כנראה ${s.estimatedLastPurchase.format(dateFormat)} (לא תועד)")
                            s.nextDue?.let { add("צפוי ${it.format(dateFormat)}") }
                        }
                        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (purchases.isNotEmpty()) {
                item { Text("קניות אחרונות", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp)) }
                items(purchases.take(100), key = { "p_" + it.id }) { p ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val date = Instant.ofEpochMilli(p.boughtAtMillis).atZone(zone).toLocalDate().format(dateFormat)
                        Text("$date  ${p.identity.displayName}", modifier = Modifier.weight(1f))
                        p.quantity?.let { Text(it.format(), style = MaterialTheme.typography.bodySmall) }
                        // lets the user remove a mistaken check-off so it does not skew the statistics
                        IconButton(onClick = { vm.deletePurchase(p) }) { Icon(Icons.Default.Delete, contentDescription = "מחק רישום") }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}
