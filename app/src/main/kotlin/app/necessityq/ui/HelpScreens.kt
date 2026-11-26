package app.necessityq.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.necessityq.domain.HelpCatalog
import app.necessityq.domain.HelpEntry
import app.necessityq.domain.HelpProblem
import app.necessityq.domain.HelpTarget
import app.necessityq.ui.icons.Icons
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.FeatureAdvisor

/**
 * "עזרה: איך משתמשים": the seven common troubles, each opening into its steps, and a search over everything the app
 * does ([HelpCatalog]) — what it is, what it gives and costs, how to turn it on, with a button that opens the place.
 * Written in words, with no arrows and no running numbers inside Hebrew lines (they jumble when drawn right to left).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(modifier: Modifier = Modifier, onOpen: (HelpTarget) -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var openProblem by rememberSaveable { mutableStateOf<String?>(null) }
    val asking = query.isNotBlank()
    val entries = remember(query) { HelpCatalog.search(query) }
    val problems = remember(query) { HelpCatalog.searchProblems(query) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { BackNavButton() },
            title = { Text("עזרה: איך משתמשים", fontWeight = FontWeight.ExtraBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        OutlinedTextField(
            query, { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text("חיפוש הסבר") },
            placeholder = { Text("למשל: מבצעים, התראות, איך מדליקים צ'אט פוד") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (asking) {
                { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "מחיקת הטקסט") } }
            } else null,
        )
        Column(
            Modifier.fadingVerticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!asking) {
                Text(
                    "שבע בעיות נפוצות",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else if (problems.isNotEmpty()) {
                Text("בעיות נפוצות שקשורות", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            problems.forEach { problem ->
                ProblemCard(problem, expanded = openProblem == problem.id || asking, onToggle = {
                    openProblem = if (openProblem == problem.id) null else problem.id
                }, onOpen = onOpen)
            }
            if (asking) {
                if (entries.isNotEmpty()) {
                    Text("הסברים", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                entries.forEach { EntryCard(it, onOpen) }
                if (entries.isEmpty() && problems.isEmpty()) {
                    Text(
                        "לא מצאנו הסבר על \"${query.trim()}\". נסו מילה אחרת, שם של יכולת (מבצעים, מחירים, צ'אט פוד) או של הגדרה (החלקה, התראות).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "לא מצאתם? כתבו בשורת החיפוש מה אתם מחפשים, או איך עושים משהו, ונסביר מה זה, מה התועלת והחיסרון, ואיך מדליקים. " +
                        "בכל הסבר יש כפתור שפותח את המקום המתאים בהגדרות.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HelpCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(NqShapes.Tile).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, NqTheme.colors.hairline, NqShapes.Tile),
    ) { content() }
}

/** A common trouble: the title (tap to open), why it happens, the numbered steps, and a button to the place. */
@Composable
private fun ProblemCard(problem: HelpProblem, expanded: Boolean, onToggle: () -> Unit, onOpen: (HelpTarget) -> Unit) {
    HelpCard {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(problem.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "סגירה" else "פתיחה",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(problem.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                problem.steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        // the number sits in its own circle: not a part of the Hebrew line, so nothing jumps around it
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (index + 1).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
                problem.target?.let { OpenButton(it, onOpen) }
            }
        }
    }
}

/** What a thing is, what it gives, what it costs, how to do it — and a button to where it is. */
@Composable
private fun EntryCard(entry: HelpEntry, onOpen: (HelpTarget) -> Unit) {
    HelpCard(Modifier.padding(0.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HelpLine("מה זה", entry.what)
            entry.benefit?.let { HelpLine("מה זה נותן", it) }
            entry.drawback?.let { HelpLine("מה החיסרון", it) }
            HelpLine("איך עושים", entry.howTo)
            entry.target?.let { OpenButton(it, onOpen) }
        }
    }
}

@Composable
private fun HelpLine(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OpenButton(target: HelpTarget, onOpen: (HelpTarget) -> Unit) {
    OutlinedButton(onClick = { onOpen(target) }) { Text(target.label) }
}

/**
 * "הצעות שדחיתם": the suggestions answered "לא תודה". Each can still be taken ("לבצע עכשיו"), or put back among
 * the ones the app may offer.
 */
@Composable
fun DeclinedSuggestionsScreen(vm: AppViewModel, modifier: Modifier = Modifier, onOpenPrices: () -> Unit) =
    PageScaffold("הצעות שדחיתם", modifier) {
        val declined by vm.declinedAdvice.collectAsStateWithLifecycle()
        if (declined.isEmpty()) {
            Text("אין הצעות שדחיתם.", style = MaterialTheme.typography.bodyMedium)
        }
        declined.sorted().forEach { id ->
            Column(
                Modifier.fillMaxWidth().clip(NqShapes.Tile).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, NqTheme.colors.hairline, NqShapes.Tile).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(FeatureAdvisor.describe(id), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.restoreAdvice(id); vm.applyAdvice(id, onOpenPrices) }) { Text("לבצע עכשיו") }
                    TextButton(onClick = { vm.restoreAdvice(id) }) { Text("להחזיר להצעות") }
                }
            }
        }
    }
