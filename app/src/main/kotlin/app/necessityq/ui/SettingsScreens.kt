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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.necessityq.ui.icons.Icons
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
            Modifier.fadingVerticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
fun AccountScreen(vm: AppViewModel, account: Account, modifier: Modifier = Modifier) = PageScaffold("חשבון", modifier) {
    if (vm.supportsAccounts) AccountCard(vm, account)
    else Text("גרסה זו פועלת במצב מכשיר יחיד, ללא חשבון.")
    // "הדגמת התחלה": only for the accounts in FirstRunDemo
    if (vm.canReplayFirstRun(account)) {
        var confirm by remember { mutableStateOf(false) }
        SettingsCard(
            "הדגמת התחלה",
            "לראות את האפליקציה כמו משתמש חדש: מסך הפתיחה, דף הפתיחה והשאלות, וההסברים וההצעות כמו בהתקנה חדשה. " +
                "שום בית לא נוצר ושום דבר לא נמחק: הרשימה, הבית וההיסטוריה נשמרים. היכולות ייקבעו לפי התשובות לשאלות.",
        ) {
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.padding(horizontal = 4.dp)) { Text("להתחיל מאפס (הדגמה)") }
        }
        if (confirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("להתחיל את ההדגמה?") },
                text = {
                    Text(
                        "האפליקציה תיפתח כמו בפעם הראשונה. אפשר לצאת מההדגמה בדף הפתיחה, ואז היכולות יחזרו למה שהיו. " +
                            "מי שמסיים את השאלות מקבל את היכולות לפי התשובות.",
                    )
                },
                confirmButton = { TextButton(onClick = { confirm = false; vm.startFirstRunReplay() }) { Text("להתחיל") } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("ביטול") } },
            )
        }
    }
}

@Composable
fun UpdatesScreen(vm: AppViewModel, modifier: Modifier = Modifier) = PageScaffold("עדכונים", modifier) {
    UpdateCard(vm)
}

/**
 * The four tabs of the settings screen, each about one idea, the most important first: what the app does (its
 * features, with the settings of each one under it), the list (how it behaves and what each item shows), the look,
 * and notifications.
 */
enum class SettingsTab(val label: String) {
    FEATURES("יכולות"),
    LIST("הרשימה"),
    LOOK("מראה"),
    NOTIFY("התראות"),
}

/**
 * The settings, in four tabs switched at the top of the page ([SettingsTab]); it opens on [initialTab]. Within a
 * tab the settings are grouped in cards, the most important first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier, initialTab: SettingsTab = SettingsTab.FEATURES) {
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
                Modifier.fadingVerticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    SettingsTab.FEATURES -> FeatureSettings(vm)
                    SettingsTab.LIST -> ListSettings(vm)
                    SettingsTab.LOOK -> LookSettings(vm)
                    SettingsTab.NOTIFY -> NotifySettings(vm)
                }
            }
        }
    }
}

/**
 * "יכולות": what the app does, grouped by what it is for. Each feature has a line saying what it gives, and the
 * settings that belong to it sit under it — hidden while it is off, so nothing on the page does nothing.
 */
