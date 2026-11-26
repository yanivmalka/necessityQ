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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    PermissionsCard()
    SampleDataCard(vm)
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

/** "נתוני דוגמה": the one place that fills the household with sample purchases and sample recipes. */
@Composable
private fun SampleDataCard(vm: AppViewModel) {
    var confirmSample by remember { mutableStateOf(false) }
    val meals by vm.meals.collectAsStateWithLifecycle()
    SettingsCard(
        "נתוני דוגמה",
        "למלא את הבית בנתונים לדוגמה כדי לראות איך האפליקציה נראית כשהיא כבר מכירה את הבית. טעינה חוזרת לא יוצרת כפילויות.",
    ) {
        OutlinedButton(onClick = { confirmSample = true }, modifier = Modifier.padding(horizontal = 4.dp)) { Text("טעינת קניות לדוגמה") }
        if (meals.dishes.isEmpty()) {
            OutlinedButton(
                onClick = { vm.updateMeals { it.copy(dishes = app.necessityq.domain.MealPlanner.sampleDishes) } },
                modifier = Modifier.padding(horizontal = 4.dp),
            ) { Text("טעינת מתכונים לדוגמה") }
        }
    }
    // sample data changes the household's history for good, so it is explained before anything is loaded
    if (confirmSample) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmSample = false },
            title = { Text("לטעון נתוני דוגמה?") },
            text = {
                Text(
                    "ייטענו לבית היסטוריית קניות לדוגמה (מה שסומן שם כ\"נקנה\") ופריטים לרשימה הנוכחית (השאר). " +
                        "ההיסטוריה הזאת משפיעה מעכשיו על ההצעות (\"כנראה נגמר בקרוב\", \"קונים בדרך כלל\") ועל הסטטיסטיקה, " +
                        "כאילו הבית קנה את המוצרים האלה. טעינה חוזרת לא יוצרת כפילויות.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmSample = false; vm.importSample() }) { Text("טעינה") } },
            dismissButton = { TextButton(onClick = { confirmSample = false }) { Text("ביטול") } },
        )
    }
}

@Composable
fun UpdatesScreen(vm: AppViewModel, modifier: Modifier = Modifier) = PageScaffold("עדכונים", modifier) {
    UpdateCard(vm)
}

/**
 * The four tabs of the settings screen, each about one idea, the most important first: what the app does (its
 * features, grouped by what they are for, with the settings of each under it), the list (a tap, a swipe, what each
 * item shows, the order), the look, and notifications.
 */
enum class SettingsTab(val label: String) {
    FEATURES("יכולות"),
    LIST("הרשימה"),
    LOOK("מראה"),
    NOTIFY("התראות"),
}

