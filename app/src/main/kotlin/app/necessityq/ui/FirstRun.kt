package app.necessityq.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.necessityq.domain.FirstRunAnswers
import app.necessityq.domain.FirstRunQuestion
import app.necessityq.domain.Features
import app.necessityq.domain.TripCadence
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role

/**
 * The first run, after the household is made: a few cards, one question each, about the household's habits — not
 * about feature names. Each card says what "yes" gives and what "no" gives up, so the choice is an informed one.
 * The last card sums up what was turned on, and that everything can change at any time while the app keeps
 * learning in the background. "דילוג" takes the recommended choice at once.
 *
 * After the yes/no questions, one more: how often the household goes shopping ([TripCadence]); every trip's amounts
 * follow it. It can be changed later in the settings.
 *
 * [onDone]: the features chosen, whether to go straight on to inviting the household, and how often it shops.
 */
@Composable
fun FirstRunQuestions(onDone: (Features, Boolean, TripCadence) -> Unit) {
    val questions = FirstRunQuestion.entries
    // the yes/no questions, then how often the household shops, then the summary
    val total = questions.size + 1
    var step by rememberSaveable { mutableIntStateOf(0) }
    var cadence by rememberSaveable { mutableStateOf(TripCadence.DEFAULT) }
    // the answers, as the names of the questions answered "yes"
    var yes by rememberSaveable { mutableStateOf(FirstRunAnswers.DEFAULT.yes.map { it.name }.toSet()) }
    val answers = FirstRunAnswers(questions.filter { it.name in yes }.toSet())

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (step < total) "שאלה ${step + 1} מתוך $total" else "מוכנים",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (step < total) {
                        TextButton(onClick = { onDone(Features.RECOMMENDED, false, cadence) }) { Text("דילוג (הגדרות מומלצות)") }
                    }
                }
                LinearProgressIndicator(
                    progress = { (step.toFloat() / total).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                AnimatedContent(targetState = step, label = "firstRun", modifier = Modifier.weight(1f)) { shown ->
                    Column(
                        Modifier.fillMaxSize().fadingVerticalScroll(rememberScrollState()).padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        when {
                            shown < questions.size -> QuestionCard(questions[shown])
                            shown == questions.size -> CadenceCard(cadence) { cadence = it }
                            else -> Summary(answers, cadence)
                        }
                    }
                }
                if (step < questions.size) {
                    val q = questions[step]
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { yes = yes - q.name; step++ },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text("לא") }
                        Button(
                            onClick = { yes = yes + q.name; step++ },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text("כן") }
                    }
                    if (step > 0) {
                        TextButton(onClick = { step-- }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("חזרה לשאלה הקודמת") }
                    }
                } else if (step == questions.size) {
                    Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 12.dp)) { Text("המשך") }
                    TextButton(onClick = { step-- }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("חזרה לשאלה הקודמת") }
                } else {
                    Button(
                        onClick = { onDone(answers.features(), FirstRunQuestion.SHARED in answers.yes, cadence) },
                        modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 4.dp),
                    ) {
                        Text(if (FirstRunQuestion.SHARED in answers.yes) "להתחיל ולהזמין את בני הבית" else "להתחיל")
                    }
                    TextButton(onClick = { step = 0 }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("לענות מחדש") }
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(q: FirstRunQuestion) {
    if (q.recommended) {
        Text(
            "מומלץ",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.clip(NqShapes.Pill).background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
    Text(q.question, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    InfoBlock("אם כן", q.gain, MaterialTheme.colorScheme.primary)
    InfoBlock("אם לא", q.loss, NqTheme.colors.hairline)
}

/** How often the household goes shopping: one of three, the amounts of every trip follow it. */
@Composable
private fun CadenceCard(selected: TripCadence, onSelect: (TripCadence) -> Unit) {
    Text("כל כמה זמן אתם עושים קנייה גדולה?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "הכמויות ברשימה יחושבו עד הקנייה הבאה. מוצר שנגמר לאט יותר, כמו אבקת כביסה, יוצע ביחידה אחת ורק כשהגיע זמנו.",
        style = MaterialTheme.typography.bodyMedium,
    )
    TripCadence.entries.forEach { c ->
        val on = c == selected
        Row(
            Modifier.fillMaxWidth().clip(NqShapes.Tile)
                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .border(1.dp, if (on) MaterialTheme.colorScheme.primary else NqTheme.colors.hairline, NqShapes.Tile)
                .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(c) })
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = on, onClick = null)
            Spacer(Modifier.width(10.dp))
            Text(c.label, style = MaterialTheme.typography.titleMedium)
        }
    }
    Text("אפשר לשנות בכל זמן בהגדרות, בלשונית הרשימה.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InfoBlock(title: String, text: String, accent: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier.fillMaxWidth().clip(NqShapes.Tile).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent, NqShapes.Tile).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Summary(answers: FirstRunAnswers, cadence: TripCadence) {
    Text("זה מה שהדלקנו", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("קנייה ${cadence.label}: הכמויות עד הקנייה הבאה.", style = MaterialTheme.typography.bodyLarge)
    val on = answers.features()
    if (on.plan.isEmpty()) {
        Text("רשימת קניות משותפת בלבד, בלי הצעות ובלי מחירים.", style = MaterialTheme.typography.bodyLarge)
    }
    app.necessityq.domain.Feature.entries.filter { it in on }.forEach { f ->
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(f.label, fontWeight = FontWeight.SemiBold)
                Text(f.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    InfoBlock(
        "אפשר לשנות הכול בכל זמן",
        "בלשונית הבית, בהגדרות, ביכולות. האפליקציה לומדת מכל קנייה ברקע גם כשההצעות כבויות, " +
            "כך שכשתדליקו אותן הן יהיו מוכנות, עם כל ההיסטוריה. ואם יש משהו שכנראה יעזור לכם, נציע אותו בהמשך.",
        NqTheme.colors.hairline,
    )
}