@Composable
private fun FeatureSettings(vm: AppViewModel) {
    val features by vm.features.collectAsStateWithLifecycle()
    val current by vm.display.collectAsStateWithLifecycle()
    val followed by vm.selectedPriceBranches.collectAsStateWithLifecycle()
    SettingsCard(
        "מה האפליקציה עושה בשבילכם",
        "כיבוי רק מסתיר: האפליקציה ממשיכה ללמוד מכל קנייה ברקע, כך שכשמדליקים שוב הכול מוכן, עם כל ההיסטוריה.",
    ) {
        Text(
            "בחירה מהירה:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "רשימה פשוטה" to app.necessityq.domain.Features.SIMPLE,
                "מומלץ" to app.necessityq.domain.Features.RECOMMENDED,
                "הכול" to app.necessityq.domain.Features.ALL,
            ).forEach { (label, preset) ->
                val chosen = features == preset
                if (chosen) {
                    androidx.compose.material3.Button(onClick = {}, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(label, maxLines = 1)
                    }
                } else {
                    OutlinedButton(onClick = { vm.setFeatures(preset) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }
        Text(
            "רשימה פשוטה: הרשימה ו\"כנראה נגמר בקרוב\". מומלץ: גם \"קונים בדרך כלל\", מחירים ומבצעים. הכול: כל מה שיש.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    app.necessityq.domain.FeatureGroup.entries.forEach { group ->
        SettingsCard(group.label, group.hint) {
            app.necessityq.domain.Feature.entries.filter { it.group == group }.forEach { feature ->
                val on = feature in features
                SwitchRow(feature.label, on, feature.description) { vm.setFeatures(features.with(feature, it)) }
                if (on) {
                    // the settings of this feature, under it
                    Column(Modifier.padding(start = 20.dp, bottom = 6.dp)) {
                        when (feature) {
                            app.necessityq.domain.Feature.RUNNING_LOW -> {
                                SubTitle("מה מופיע בכל הצעה")
                                app.necessityq.platform.RecommendationDetail.entries.forEach { detail ->
                                    RadioRow(detail.label, current.recommendationDetail == detail) {
                                        vm.setDisplay(current.copy(recommendationDetail = detail))
                                    }
                                }
                                SwitchRow("סימון מועדפים ★ בהצעות", current.showFavorite) { vm.setDisplay(current.copy(showFavorite = it)) }
                            }
                            app.necessityq.domain.Feature.PRICES -> {
                                SwitchRow("סכום משוער בתחתית הרשימה", current.showTotal) { vm.setDisplay(current.copy(showTotal = it)) }
                                SubTitle("מחיר של מוצר במשקל")
                                Text(
                                    "ירקות, פירות, בשר ומוצרים שנמכרים במשקל: ברשימה, בדף המוצר ובהשוואה בין הסניפים.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                                app.necessityq.domain.WeightPriceUnit.entries.forEach { unit ->
                                    RadioRow("מחיר " + unit.title, current.weightUnit == unit) { vm.setDisplay(current.copy(weightUnit = unit)) }
                                }
                            }
                            app.necessityq.domain.Feature.TRIP_PLAN -> {
                                if (followed.size < 2) {
                                    // without two branches the comparison never shows: say why
                                    Text(
                                        "כדי להשוות צריך לעקוב אחרי שני סניפים לפחות (בלשונית \"מחירים\"). כרגע: ${followed.size}.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                            }
                            app.necessityq.domain.Feature.CHAT -> Text(
                                "מתי מקבלים התראה על הודעה: בלשונית \"התראות\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

/** "הרשימה": what a tap on an item does, what each item shows, and the order of the list. */
@Composable
private fun ListSettings(vm: AppViewModel) {
    val current by vm.display.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()
    SettingsCard("נגיעה בפריט", "מה קורה כשנוגעים בפריט ברשימה.") {
        app.necessityq.platform.TapMode.entries.forEach { mode ->
            RadioRow(mode.label, current.tapMode == mode, mode.hint) { vm.setDisplay(current.copy(tapMode = mode)) }
        }
    }

    var editing by remember { mutableStateOf<ListMode?>(null) }
    SettingsCard(
        "מה להציג על כל פריט ברשימה",
        "לכל שלב תצוגה משלו: בהכנת רשימה ובקנייה. נגיעה ברכיב בפריט לדוגמה מסתירה או מחזירה אותו.",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ListMode.entries.forEach { m ->
                OutlinedButton(onClick = { editing = m }, modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(m.label)
                        Text(
                            "${features.tile(current.tile(m)).shown.size} מתוך ${TileElement.entries.size} רכיבים",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
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
            blocked = features.hiddenElements(),
            onEnableFeature = { vm.setFeatures(features.with(it, true)) },
        )
    }

    SettingsCard("סדר הרשימה") {
        SwitchRow("מוצרי קירור והקפאה בסוף הרשימה", current.chilledLast) { vm.setDisplay(current.copy(chilledLast = it)) }
        SwitchRow(
            "הצגת מוצרים שסומנו או נמחקו",
            current.showBought,
            "בתחתית הרשימה, מחוקים בקו. אותה הגדרה כמו בתפריט ⋮ של הרשימה.",
        ) { vm.setDisplay(current.copy(showBought = it)) }
    }
}

/** "מראה": light or dark, colours, the app's icon, and the order of the tabs. */
@Composable
private fun LookSettings(vm: AppViewModel) {
    SettingsCard("צבעים וסמל") {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DisplayCard(vm)
            PalettePicker(vm)
            AppIconPicker(vm)
        }
    }
    SettingsCard("סדר הלשוניות") { TabOrderSettings(vm) }
}

/** "התראות": צ'אט פוד (who is notified, and how it sounds) while it is on, and who hears about an item marked missing. */
@Composable
private fun NotifySettings(vm: AppViewModel) {
    val notifyMode by vm.chatNotifyMode.collectAsStateWithLifecycle()
    val loud by vm.loudMentions.collectAsStateWithLifecycle()
    val sound by vm.chatSound.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()
    if (app.necessityq.domain.Feature.CHAT in features) {
        SettingsCard(
            "צ'אט פוד",
            "כדי שלא כל בני הבית יקבלו התראה על כל הודעה: התראה מגיעה רק למי שתויג ב-@ ולמי שההודעה שלו צוטטה.",
        ) {
            app.necessityq.domain.ChatNotifyMode.entries.forEach { mode ->
                RadioRow("התראה: " + mode.label, notifyMode == mode) { vm.setChatNotifyMode(mode) }
            }
            SwitchRow("תיוג או ציטוט נשמעים גם כשהטלפון על שקט", loud) { vm.setLoudMentions(it) }
            SwitchRow("צליל להודעות שאינן תיוג (כשבחרתם \"כל הודעה\")", sound) { vm.setChatSound(it) }
        }
    } else {
        Text(
            "צ'אט פוד כבוי, ולכן אין לו התראות. אפשר להדליק אותו בלשונית \"יכולות\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
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
        // a tab turned off in "יכולות" keeps its place, greyed
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

/** An on / off setting: the whole row toggles it, with a line of explanation under the label when given. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** One of several choices: the whole row picks it. */
@Composable
private fun RadioRow(label: String, selected: Boolean, hint: String? = null, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(label)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SubTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, top = 8.dp))

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