/**
 * The settings, in four tabs ([SettingsTab]) switched at the top of the page or by swiping sideways between them;
 * it opens on [initialTab]. Within a tab the settings are grouped in cards, the most important first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier, initialTab: SettingsTab = SettingsTab.FEATURES) {
    val pager = rememberPagerState(initialPage = initialTab.ordinal) { SettingsTab.entries.size }
    val scope = rememberCoroutineScope()
    // opened again on another tab (e.g. "התראות" from the household page) while the page is still here
    var appliedTab by rememberSaveable { mutableStateOf(initialTab) }
    LaunchedEffect(initialTab) {
        if (initialTab != appliedTab) {
            appliedTab = initialTab
            pager.scrollToPage(initialTab.ordinal)
        }
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { BackNavButton() },
            title = { Text("הגדרות · ${SettingsTab.entries[pager.targetPage].label}", fontWeight = FontWeight.ExtraBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Box(Modifier.padding(horizontal = 16.dp)) {
            // while swiping, the tab lights up as soon as the swipe would land on it
            SegmentedTabs(SettingsTab.entries.map { it.label }, pager.targetPage) { page ->
                scope.launch { pager.animateScrollToPage(page) }
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            key = { SettingsTab.entries[it].name },
        ) { page ->
            // each tab scrolls on its own, from its top
            Column(
                Modifier.fillMaxSize().fadingVerticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (SettingsTab.entries[page]) {
                    SettingsTab.FEATURES -> FeatureSettings(vm)
                    SettingsTab.LIST -> ListSettings(vm)
                    SettingsTab.LOOK -> LookSettings(vm)
                    SettingsTab.NOTIFY -> NotifySettings(vm)
                }
            }
        }
    }
}

/** "הרשימה": what a tap and a swipe on an item do, what each item shows, and the order of the list. */
@Composable
private fun ListSettings(vm: AppViewModel) {
    val current by vm.display.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()
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

    // what each tile shows, per stage: the stage is chosen with the same switch as on the list
    var tileMode by rememberSaveable { mutableStateOf(ListMode.PLAN) }
    var editing by remember { mutableStateOf<ListMode?>(null) }
    SettingsCard("מה מוצג על כל פריט ברשימה", "לכל שלב תצוגה משלו. נגיעה ברכיב בפריט לדוגמה מסתירה או מחזירה אותו.") {
        ModeSwitch(shopping = tileMode == ListMode.SHOP, onSelect = { tileMode = it }, modifier = Modifier.padding(vertical = 4.dp))
        val shown = features.tile(current.tile(tileMode), tileMode)
        OutlinedButton(onClick = { editing = tileMode }, modifier = Modifier.fillMaxWidth()) {
            Text("עריכה · ${tileMode.label} · ${shown.shown.size} מתוך ${TileElement.of(tileMode).size} רכיבים")
        }
    }
    editing?.let { m ->
        TileEditorSheet(
            mode = m,
            // as the list shows it: a part whose feature is off shows as hidden, and showing it turns the feature on
            layout = features.tile(current.tile(m), m),
            onSave = { vm.saveTile(m, it); editing = null },
            onDismiss = { editing = null },
        )
    }

    SwipeSettings(vm, current, features)

    SettingsCard("סדר הרשימה") {
        OptionRow("מוצרי קירור והקפאה בסוף הרשימה", current.chilledLast) { vm.setDisplay(current.copy(chilledLast = it)) }
    }

}

/**
 * "החלקה על פריט": what pulling a tile to the left and to the right does, per stage (the same switch as on the list).
 * While making the list a swipe removes an item or does nothing; in the shop it can also mark bought or missing, or
 * open the product's צ'אט פוד — and both sides may do the same. Choosing צ'אט פוד while it is off asks to turn it on.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SwipeSettings(vm: AppViewModel, current: app.necessityq.platform.DisplayOptions, features: app.necessityq.domain.Features) {
    var mode by rememberSaveable { mutableStateOf(ListMode.PLAN) }
    /** A swipe set to צ'אט פוד while it is off: (stage, actions) waiting for "להדליק?". */
    var askChat by remember { mutableStateOf<Pair<ListMode, app.necessityq.domain.SwipeActions>?>(null) }
    val chatOn = app.necessityq.domain.Feature.CHAT in features
    SettingsCard(
        "החלקה על פריט",
        "מה קורה כשמושכים אריח הצידה ומחזיקים עד שהטבעת מתמלאת (או דוחפים בכוח). אפשר לבחור לכל צד ולכל שלב.",
    ) {
        ModeSwitch(shopping = mode == ListMode.SHOP, onSelect = { mode = it }, modifier = Modifier.padding(vertical = 4.dp))
        val actions = current.swipe(mode)
        fun choose(next: app.necessityq.domain.SwipeActions) {
            val wantsChat = next.left == app.necessityq.domain.SwipeChoice.CHAT || next.right == app.necessityq.domain.SwipeChoice.CHAT
            if (wantsChat && !chatOn) askChat = mode to next else vm.setDisplay(current.withSwipe(mode, next))
        }
        listOf("החלקה שמאלה" to true, "החלקה ימינה" to false).forEach { (title, left) ->
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                app.necessityq.domain.SwipeChoice.of(mode).forEach { choice ->
                    val selected = (if (left) actions.left else actions.right) == choice
                    androidx.compose.material3.FilterChip(
                        selected = selected,
                        onClick = { choose(if (left) actions.copy(left = choice) else actions.copy(right = choice)) },
                        label = { Text(choice.label) },
                    )
                }
            }
        }
        if (mode == ListMode.PLAN) {
            Text(
                "בהכנת רשימה עוד לא קונים: החלקה מסירה פריט, פותחת צ'אט פוד, או לא עושה כלום. סימון נקנה וחסר — בקנייה, ושם אי אפשר למחוק פריט.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
    askChat?.let { (m, next) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askChat = null },
            title = { Text("צ'אט פוד כבוי") },
            text = { Text("כדי שהחלקה תפתח את צ'אט פוד של המוצר צריך להדליק את צ'אט פוד. להדליק אותו?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.setFeatures(vm.features.value.with(app.necessityq.domain.Feature.CHAT, true))
                    vm.setDisplay(vm.display.value.withSwipe(m, next))
                    askChat = null
                }) { Text("להדליק") }
            },
            dismissButton = { TextButton(onClick = { askChat = null }) { Text("לא עכשיו") } },
        )
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

