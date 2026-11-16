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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.BuildConfig
import app.necessityq.domain.Account

/** Opens the navigation drawer from any screen's top bar. */
val LocalOpenDrawer = compositionLocalOf<() -> Unit> { {} }

/** New messages across the app — shown as a dot on the menu button of every screen. */
val LocalUnreadTotal = compositionLocalOf { 0 }

@Composable
fun DrawerButton() {
    val open = LocalOpenDrawer.current
    val unread = LocalUnreadTotal.current
    IconButton(onClick = open) {
        BadgedBox(badge = { if (unread > 0) Badge() }) {
            Icon(Icons.Default.Menu, contentDescription = if (unread > 0) "תפריט ראשי — $unread הודעות חדשות" else "תפריט ראשי")
        }
    }
}

/** Common frame for the drawer's secondary pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { DrawerButton() },
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
fun AppearanceScreen(vm: AppViewModel, modifier: Modifier = Modifier) = PageScaffold("מראה", modifier) {
    DisplayCard(vm)
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

/** What the list and the recommendations show. */
@Composable
fun DisplaySettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier) = PageScaffold("הגדרות תצוגה", modifier) {
    val current by vm.display.collectAsStateWithLifecycle()
    val sound by vm.chatSound.collectAsStateWithLifecycle()

    Text("מה להציג על כל פריט ברשימה", style = MaterialTheme.typography.titleMedium)
    OptionRow("כמות", current.showQuantity) { vm.setDisplay(current.copy(showQuantity = it)) }
    OptionRow("כפתורי כמות (+ / −)", current.showStepper) { vm.setDisplay(current.copy(showStepper = it)) }
    OptionRow("מחירים", current.showPrices) { vm.setDisplay(current.copy(showPrices = it)) }
    OptionRow("סטטיסטיקה (נקנה לאחרונה, תדירות)", current.showStats) { vm.setDisplay(current.copy(showStats = it)) }
    OptionRow("מחיר משוער בתחתית הרשימה", current.showTotal) { vm.setDisplay(current.copy(showTotal = it)) }
    OptionRow("מוצרי קירור והקפאה בסוף הרשימה", current.chilledLast) { vm.setDisplay(current.copy(chilledLast = it)) }

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

    Text("הודעות", style = MaterialTheme.typography.titleMedium)
    OptionRow("צליל כשמגיעה הודעה חדשה", sound) { vm.setChatSound(it) }

    Text("פרטים בהמלצות ובפריטים", style = MaterialTheme.typography.titleMedium)
    OptionRow("סיבת ההמלצה", current.showReason) { vm.setDisplay(current.copy(showReason = it)) }
    OptionRow("מתי נקנה לאחרונה", current.showLastBought) { vm.setDisplay(current.copy(showLastBought = it)) }
    OptionRow("תדירות קנייה", current.showFrequency) { vm.setDisplay(current.copy(showFrequency = it)) }
    OptionRow("סימון מועדפים ★", current.showFavorite) { vm.setDisplay(current.copy(showFavorite = it)) }
    OptionRow("מי הוסיף", current.showAddedBy) { vm.setDisplay(current.copy(showAddedBy = it)) }
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
