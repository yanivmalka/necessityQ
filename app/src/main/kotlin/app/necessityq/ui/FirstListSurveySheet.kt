package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.necessityq.domain.AgeGroup
import app.necessityq.domain.FirstListSurvey
import app.necessityq.domain.HouseholdBaseline
import app.necessityq.domain.HouseholdMakeup
import app.necessityq.domain.SurveyEstimate
import app.necessityq.domain.SurveyItem
import app.necessityq.domain.SurveyPeriod
import app.necessityq.ui.icons.Icons
import kotlinx.coroutines.delay

/**
 * The first-list survey, shown in the list-building sheet the first time a list is built ([FirstListSurvey]): how
 * much the household buys of the basic products. The amounts are proposed (by the model from the ages at home, or
 * the household's own purchases, or a general table) and the household corrects them. It is filled once; the
 * answers size the first list and every item the household has not bought yet.
 *
 * [saved]: earlier answers, when the survey is opened again. [onSave] gets the answers, [onSkip] a skipped survey.
 */
@Composable
internal fun FirstListSurveyContent(
    vm: AppViewModel,
    saved: HouseholdBaseline?,
    onSave: (HouseholdBaseline) -> Unit,
    onSkip: (HouseholdBaseline) -> Unit,
) {
    val profiles = remember { vm.meals.value.members.isNotEmpty() }
    // with no family profiles, the survey asks who lives at home, by age group
    var groups by remember {
        mutableStateOf(
            saved?.makeup?.takeIf { !profiles }?.ages?.groupingBy(::groupOf)?.eachCount()
                ?: mapOf(AgeGroup.ADULTS to 2),
        )
    }
    val makeup = if (profiles) remember { vm.surveyMakeup() } else HouseholdMakeup.ofGroups(groups)
    var estimates by remember { mutableStateOf<Map<String, SurveyEstimate>>(emptyMap()) }
    var fromModel by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<app.necessityq.domain.EstimateFailure?>(null) }
    // "נסו שוב" asks the AI again
    var attempt by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    // the household's own answers win over any estimate; earlier answers count as its own
    val previous = saved?.takeIf { !it.skipped }?.amounts?.mapValues { it.value.amount }.orEmpty()
    var answers by remember { mutableStateOf(previous) }

    LaunchedEffect(makeup, attempt) {
        loading = true
        failure = null
        if (!profiles) delay(600) // the group counts settle before asking
        val r = vm.estimateSurvey(makeup)
        estimates = r.estimates
        fromModel = r.fromModel
        failure = r.failure
        loading = false
    }

    val items = FirstListSurvey.itemsFor(makeup)
    fun amountOf(item: SurveyItem): Double = answers[item.name] ?: estimates[item.name]?.amount ?: 0.0
    fun now() = System.currentTimeMillis()
    val ownMakeup = if (profiles) null else makeup.takeIf { !it.isEmpty }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("כמה אתם קונים בדרך כלל?", style = MaterialTheme.typography.titleLarge)
        Text(
            "שאלון חד-פעמי: ממלאים אותו פעם אחת, וממנו האפליקציה לומדת את הכמויות לרשימות הבאות. " +
                "אחרי כל קנייה הכמויות מתעדכנות לפי מה שקניתם באמת.",
            Modifier.fillMaxWidth().clip(NqShapes.Panel).background(MaterialTheme.colorScheme.primaryContainer).padding(10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (profiles) {
            Text("לפי בני המשפחה: ${makeup.describe()}", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("מי גר בבית?", style = MaterialTheme.typography.titleSmall)
            AgeGroup.entries.forEach { g ->
                val n = groups[g] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Stepper(
                        text = n.toString(),
                        canMinus = n > 0,
                        onMinus = { groups = groups + (g to (n - 1).coerceAtLeast(0)) },
                        onPlus = { groups = groups + (g to (n + 1).coerceAtMost(HouseholdMakeup.MAX_PEOPLE)) },
                        minusLabel = "פחות ${g.label}",
                        plusLabel = "עוד ${g.label}",
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            } else if (fromModel) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                when {
                    loading -> "ה-AI מנתח את הבית שלכם ומחשב כמויות… (עד כחצי דקה)"
                    fromModel -> "הכמויות הוצעו על ידי AI לפי מספר בני הבית והגילים (נשלחים רק הגילים, בלי שמות). תקנו לפי מה שאתם קונים באמת."
                    failure != null -> failureText(failure!!)
                    else -> "הכמויות לפי הערכה כללית לבית בגודל שלכם. תקנו לפי מה שאתם קונים באמת."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (failure != null && !loading) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // a failure that may pass (no signal, too slow, an error) can be tried again; the daily limit and a missing set-up cannot
        val canRetry = !loading && failure != null && failure != app.necessityq.domain.EstimateFailure.LIMIT &&
            failure != app.necessityq.domain.EstimateFailure.UNAVAILABLE
        if (canRetry) TextButton(onClick = { attempt++ }) { Text("נסו שוב עם AI") }

        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).fadingEdges(listState, horizontal = false), state = listState) {
            SurveyPeriod.entries.forEach { period ->
                val inPeriod = items.filter { it.period == period }
                if (inPeriod.isEmpty()) return@forEach
                item(key = "h_${period.name}") {
                    Text(
                        if (period == SurveyPeriod.WEEK) "כמה אתם קונים בשבוע" else "כמה אתם קונים בחודש",
                        Modifier.padding(top = 10.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(inPeriod, key = { it.name }) { item ->
                    val amount = amountOf(item)
                    val usual = estimates[item.name]?.takeIf { it.source != SurveyEstimate.Source.HISTORY }?.amount ?: 0.0
                    val warning = if (loading) null else FirstListSurvey.check(item, amount, usual)
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(item.category)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            when {
                                warning != null -> Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                estimates[item.name]?.source == SurveyEstimate.Source.HISTORY && item.name !in answers ->
                                    Text("לפי הקניות שלכם", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Stepper(
                            text = FirstListSurvey.format(item, amount),
                            canMinus = amount > 0.0,
                            onMinus = { answers = answers + (item.name to FirstListSurvey.step(item, amount, -1)) },
                            onPlus = { answers = answers + (item.name to FirstListSurvey.step(item, amount, +1)) },
                            minusLabel = "פחות ${item.name}",
                            plusLabel = "יותר ${item.name}",
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSkip(HouseholdBaseline(now(), makeup = ownMakeup, skipped = true)) }) { Text("דילוג") }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = !loading && !makeup.isEmpty,
                onClick = { onSave(FirstListSurvey.baseline(items.associate { it.name to amountOf(it) }, ownMakeup, now())) },
            ) { Text("שמירה והמשך") }
        }
    }
}

/** What went wrong with the AI, in plain words, and what the user sees instead. */
private fun failureText(f: app.necessityq.domain.EstimateFailure): String = when (f) {
    app.necessityq.domain.EstimateFailure.OFFLINE -> "אין חיבור לאינטרנט, ולכן ה-AI לא ענה. מוצגות כמויות כלליות; אפשר לנסות שוב כשיש חיבור."
    app.necessityq.domain.EstimateFailure.TIMEOUT -> "ה-AI לא הספיק לענות בזמן. מוצגות כמויות כלליות; אפשר לנסות שוב."
    app.necessityq.domain.EstimateFailure.LIMIT -> "הגעתם למכסה היומית של ה-AI. מוצגות כמויות כלליות; מחר אפשר לנסות שוב."
    app.necessityq.domain.EstimateFailure.SIGNED_OUT -> "צריך להתחבר לחשבון כדי להשתמש ב-AI. מוצגות כמויות כלליות."
    app.necessityq.domain.EstimateFailure.BAD_REPLY -> "ה-AI החזיר תשובה שלא הצלחנו לקרוא. מוצגות כמויות כלליות; אפשר לנסות שוב."
    app.necessityq.domain.EstimateFailure.UNAVAILABLE -> "ה-AI לא זמין כאן (בלי בית משותף בענן). מוצגות כמויות כלליות."
    app.necessityq.domain.EstimateFailure.ERROR -> "אירעה שגיאה בפנייה ל-AI. מוצגות כמויות כלליות; אפשר לנסות שוב."
}

/** − amount + : the amount in the middle, never below zero. */
@Composable
private fun Stepper(text: String, canMinus: Boolean, onMinus: () -> Unit, onPlus: () -> Unit, minusLabel: String, plusLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus, enabled = canMinus) { Icon(Icons.Default.Remove, contentDescription = minusLabel) }
        Text(text, Modifier.widthIn(min = 64.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = onPlus) { Icon(Icons.Default.Add, contentDescription = plusLabel) }
    }
}

/** The survey's age group of a person [age] years old (to show earlier answers). */
private fun groupOf(age: Int): AgeGroup = when {
    age >= 13 -> AgeGroup.ADULTS
    age >= 6 -> AgeGroup.KIDS
    age >= 2 -> AgeGroup.SMALL
    else -> AgeGroup.BABIES
}
