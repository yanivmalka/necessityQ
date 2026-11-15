package app.necessityq.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.LineStatus
import app.necessityq.domain.ListLine
import app.necessityq.domain.Suggestion

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ListScreen(vm: AppViewModel, householdName: String, modifier: Modifier = Modifier) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ListLine?>(null) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text(householdName) })

        // Quick add: free text with quantity, e.g. "2 קוטג' 5%" / "חצי קילו גבינה צהובה"
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            placeholder = { Text("הוספה מהירה: 2 קוטג' 5%") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.submitQuery() }),
            trailingIcon = {
                IconButton(onClick = vm::submitQuery, enabled = query.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "הוסף")
                }
            },
        )
        if (completions.isNotEmpty()) {
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                completions.forEach { item ->
                    SuggestionChip(onClick = { vm.pickCompletion(item) }, label = { Text(item.displayName) })
                }
            }
        }

        val snap = snapshot
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (snap == null) {
                item { Text("טוען…", Modifier.padding(16.dp)) }
                return@LazyColumn
            }

            if (snap.runningLow.isNotEmpty()) {
                item { SectionTitle("כנראה נגמר בקרוב") }
                items(snap.runningLow, key = { "low_" + it.itemId }) { s ->
                    RunningLowCard(s, onAdd = { vm.accept(s) }, onDismiss = { vm.dismiss(s) }, Modifier.animateItem())
                }
            }

            if (snap.frequent.isNotEmpty()) {
                item(key = "frequent") {
                  Column {
                    SectionTitle("קונים בדרך כלל")
                    FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        snap.frequent.forEach { s ->
                            AssistChip(
                                onClick = { vm.accept(s) },
                                label = { Text(s.identity.displayName) },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                            )
                        }
                    }
                  }
                }
            }

            if (snap.grouped.isEmpty()) {
                item {
                    Text(
                        "הרשימה ריקה. הוסיפו מוצרים בשורה למעלה, או בהקשה על אחת ההצעות.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            snap.grouped.forEach { (category, lines) ->
                item(key = "cat_" + category.name) { SectionTitle(category.label) }
                items(lines, key = { it.id }) { line ->
                    LineRow(line, onToggle = { vm.checkOff(line) }, onClick = { editing = line }, Modifier.animateItem())
                }
            }

            if (snap.bought.isNotEmpty()) {
                item(key = "bought_header") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("נקנו (${snap.bought.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::clearBought) { Text("נקה") }
                    }
                }
                items(snap.bought, key = { it.id }) { line ->
                    LineRow(line, onToggle = { vm.reopen(line) }, onClick = {}, Modifier.animateItem())
                }
            }
        }
    }

    editing?.let { line ->
        // look up the live version so edits by others while the dialog is open are reflected
        val live = snapshot?.lines?.firstOrNull { it.id == line.id }
        if (live == null) {
            editing = null
        } else {
            EditLineDialog(
                line = live,
                onDismiss = { editing = null },
                onSave = { quantity, note ->
                    if (quantity != live.quantity) vm.changeQuantity(live, quantity)
                    if (note != live.note) vm.changeNote(live, note)
                    editing = null
                },
                onDelete = { vm.delete(live); editing = null },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun RunningLowCard(s: Suggestion, onAdd: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.identity.displayName + (s.quantity?.let { " · " + it.format() } ?: ""),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(s.reason, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "לא עכשיו") }
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "הוסף לרשימה") }
        }
    }
}

@Composable
private fun LineRow(line: ListLine, onToggle: () -> Unit, onClick: () -> Unit, modifier: Modifier) {
    val bought = line.status == LineStatus.BOUGHT
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = bought, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    line.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (bought) TextDecoration.LineThrough else null,
                    color = if (bought) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                line.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            line.quantity?.let {
                Text(it.format(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(8.dp))
            }
        }
        HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 0.5.dp)
    }
}