/**
 * "יכולות": what the app does, grouped by what it is for ([app.necessityq.domain.FeatureGroup]). A feature of the whole
 * app has one switch; one of the list has a switch per stage, chosen with the same switch as on the list (a feature
 * that has nothing to do with a stage is not offered there). The "!" beside each name explains what it adds, and the
 * settings that belong to a feature sit under it — hidden while it is off, so nothing on the page does nothing.
 */
@Composable
private fun FeatureSettings(vm: AppViewModel) {
    val features by vm.features.collectAsStateWithLifecycle()
    val display by vm.display.collectAsStateWithLifecycle()
    val followed by vm.selectedPriceBranches.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(ListMode.PLAN) }
    SettingsCard(
        "מה האפליקציה עושה בשבילכם",
        "כיבוי רק מסתיר: האפליקציה ממשיכה ללמוד מכל קנייה ברקע, כך שכשמדליקים שוב הכול מוכן, עם כל ההיסטוריה. " +
            "נגיעה ב-! ליד השם מסבירה מה כל יכולת מוסיפה.",
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
                if (features == preset) {
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
        Text(
            "יכולות של הרשימה נקבעות לכל שלב בנפרד:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        ModeSwitch(shopping = mode == ListMode.SHOP, onSelect = { mode = it }, modifier = Modifier.padding(vertical = 4.dp))
    }

    app.necessityq.domain.FeatureGroup.entries.forEach { group ->
        val shown = app.necessityq.domain.Feature.entries.filter { it.group == group && it.appliesTo(mode) }
        if (shown.isEmpty()) return@forEach
        SettingsCard(group.label, group.hint) {
            shown.forEach { feature ->
                val appWide = feature.scope == app.necessityq.domain.FeatureScope.APP
                val on = features.isOn(feature, mode)
                FeatureRow(feature, on = on) { vm.setFeatures(if (appWide) features.with(feature, it) else features.with(feature, mode, it)) }
                if (on) FeatureSubSettings(vm, feature, display, followed.size)
            }
        }
    }
}

/** The settings of one feature, under it while it is on. */
@Composable
private fun FeatureSubSettings(
    vm: AppViewModel,
    feature: app.necessityq.domain.Feature,
    display: app.necessityq.platform.DisplayOptions,
    followed: Int,
) {
    val note: @Composable (String) -> Unit = { text ->
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 40.dp, end = 4.dp, bottom = 6.dp),
        )
    }
    when (feature) {
        app.necessityq.domain.Feature.RUNNING_LOW -> Column(Modifier.padding(start = 36.dp, bottom = 6.dp)) {
            Text("מה מופיע בכל הצעה", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            app.necessityq.platform.RecommendationDetail.entries.forEach { detail ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setDisplay(display.copy(recommendationDetail = detail)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = display.recommendationDetail == detail, onClick = { vm.setDisplay(display.copy(recommendationDetail = detail)) })
                    Text(detail.label)
                }
            }
            OptionRow("סימון מועדפים ★ בהצעות", display.showFavorite) { vm.setDisplay(display.copy(showFavorite = it)) }
        }
        app.necessityq.domain.Feature.PRICES -> Column(Modifier.padding(start = 36.dp, bottom = 6.dp)) {
            OptionRow("סכום משוער בתחתית הרשימה", display.showTotal) { vm.setDisplay(display.copy(showTotal = it)) }
            Text("מחיר של מוצר במשקל", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            Text(
                "ירקות, פירות, בשר ומוצרים שנמכרים במשקל: ברשימה, בדף המוצר ובהשוואה בין הסופרים.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            app.necessityq.domain.WeightPriceUnit.entries.forEach { unit ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setDisplay(display.copy(weightUnit = unit)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = display.weightUnit == unit, onClick = { vm.setDisplay(display.copy(weightUnit = unit)) })
                    Text("מחיר " + unit.title)
                }
            }
        }
        app.necessityq.domain.Feature.BRANCH_COMPARE ->
            CompareThreshold(display.compareMinShekels) { vm.setDisplay(display.copy(compareMinShekels = it)) }
        app.necessityq.domain.Feature.TRIP_PLAN ->
            if (followed < 2) note("כדי להשוות צריך לעקוב אחרי שני סופרים לפחות (בלשונית \"מחירים\"). כרגע: $followed.")
        app.necessityq.domain.Feature.CHAT -> note("מתי מקבלים התראה על הודעה: בלשונית \"התראות\".")
        else -> Unit
    }
}

