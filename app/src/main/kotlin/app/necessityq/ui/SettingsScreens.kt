package app.necessityq.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * The three tabs of the settings screen, each about one idea, the most used first: the list (how it behaves and
 * what each item shows), the app (its look, its features and its tabs), and notifications.
 */
enum class SettingsTab(val label: String) {
    LIST("הרשימה"),
    APP("האפליקציה"),
    NOTIFY("התראות"),
}

/**
 * The settings, in three tabs switched at the top of the page ([SettingsTab]); it opens on [initialTab]. Within a
 * tab the settings are grouped in cards, the most important first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier, initialTab: SettingsTab = SettingsTab.LIST) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { BackNavButton() },
            title = { Text("הגדרות", fontWeight = FontWeight.ExtraBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Box(Modifier.padding(horizontal = 16.dp)) {
            SegmentedTabs(SettingsTab.entries.map { it.label }, tab.ordinal) { tab = SettingsTab.entries[it] }
        }
        // each tab scrolls on its own, from its top
        key(tab) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    SettingsTab.LIST -> ListSettings(vm)
                    SettingsTab.APP -> AppSettings(vm)
                    SettingsTab.NOTIFY -> NotifySettings(vm)
                }
            }
        }
    }
}

/** "הרשימה": what a tap on an item does, what each tile shows, the order of the list, prices and recommendations. */
@Composable
private fun ListSettings(vm: AppViewModel) {
    val current by vm.display.collectAsStateWithLifecycle()
    SettingsCard("נגיעה בפריט", "מה קורה כשנוגעים בפריט ברשימה.") {
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
    }

    var editing by remember { mutableStateOf<ListMode?>(null) }
    SettingsCard("מה להציג על כל פריט ברשימה", "לכל שלב תצוגה משלו. נגיעה ברכיב באריח לדוגמה מסתירה או מחזירה אותו.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ListMode.entries.forEach { m ->
                OutlinedButton(onClick = { editing = m }, modifier = Modifier.weight(1f)) {
                    Text("${m.label} · ${current.tile(m).shown.size}/${TileElement.entries.size}")
                }
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

    SettingsCard("סדר הרשימה") {
        OptionRow("מוצרי קירור והקפאה בסוף הרשימה", current.chilledLast) { vm.setDisplay(current.copy(chilledLast = it)) }
        OptionRow("מוצרים שסומנו או נמחקו (מחוקים בקו) בתחתית הרשימה", current.showBought) { vm.setDisplay(current.copy(showBought = it)) }
    }

    SettingsCard("מחירים") {
        OptionRow("מחיר משוער בתחתית הרשימה", current.showTotal) { vm.setDisplay(current.copy(showTotal = it)) }
        Text("מחיר של מוצר במשקל", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        Text(
            "ירקות, פירות, בשר ומוצרים שנמכרים במשקל: המחיר ברשימה, בדף המוצר ובהשוואה בין הסניפים.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
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
    }

    SettingsCard("פרטים בהמלצות", "מה מופיע בכרטיסי \"כנראה נגמר בקרוב\".") {
        app.necessityq.platform.RecommendationDetail.entries.forEach { detail ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.setDisplay(current.copy(recommendationDetail = detail)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current.recommendationDetail == detail, onClick = { vm.setDisplay(current.copy(recommendationDetail = detail)) })
                Text(detail.label)
            }
        }
        OptionRow("סימון מועדפים ★", current.showFavorite) { vm.setDisplay(current.copy(showFavorite = it)) }
    }
}

/** "האפליקציה": its look (theme, colours, icon), which features are on, and the order of the tabs. */
@Composable
private fun AppSettings(vm: AppViewModel) {
    SettingsCard("מראה") {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DisplayCard(vm)
            PalettePicker(vm)
            AppIconPicker(vm)
        }
    }

    val features by vm.features.collectAsStateWithLifecycle()
    SettingsCard(
        "פיצ'רים",
        "כל פיצ'ר אפשר לכבות ולהדליק בחזרה בכל זמן. כששתי הלשוניות \"תפריט\" ו\"מחירים\" כבויות, נשארות למטה שתי לשוניות: הרשימה והבית.",
    ) {
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.setFeatures(app.necessityq.domain.Features.SIMPLE) }) { Text("רשימה פשוטה") }
            OutlinedButton(onClick = { vm.setFeatures(app.necessityq.domain.Features.ALL) }) { Text("הכול") }
        }
        app.necessityq.domain.Feature.entries.forEach { feature ->
            val on = feature in features
            Row(
                Modifier.fillMaxWidth().clickable { vm.setFeatures(features.with(feature, !on)) }.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(feature.label, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = on, onCheckedChange = { vm.setFeatures(features.with(feature, it)) })
            }
        }
    }

    SettingsCard("סדר הלשוניות") { TabOrderSettings(vm) }
}

/** "התראות": צ'אט פוד (who is notified, and how it sounds), and who hears about an item marked missing. */
@Composable
private fun NotifySettings(vm: AppViewModel) {
    val notifyMode by vm.chatNotifyMode.collectAsStateWithLifecycle()
    val loud by vm.loudMentions.collectAsStateWithLifecycle()
    val sound by vm.chatSound.collectAsStateWithLifecycle()
    SettingsCard(
        "צ'אט פוד",
        "כדי שלא כל בני הבית יקבלו התראה על כל הודעה: התראה מגיעה רק למי שתויג ב-@ ולמי שההודעה שלו צוטטה.",
    ) {
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
    }

    if (vm.isShared) {
        val members by vm.members.collectAsStateWithLifecycle()
        NotifyCard(vm, members, vm.me)
    } else {
        Text(
            "התראות על מוצר חסר מופיעות כאן כשמשתפים את הרשימה עם בני הבית.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** A group of settings about one thing: a card with a title, an optional line of explanation, and the options. */
@Composable
private fun SettingsCard(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(NqShapes.Tile).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, NqTheme.colors.hairline, NqShapes.Tile).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}

/**
 * The tabs in their order (the first is on the right of the bar): drag a tab by its handle to move it, and pick the
 * one the app opens on.
 */
@Composable
private fun TabOrderSettings(vm: AppViewModel) {
    val order by vm.tabOrder.collectAsStateWithLifecycle()
    val start by vm.startTab.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()
    Text(
        "גררו לשונית בידית כדי להזיז אותה (הראשונה מימין בסרגל). העיגול בוחר באיזו לשונית האפליקציה נפתחת.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    sh.calvin.reorderable.ReorderableColumn(
        list = order,
        onSettle = { from, to -> vm.moveTab(from, to) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { _, tab, isDragging ->
        // a tab turned off in "פיצ'רים" keeps its place, greyed
        val off = (tab == app.necessityq.domain.AppTab.MENU && app.necessityq.domain.Feature.MENU_TAB !in features) ||
            (tab == app.necessityq.domain.AppTab.PRICES && app.necessityq.domain.Feature.PRICES_TAB !in features)
        androidx.compose.runtime.key(tab) {
            Row(
                Modifier.fillMaxWidth()
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        NqShapes.Panel,
                    )
                    .clickable(enabled = !off) { vm.setStartTab(tab) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = start == tab, onClick = { vm.setStartTab(tab) }, enabled = !off)
                Text(
                    tab.label + (if (start == tab) " · נפתחת ראשונה" else "") + (if (off) " (כבויה)" else ""),
                    modifier = Modifier.weight(1f),
                    color = if (off) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "גרירה: ${tab.label}",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.draggableHandle().padding(12.dp),
                )
            }
        }
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
