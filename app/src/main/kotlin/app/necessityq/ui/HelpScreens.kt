package app.necessityq.ui

import androidx.compose.foundation.background
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

/** One topic of the help page: a title and its lines. */
private class HelpTopic(val title: String, val lines: List<String>)

private val HELP = listOf(
    HelpTopic(
        "שני שלבים: הכנת רשימה ובקנייה",
        listOf(
            "המתג בראש הרשימה עובר בין \"הכנת רשימה\" (בבית: מוסיפים ומתכננים) ל\"בקנייה\" (בחנות: מסמנים מה נכנס לעגלה).",
            "\"יוצאים לקנות\" בתחתית הרשימה עובר לקנייה. רק \"סיום קנייה\" מסיים אותה: מה שנקנה עובר לסל המחזור, ואפשר לבטל.",
        ),
    ),
    HelpTopic(
        "הוספת מוצרים",
        listOf(
            "כותבים בשורה שבראש הרשימה, כמו שמדברים: \"2 קוטג' 5%\", \"חצי קילו גבינה צהובה\", \"חלב 3% 2 ליטר\".",
            "מוצר שכבר ברשימה לא מוכפל: הכמות מתווספת לו.",
            "סמל הברקוד שבשורה סורק מוצר ומוסיף אותו.",
        ),
    ),
    HelpTopic(
        "החלקה, סימון ומחיקה",
        listOf(
            "בהכנת רשימה עוד לא קונים: החלקה ימינה על פריט מסירה אותו (לסל המחזור). אין שם סימון \"נקנה\".",
            "בקנייה: החלקה שמאלה — נקנה. החלקה ימינה — \"חסר\" (לא היה בחנות; נשאר ברשימה).",
            "מחזיקים את ההחלקה עד שהטבעת מתמלאת, כדי שנגיעה בטעות לא תסמן.",
            "מה כל החלקה עושה אפשר לבחור, לכל צד ולכל שלב: \"הגדרות\" ← \"הרשימה\" ← \"החלקה על פריט\".",
            "לא נוח להחליק? בקנייה, נגיעה בעיגול שליד הפריט מסמנת אותו כנקנה, ונגיעה בסמל ה-✓ מחזירה.",
            "כל פעולה מציגה הודעה עם \"ביטול\" לחצי דקה. מה שנמחק מחכה ב\"סל מחזור\" (בתפריט ⋮).",
        ),
    ),
    HelpTopic(
        "איך האפליקציה לומדת",
        listOf(
            "כל סימון \"נקנה\" נרשם. אחרי כמה קניות האפליקציה יודעת כל כמה זמן הבית קונה כל מוצר, ובאיזו כמות.",
            "כשעבר רוב הזמן הרגיל, המוצר מופיע ב\"כנראה נגמר בקרוב\" עם הסיבה (\"בדרך כלל כל 5 ימים · נקנה לפני 4 ימים\").",
            "\"✕\" על הצעה משתיק אותה לחצי מהזמן הרגיל. בלי למידת מכונה: כל הצעה אפשר להסביר.",
            "הלמידה ממשיכה גם כשההצעות כבויות, כך שכשמדליקים אותן הן מוכנות.",
        ),
    ),
    HelpTopic(
        "מחירים",
        listOf(
            "בלשונית \"מחירים\" בוחרים את הסניפים שקונים בהם. אחר כך המחיר מופיע ליד כל פריט, והסכום המשוער בתחתית הרשימה.",
            "מוצר שיש עליו מבצע מסומן במתנה בפינה, ומתחתיו פס עם המבצע, המחיר איתו ואיפה.",
            "עם שני סניפים במעקב: \"איפה הכי משתלם לקנות?\" בתפריט ⋮ משווה את הרשימה כולה.",
        ),
    ),
    HelpTopic(
        "צ'אט פוד",
        listOf(
            "שיחה על מוצר מסוים עם בני הבית — \"אין קוטג' 5%, לקחת 3%?\" — עם תמונה, הקלטה ותיוג ב-@.",
            "התראה מגיעה רק למי שתויג או צוטט, כדי שלא כולם יקבלו התראה על כל הודעה.",
        ),
    ),
    HelpTopic(
        "הצעות מהאפליקציה",
        listOf(
            "מדי פעם (לכל היותר פעם בשבוע) מופיע מעל הרשימה כרטיס שמציע יכולת שכנראה תעזור לכם, עם הסבר מהנתונים שלכם.",
            "\"לא תודה\" — לא נציע שוב. אפשר לחזור להצעות שדחיתם ב\"הבית\" ← \"הצעות שדחיתם\".",
        ),
    ),
    HelpTopic(
        "הגדרות",
        listOf(
            "\"הבית\" ← \"הגדרות\": יכולות (מה האפליקציה עושה; ליכולות של הרשימה מתג נפרד להכנה ולקנייה), הרשימה, מראה והתראות.",
            "\"מה מוצג על כל פריט\" (בתפריט ⋮) בוחר מה מופיע על הפריטים ברשימה, לכל שלב בנפרד.",
        ),
    ),
)

/** "עזרה: איך משתמשים": the gestures, the two stages, how the app learns — always there, not only in the first hints. */
@Composable
fun HelpScreen(modifier: Modifier = Modifier) = PageScaffold("עזרה: איך משתמשים", modifier) {
    HELP.forEach { topic ->
        Column(
            Modifier.fillMaxWidth().clip(NqShapes.Tile).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, NqTheme.colors.hairline, NqShapes.Tile).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(topic.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            topic.lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
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
