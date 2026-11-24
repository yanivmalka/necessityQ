package app.necessityq.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.BuildConfig
import app.necessityq.domain.Account
import app.necessityq.domain.ListMode
import app.necessityq.domain.TileElement

/**
 * Back action of the current page. Null on the bottom-bar tabs (they have no back button) and set on
 * pages reached from another screen (the "הבית" hub, or messages opened from the list).
 */
val LocalNavBack = compositionLocalOf<(() -> Unit)?> { null }

/** New messages across the app: a badge on the "הבית" tab. */
val LocalUnreadTotal = compositionLocalOf { 0 }

/** Prices of products sold by weight are shown per kilo or per 100 g, as chosen in the display settings. */
val LocalWeightPriceUnit = compositionLocalOf { app.necessityq.domain.WeightPriceUnit.KILO }

/**
 * A price of [p] in its own price unit ([price]: today's, the low of a range, another branch's in p's unit), as shown:
 * by weight in the chosen unit with its label ("₪8.90 לק\"ג"); otherwise as published.
 */
fun shownProductPrice(p: app.necessityq.domain.BranchProduct, price: Double, unit: app.necessityq.domain.WeightPriceUnit): String {
    val k = p.priceUnitKilos
    return if (p.soldByWeight && k != null) "${app.necessityq.domain.PriceData.formatPrice(unit.convert(price, k))} ${unit.label}"
    else app.necessityq.domain.PriceData.formatPrice(price)
}

/** Back arrow in a page's top bar; renders nothing on a top-level tab. */
@Composable
fun BackNavButton() {
    val back = LocalNavBack.current ?: return
    IconButton(onClick = back) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
    }
}

/** Common frame for the secondary pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { BackNavButton() },
            title = { Text(title, fontWeight = FontWeight.ExtraBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
fun AccountScreen(vm: AppViewModel, account: Account, modifier: Modifier = Modifier) = PageScaffold("חשבון", modifier) {
    if (vm.supportsAccounts) AccountCard(vm, account)
    else Text("גרסה זו פועלת במצב מכשיר יחיד, ללא חשבון.")
}

@Composable
fun UpdatesScreen(vm: AppViewModel, modifier: Modifier = Modifier) = PageScaffold("עדכונים", modifier) {
    UpdateCard(vm)
}

/** A part of the settings screen: a clear title over its options. */
@Composable
private fun SettingsSection(title: String) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

/**
 * One settings screen, in parts: the list and its tiles · appearance (theme, colours, icon) · notifications
 * (צ'אט פוד, and who hears about a missing item).
 */