/** One feature: the "!" that explains it (right of the name, in Hebrew), its name and its switch. */
@Composable
private fun FeatureRow(feature: app.necessityq.domain.Feature, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeatureInfoButton(feature)
        Text(feature.label, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = on, onCheckedChange = onChange)
    }
}

/**
 * A small "!" in a ring: a tap opens a floating box over the page with everything the feature adds; a tap anywhere
 * else (or back) closes it.
 */
@Composable
private fun FeatureInfoButton(feature: app.necessityq.domain.Feature) {
    var open by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    Box {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .clickable(onClickLabel = "מה ${feature.label} מוסיף") { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(20.dp).border(1.5.dp, primary, CircleShape), contentAlignment = Alignment.Center) {
                Text("!", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = primary)
            }
        }
        if (open) {
            val below = with(androidx.compose.ui.platform.LocalDensity.current) { 38.dp.roundToPx() }
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopStart,
                offset = androidx.compose.ui.unit.IntOffset(0, below),
                onDismissRequest = { open = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier.widthIn(max = 300.dp)
                        .shadow(10.dp, NqShapes.Panel)
                        .clip(NqShapes.Panel)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, NqTheme.colors.hairline, NqShapes.Panel)
                        .clickable { open = false }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(feature.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(feature.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        when (feature.scope) {
                            app.necessityq.domain.FeatureScope.APP -> "בכל האפליקציה"
                            app.necessityq.domain.FeatureScope.PLAN -> "בהכנת רשימה"
                            app.necessityq.domain.FeatureScope.SHOP -> "בקנייה"
                            app.necessityq.domain.FeatureScope.BOTH -> "בהכנת רשימה ובקנייה — לכל אחד מתג משלו"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** From which gap (₪) between the household's branches the comparison is shown: always, or 1–20 ₪. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CompareThreshold(current: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 40.dp, end = 4.dp, bottom = 6.dp)) {
        Text(
            "להציג השוואה כשההפרש בין הסופרים שלך הוא לפחות:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 1, 2, 3, 5, 10, 20).forEach { amount ->
                androidx.compose.material3.FilterChip(
                    selected = current == amount,
                    onClick = { onChange(amount) },
                    label = { Text(if (amount == 0) "כל הפרש בולט" else "$amount ₪") },
                )
            }
        }
    }
}

/** "התראות": צ'אט פוד (who is notified, and how it sounds) while it is on, and who hears about an item marked missing. */
@Composable
private fun NotifySettings(vm: AppViewModel) {
    val notifyMode by vm.chatNotifyMode.collectAsStateWithLifecycle()
    val loud by vm.loudMentions.collectAsStateWithLifecycle()
    val sound by vm.chatSound.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()
    if (app.necessityq.domain.Feature.CHAT !in features) {
        Text(
            "צ'אט פוד כבוי, ולכן אין לו התראות. אפשר להדליק אותו בלשונית \"יכולות\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    } else SettingsCard(
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

/** A permission the app can ask for from the account page. */
private enum class AppPermission(val label: String, val hint: String, val permissions: List<String>) {
    NOTIFICATIONS(
        "שליחת התראות",
        "הודעות בצ'אט פוד, מוצר שסומן חסר, ועדכון שהורד.",
        if (android.os.Build.VERSION.SDK_INT >= 33) listOf(android.Manifest.permission.POST_NOTIFICATIONS) else emptyList(),
    ),
    LOCATION(
        "מיקום",
        "סופרים קרובים במחירים, וזיהוי שאתם בסופר כדי לעבור לקנייה בנגיעה.",
        listOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION),
    ),
    MICROPHONE(
        "מיקרופון",
        "הקלטת הודעות קוליות בצ'אט פוד.",
        listOf(android.Manifest.permission.RECORD_AUDIO),
    ),
    ;

    fun granted(context: android.content.Context): Boolean = when (this) {
        NOTIFICATIONS -> androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        else -> permissions.any {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * "הרשאות": notifications, location and the microphone, each with a switch showing whether it is allowed. Turning one
 * on asks Android; one that Android no longer asks about (refused twice), and turning one off, open the app's page
 * in the phone's settings. The switches are read again whenever the page comes back to the screen.
 */
@Composable
private fun PermissionsCard() {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { tick++ }
    /** Permissions Android refused without asking: a tap opens the phone's settings instead. */
    var blocked by remember { mutableStateOf(emptySet<AppPermission>()) }
    var asking by remember { mutableStateOf<AppPermission?>(null) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val p = asking ?: return@rememberLauncherForActivityResult
        asking = null
        tick++
        val activity = context.findActivity()
        val refused = result.isNotEmpty() && result.values.none { it }
        if (refused && activity != null && p.permissions.none { androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }) {
            blocked = blocked + p
        }
    }
    fun openSettings(p: AppPermission) {
        val intent = if (p == AppPermission.NOTIFICATIONS && android.os.Build.VERSION.SDK_INT >= 26) {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }
        runCatching { context.startActivity(intent) }
    }
    SettingsCard("הרשאות", "מה האפליקציה רשאית לעשות בטלפון. אפשר להדליק מכאן; כיבוי נעשה בהגדרות הטלפון, שנפתחות בנגיעה.") {
        AppPermission.entries.forEach { p ->
            val granted = remember(tick) { p.granted(context) }
            fun toggle() {
                when {
                    granted -> openSettings(p)
                    p.permissions.isEmpty() || p in blocked -> openSettings(p)
                    else -> { asking = p; launcher.launch(p.permissions.toTypedArray()) }
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { toggle() }.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.label)
                    Text(
                        if (!granted && p in blocked) "נחסם — אפשר להדליק בהגדרות הטלפון (נגיעה פותחת אותן)" else p.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(checked = granted, onCheckedChange = { toggle() })
            }
        }
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

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // on / off settings are switches all over the app (several choices are radio buttons, picking people is checkboxes)
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
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
    Text("• בקנייה — איסוף, סדר איסוף לפי סופר, חסרים והתייעצות על תחליפים.")
    Text("• תפריט — בני משפחה, מאכלים אהובים ותפריט שבועי שממלא את הרשימה.")
    TextButton(onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yanivmalka/necessityQ")))
    }) { Text("קוד המקור ב-GitHub") }
}