@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier) = PageScaffold("הגדרות", modifier) {
    val current by vm.display.collectAsStateWithLifecycle()
    val sound by vm.chatSound.collectAsStateWithLifecycle()

    SettingsSection("רשימה ואריחים")
    var editing by remember { mutableStateOf<ListMode?>(null) }
    Text("מה להציג על כל פריט ברשימה", style = MaterialTheme.typography.titleMedium)
    Text(
        "לכל שלב תצוגה משלו. נגיעה ברכיב באריח לדוגמה מסתירה או מחזירה אותו.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ListMode.entries.forEach { m ->
            OutlinedButton(onClick = { editing = m }, modifier = Modifier.weight(1f)) {
                Text("${m.label} · ${current.tile(m).shown.size}/${TileElement.entries.size}")
            }
        }
    }
    editing?.let { m ->
        TileEditorSheet(
            mode = m,
            layout = current.tile(m),
            onSave = { vm.setDisplay(current.withTile(m, it)); editing = null },
            onDismiss = { editing = null },
        )
    }

    Text("רשימה", style = MaterialTheme.typography.titleMedium)
    OptionRow("מחיר משוער בתחתית הרשימה", current.showTotal) { vm.setDisplay(current.copy(showTotal = it)) }
    OptionRow("מוצרי קירור והקפאה בסוף הרשימה", current.chilledLast) { vm.setDisplay(current.copy(chilledLast = it)) }
    OptionRow("מוצרים שסומנו או נמחקו (מחוקים בקו) בתחתית הרשימה", current.showBought) { vm.setDisplay(current.copy(showBought = it)) }

    Text("מחיר של מוצר במשקל", style = MaterialTheme.typography.titleMedium)
    Text(
        "ירקות, פירות, בשר ומוצרים שנמכרים במשקל: המחיר ברשימה, בדף המוצר ובהשוואה בין הסניפים.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    app.necessityq.domain.WeightPriceUnit.entries.forEach { unit ->
        Row(
            Modifier.fillMaxWidth().clickable { vm.setDisplay(current.copy(weightUnit = unit)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = current.weightUnit == unit, onClick = { vm.setDisplay(current.copy(weightUnit = unit)) })
            Text("מחיר " + unit.title)
        }
    }

    Text("נגיעה בפריט", style = MaterialTheme.typography.titleMedium)
    app.necessityq.platform.TapMode.entries.forEach { mode ->
        Row(
            Modifier.fillMaxWidth().clickable { vm.setDisplay(current.copy(tapMode = mode)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = current.tapMode == mode, onClick = { vm.setDisplay(current.copy(tapMode = mode)) })
            Column {
                Text(mode.label)
                Text(mode.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Text("פרטים בהמלצות", style = MaterialTheme.typography.titleMedium)
    OptionRow("סיבת ההמלצה", current.showReason) { vm.setDisplay(current.copy(showReason = it)) }
    OptionRow("מתי נקנה לאחרונה", current.showLastBought) { vm.setDisplay(current.copy(showLastBought = it)) }
    OptionRow("תדירות קנייה", current.showFrequency) { vm.setDisplay(current.copy(showFrequency = it)) }
    OptionRow("סימון מועדפים ★", current.showFavorite) { vm.setDisplay(current.copy(showFavorite = it)) }

    SettingsSection("מראה")
    DisplayCard(vm)
    PalettePicker(vm)
    AppIconPicker()

    SettingsSection("התראות")
    Text("צ'אט פוד", style = MaterialTheme.typography.titleMedium)
    Text(
        "כדי שלא כל בני הבית יקבלו התראה על כל הודעה: התראה מגיעה רק למי שתויג ב-@ ולמי שההודעה שלו צוטטה.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val notifyMode by vm.chatNotifyMode.collectAsStateWithLifecycle()
    val loud by vm.loudMentions.collectAsStateWithLifecycle()
    app.necessityq.domain.ChatNotifyMode.entries.forEach { mode ->
        Row(
            Modifier.fillMaxWidth().clickable { vm.setChatNotifyMode(mode) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = notifyMode == mode, onClick = { vm.setChatNotifyMode(mode) })
            Text("התראה: " + mode.label)
        }
    }
    OptionRow("תיוג או ציטוט נשמעים גם כשהטלפון על שקט", loud) { vm.setLoudMentions(it) }
    OptionRow("צליל להודעות שאינן תיוג (כשבחרתם \"כל הודעה\")", sound) { vm.setChatSound(it) }

    // who hears about an item marked missing — next to the chat's notifications, all in one place
    if (vm.isShared) {
        val members by vm.members.collectAsStateWithLifecycle()
        NotifyCard(vm, members, vm.me)
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
fun AboutScreen(modifier: Modifier = Modifier) = PageScaffold("אודות", modifier) {
    val context = LocalContext.current
    Text("necessityQ", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    Text("גרסה ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    Text(
        "רשימת קניות משותפת לבית שלומדת מה צורכים ובאיזו תדירות, ומציעה מה להוסיף. " +
            "המוצרים נרשמים בשם כללי (קוטג' 5%, גבינה צהובה) ולא לפי חברה.",
    )
    Text("שלבי שימוש", style = MaterialTheme.typography.titleMedium)
    Text("• הכנת רשימה — מלאי, הצעות, מחיר ומבצע, חלוקה לקטגוריות.")
    Text("• בקנייה — איסוף, סדר איסוף לפי חנות, חסרים והתייעצות על תחליפים.")
    Text("• תפריט — בני משפחה, מאכלים אהובים ותפריט שבועי שממלא את הרשימה.")
    TextButton(onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yanivmalka/necessityQ")))
    }) { Text("קוד המקור ב-GitHub") }
}
