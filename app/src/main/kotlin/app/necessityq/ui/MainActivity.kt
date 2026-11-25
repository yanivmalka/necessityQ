package app.necessityq.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.necessityq.domain.ListMode
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import app.necessityq.ui.icons.Icons
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.NecessityApp
import app.necessityq.domain.Session

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        AppViewModel.factory((application as NecessityApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val bright by vm.brightTheme.collectAsStateWithLifecycle()
            val palette by vm.palette.collectAsStateWithLifecycle()
            NecessityTheme(bright, palette) {
                // a themed ground for the whole app, so text everywhere (also in full-page dialogs such as a
                // צ'אט פוד conversation) takes the theme's ink: light on the dark theme, dark on the light one
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { Root(vm) }
                CrashReportDialog()
            }
        }
        openChatFrom(intent)
        joinFrom(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openChatFrom(intent)
        joinFrom(intent)
    }

    /** A join link (necessityq://join?code=…) offers to join that household, in one tap. */
    private fun joinFrom(intent: android.content.Intent?) {
        val code = app.necessityq.domain.JoinLinks.codeFrom(intent?.dataString) ?: return
        intent?.data = null
        vm.requestJoin(code)
    }

    /** A tapped צ'אט פוד notification opens its conversation. */
    private fun openChatFrom(intent: android.content.Intent?) {
        val itemId = intent?.getStringExtra(app.necessityq.platform.MentionAlerter.EXTRA_ITEM_ID) ?: return
        val itemName = intent.getStringExtra(app.necessityq.platform.MentionAlerter.EXTRA_ITEM_NAME).orEmpty()
        intent.removeExtra(app.necessityq.platform.MentionAlerter.EXTRA_ITEM_ID)
        vm.requestOpenChat(itemId, itemName)
    }

    /**
     * Set while the app itself opens another screen (a photo picker, sharing, a link, the installer): stopping for it
     * is not the user leaving the app. Every launch from the activity (also through the activity-result launchers)
     * passes through [startActivityForResult].
     */
    private var openingOther = false

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun startActivityForResult(intent: android.content.Intent, requestCode: Int, options: Bundle?) {
        openingOther = true
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onStart() {
        super.onStart()
        openingOther = false
        app.necessityq.platform.MentionAlerter.appVisible = true
        // Android often keeps the app in memory, so "opening" it is usually a return to the foreground
        vm.onForeground()
    }

    override fun onStop() {
        app.necessityq.platform.MentionAlerter.appVisible = false
        // a new app icon waits until the user leaves the app (home, recents, back): switching it closes the app
        if (!isChangingConfigurations && !openingOther) app.necessityq.platform.AppIcons.applyPending(this)
        super.onStop()
    }
}

/** Screens of the app. The first four are the bottom-bar tabs; the rest open from the "הבית" hub. */
private enum class Dest(val title: String) {
    LIST("רשימה"), MENU("תפריט"), PRICES("מחירים"), HOME("הבית"),
    HOUSEHOLD("בני הבית ושיתוף"), MESSAGES("צ'אט פוד"), HISTORY("היסטוריה"), ACCOUNT("חשבון"),
    SETTINGS("הגדרות"), APPEARANCE("הגדרות"), UPDATES("עדכונים"), ABOUT("אודות"),
    FAMILY_MEMBER("בן/בת משפחה"), HELP("עזרה: איך משתמשים"), SUGGESTIONS("הצעות שדחיתם"),
}

/** The bottom-bar tabs (their order, and the one the app opens on, are chosen in the settings). */
private val TABS = listOf(Dest.HOME, Dest.MENU, Dest.PRICES, Dest.LIST)

private val app.necessityq.domain.AppTab.dest: Dest
    get() = when (this) {
        app.necessityq.domain.AppTab.HOME -> Dest.HOME
        app.necessityq.domain.AppTab.MENU -> Dest.MENU
        app.necessityq.domain.AppTab.PRICES -> Dest.PRICES
        app.necessityq.domain.AppTab.LIST -> Dest.LIST
    }

/** The tab that is lit while a screen is open. */
private val Dest.tab: Dest
    get() = when (this) {
        Dest.LIST, Dest.MENU, Dest.PRICES -> this
        else -> Dest.HOME
    }

@Composable
private fun Root(vm: AppViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    // system messages lie in layers: a new one does not hide (or drop) the previous one and its "ביטול"
    val systemMessages = remember { SystemMessagesState() }
    LaunchedEffect(Unit) { vm.messages.collect { systemMessages.show(it) } }
    LaunchedEffect(Unit) { vm.undo.collect { event -> systemMessages.show(event.text, event.undo, event.label) } }

    // notifications (Android 13+) — asked in context (a second member, the first "חסר" or tag), once per run
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val permissionContext = LocalContext.current
    var notificationsAsked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.wantNotifications.collect {
            val granted = Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(
                permissionContext, Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted && !notificationsAsked) {
                notificationsAsked = true
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // "install automatically when the download finishes": collected only while the app is on screen,
    // since Android does not let a backgrounded app open the installer
    val pendingInstall by vm.pendingInstall.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(pendingInstall) {
        val file = pendingInstall ?: return@LaunchedEffect
        vm.consumePendingInstall()
        if (file.exists()) startInstall(vm, context, file)
    }

    // the opening screen stays until the list and its prices are in (once per launch; kept across rotation)
    // ("הדגמת התחלה" shows it again: [AppViewModel.introRun] is bumped)
    val introRun by vm.introRun.collectAsStateWithLifecycle()
    var started by rememberSaveable(introRun) { mutableStateOf(false) }
    if (!started) {
        val step by vm.startupStep.collectAsStateWithLifecycle()
        val palette by vm.palette.collectAsStateWithLifecycle()
        StartupScreen(step, palette) { started = true }
        return
    }

    when (val s = session) {
        Session.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Session.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message) }
        is Session.SignedIn -> {
            val household = s.household
            val replay by vm.firstRunReplay.collectAsStateWithLifecycle()
            if (household == null || replay == AppViewModel.FirstRunReplay.ONBOARDING) {
                Scaffold(snackbarHost = { SystemMessagesHost(systemMessages) }) { padding ->
                    // a replayed first run shows the welcome page without creating anything
                    OnboardingScreen(vm, s.account, Modifier.padding(padding), demo = household != null)
                }
                // opened from a join link: one tap joins (the code can still be typed by hand)
                val pendingJoin by vm.pendingJoin.collectAsStateWithLifecycle()
                pendingJoin?.let { code ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = vm::consumeJoin,
                        title = { Text("הצטרפות לבית") },
                        text = { Text("להצטרף לרשימת הקניות של הבית שהזמין אתכם (קוד $code)?") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { vm.joinHousehold(code); vm.consumeJoin() }) { Text("הצטרפות") }
                        },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = vm::consumeJoin) { Text("לא עכשיו") } },
                    )
                }
                return
            }
            val mode by vm.mode.collectAsStateWithLifecycle()
            // the app opens on the tab chosen in the settings (the list, unless changed)
            var dest by rememberSaveable { mutableStateOf(vm.startTab.value.dest) }
            /** Where the back arrow of a secondary page leads; null on the tabs. */
            var backTo by rememberSaveable { mutableStateOf<Dest?>(null) }
            // the settings tab to open on: notifications when coming from the household's notification row
            var settingsTab by rememberSaveable { mutableStateOf(SettingsTab.FEATURES) }
            /** The family member whose profile is open ("" = a new member). */
            var familyMember by rememberSaveable { mutableStateOf("") }
            val unread by vm.unreadTotal.collectAsStateWithLifecycle()
            val update by vm.update.collectAsStateWithLifecycle()
            var openChat by remember { mutableStateOf<Pair<String, String>?>(null) }
            val chatRequest by vm.openChatRequest.collectAsStateWithLifecycle()
            LaunchedEffect(chatRequest) {
                chatRequest?.let { openChat = it; vm.consumeOpenChatRequest() }
            }

            // the "תפריט" and "מחירים" tabs can be turned off (הגדרות ← יכולות): with both off, two tabs are left
            val features by vm.features.collectAsStateWithLifecycle()
            val tabOrder by vm.tabOrder.collectAsStateWithLifecycle()
            val tabs = remember(features, tabOrder) {
                tabOrder.map { it.dest }.filter { t ->
                    when (t) {
                        Dest.MENU -> app.necessityq.domain.Feature.MENU_TAB in features
                        Dest.PRICES -> app.necessityq.domain.Feature.PRICES_TAB in features
                        else -> true
                    }
                }
            }
            val startTab by vm.startTab.collectAsStateWithLifecycle()
            /** Where "back" from the other tabs leads, and where back leaves the app. */
            val startDest = startTab.dest.takeIf { it in tabs } ?: Dest.LIST
            val pager = rememberPagerState(initialPage = maxOf(0, tabs.indexOf(dest.tab))) { tabs.size }
            val scope = rememberCoroutineScope()
            /** A recipe or other page is open inside the menu tab: swiping there belongs to that page, not to the tabs. */
            var menuInnerPage by remember { mutableStateOf(false) }

            fun go(d: Dest, from: Dest? = null) {
                val page = tabs.indexOf(d)
                if (page >= 0 && pager.currentPage != page) {
                    // on the tabs the pager glides to the tab; from another page it is already in place when that page slides away
                    val onTabs = dest in tabs
                    scope.launch { if (onTabs) pager.animateScrollToPage(page) else pager.scrollToPage(page) }
                }
                dest = d
                backTo = from
            }

            fun back() {
                val to = backTo ?: startDest
                // a page under "הבית" (the household, from a family member's profile) keeps its own way back
                go(to, from = if (to !in tabs && to.tab == Dest.HOME) Dest.HOME else null)
            }

            // a swipe between the tabs (as between WhatsApp's chats, updates, communities and calls) selects the tab it lands on
            LaunchedEffect(pager, tabs) {
                snapshotFlow { pager.settledPage }.collect { page ->
                    val landed = tabs.getOrNull(page) ?: return@collect
                    if (dest in tabs && dest != landed) go(landed)
                }
            }
            // a tab that was turned off is not where the app stays
            LaunchedEffect(tabs) {
                if (dest in TABS && dest !in tabs) go(startDest)
            }

            val shoppingOnList = dest == Dest.LIST && mode == ListMode.SHOP
            BackHandler(enabled = dest != startDest || shoppingOnList) {
                if (shoppingOnList) vm.setMode(ListMode.PLAN) else back()
            }

            val declinedAdvice by vm.declinedAdvice.collectAsStateWithLifecycle()
            // which features are used: one nobody opens for a month is offered to be hidden ("הצעות")
            LaunchedEffect(dest) {
                when (dest) {
                    Dest.MENU -> vm.noteUsed(app.necessityq.domain.Feature.MENU_TAB)
                    Dest.PRICES -> vm.noteUsed(app.necessityq.domain.Feature.PRICES_TAB)
                    Dest.MESSAGES -> vm.noteUsed(app.necessityq.domain.Feature.CHAT)
                    else -> Unit
                }
            }
            LaunchedEffect(openChat) { if (openChat != null) vm.noteUsed(app.necessityq.domain.Feature.CHAT) }
            // after the first-run questions, a household that shops together goes straight on to inviting
            val openInvite by vm.openInvite.collectAsStateWithLifecycle()
            LaunchedEffect(openInvite) {
                if (openInvite) {
                    vm.consumeOpenInvite()
                    go(Dest.HOUSEHOLD, from = Dest.HOME)
                }
            }

            // a suggestion to pick a branch opens the "מחירים" tab — once it is among the tabs (it may have just been turned on)
            var pricesRequested by remember { mutableStateOf(false) }
            LaunchedEffect(pricesRequested, tabs) {
                if (pricesRequested && Dest.PRICES in tabs) {
                    pricesRequested = false
                    go(Dest.PRICES)
                }
            }

            val hubEntries = listOf(
                listOf(
                    HubEntry(Dest.HOUSEHOLD.title, Icons.Default.Group) { go(Dest.HOUSEHOLD, from = Dest.HOME) },
                    HubEntry(Dest.MESSAGES.title, Icons.AutoMirrored.Filled.Chat, badge = unread.takeIf { it > 0 }?.toString()) {
                        go(Dest.MESSAGES, from = Dest.HOME)
                    }.takeIf { app.necessityq.domain.Feature.CHAT in features },
                    HubEntry(Dest.HISTORY.title, Icons.Default.History) { go(Dest.HISTORY, from = Dest.HOME) },
                ).filterNotNull(),
                listOfNotNull(
                    HubEntry(Dest.HELP.title, Icons.Default.Info) { go(Dest.HELP, from = Dest.HOME) },
                    HubEntry(Dest.SUGGESTIONS.title, Icons.Default.AutoAwesome, badge = declinedAdvice.size.toString()) {
                        go(Dest.SUGGESTIONS, from = Dest.HOME)
                    }.takeIf { declinedAdvice.isNotEmpty() },
                ),
                listOf(
                    HubEntry(Dest.ACCOUNT.title, Icons.Default.AccountCircle) { go(Dest.ACCOUNT, from = Dest.HOME) },
                    HubEntry(Dest.SETTINGS.title, Icons.Default.Tune) { settingsTab = SettingsTab.FEATURES; go(Dest.SETTINGS, from = Dest.HOME) },
                ),
                listOf(
                    HubEntry(
                        Dest.UPDATES.title, Icons.Default.SystemUpdate,
                        badge = when (update) {
                            is UpdateState.Available, is UpdateState.Ready -> "חדש"
                            is UpdateState.Downloading -> "${((update as UpdateState.Downloading).progress * 100).toInt()}%"
                            else -> null
                        },
                    ) { go(Dest.UPDATES, from = Dest.HOME) },
                    HubEntry(Dest.ABOUT.title, Icons.Default.Info) { go(Dest.ABOUT, from = Dest.HOME) },
                ),
            )

            val display by vm.display.collectAsStateWithLifecycle()
            CompositionLocalProvider(
                LocalNavBack provides (if (backTo != null) ({ back() }) else null),
                LocalUnreadTotal provides unread,
                LocalWeightPriceUnit provides display.weightUnit,
            ) {
                Scaffold(
                    snackbarHost = { SystemMessagesHost(systemMessages) },
                    bottomBar = {
                        // shopping is a focused mode: the list takes the whole screen and has its own way back
                        if (!shoppingOnList) {
                            Column {
                                // the download runs in the background; a strip above the tabs follows it on every screen
                                AnimatedVisibility(
                                    visible = update is UpdateState.Downloading && dest != Dest.UPDATES,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut(),
                                ) {
                                    // keep the last progress on screen while the strip slides away
                                    var lastDownload by remember { mutableStateOf<UpdateState.Downloading?>(null) }
                                    (update as? UpdateState.Downloading)?.let { lastDownload = it }
                                    lastDownload?.let { d ->
                                        DownloadStrip(d, onOpen = { go(Dest.UPDATES, from = Dest.HOME) }, onStop = vm::stopDownload)
                                    }
                                }
                                BottomBar(
                                    // while swiping, the tab lights up as soon as the swipe would land on it
                                    current = if (dest in tabs) tabs.getOrNull(pager.targetPage) ?: dest else dest.tab,
                                    tabs = tabs,
                                    homeBadge = when {
                                        unread > 0 -> "$unread"
                                        update is UpdateState.Available || update is UpdateState.Ready -> "חדש"
                                        else -> null
                                    },
                                    onSelect = { go(it) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    val modifier = Modifier.padding(padding)
                    // null = the four tabs; any other page opens over them
                    AnimatedContent(
                        targetState = dest.takeIf { it !in tabs },
                        transitionSpec = { pageSlide(back = targetState == null || (initialState == Dest.FAMILY_MEMBER && targetState == Dest.HOUSEHOLD)) },
                        label = "page",
                    ) { page ->
                        // opaque, so the page sliding over another hides it
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            when (page) {
                                null -> HorizontalPager(
                                    state = pager,
                                    // while the tabs change (a tab turned on or off) the pager may ask for a page the list does not
                                    // have yet (or any more) for one frame: never index it blindly
                                    key = { tabs.getOrNull(it) ?: it },
                                    // no swiping away from the list: a quick swipe on a tile (meant to change tab)
                                    // would mark it bought; the tabs below move between pages there
                                    userScrollEnabled = tabs.getOrNull(pager.currentPage).let { onPage ->
                                        onPage != Dest.LIST && !(menuInnerPage && onPage == Dest.MENU)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                ) { index ->
                                    val tab = tabs.getOrNull(index) ?: return@HorizontalPager
                                    when (tab) {
                                        Dest.LIST -> ListScreen(
                                            vm, household.name, modifier,
                                            onOpenMessages = { go(Dest.MESSAGES, from = Dest.LIST) },
                                            onOpenDisplaySettings = { settingsTab = SettingsTab.LIST; go(Dest.SETTINGS, from = Dest.LIST) },
                                            onOpenPrices = { pricesRequested = true },
                                            onOpenHelp = { go(Dest.HELP, from = Dest.LIST) },
                                        )
                                        Dest.MENU -> MenuScreen(vm, modifier, onInnerPage = { menuInnerPage = it })
                                        Dest.PRICES -> PricesScreen(vm, modifier)
                                        else -> HomeHubScreen(
                                            vm, household.name, modifier, entries = hubEntries,
                                        )
                                    }
                                }
                                Dest.MESSAGES -> MessagesScreen(vm, modifier, onOpen = { itemId, name -> openChat = itemId to name })
                                Dest.HISTORY -> HistoryScreen(vm, modifier)
                                Dest.HOUSEHOLD -> HouseholdScreen(
                                    vm, household, s.account, s.userId, modifier,
                                    onOpenFamilyMember = { id -> familyMember = id.orEmpty(); go(Dest.FAMILY_MEMBER, from = Dest.HOUSEHOLD) },
                                    onOpenAccount = { go(Dest.ACCOUNT, from = Dest.HOUSEHOLD) },
                                    onOpenSettings = { settingsTab = SettingsTab.NOTIFY; go(Dest.SETTINGS, from = Dest.HOUSEHOLD) },
                                )
                                Dest.ACCOUNT -> AccountScreen(vm, s.account, modifier)
                                // "מראה" is a part of the settings' "האפליקציה" tab now
                                Dest.SETTINGS -> SettingsScreen(vm, modifier, settingsTab)
                                Dest.APPEARANCE -> SettingsScreen(vm, modifier, SettingsTab.LOOK)
                                Dest.UPDATES -> UpdatesScreen(vm, modifier)
                                Dest.ABOUT -> AboutScreen(modifier)
                                Dest.HELP -> HelpScreen(modifier)
                                Dest.SUGGESTIONS -> DeclinedSuggestionsScreen(vm, modifier, onOpenPrices = { pricesRequested = true })
                                Dest.FAMILY_MEMBER -> MemberScreen(vm, familyMember.ifEmpty { null }, onBack = { back() }, modifier = modifier)
                                // the tabs are never a page of their own (they live in the pager)
                                Dest.LIST, Dest.MENU, Dest.PRICES, Dest.HOME -> Unit
                            }
                        }
                    }
                }
            // a join link while already in a household: joining another one means leaving this one first
            val pendingJoin by vm.pendingJoin.collectAsStateWithLifecycle()
            pendingJoin?.let { code ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = vm::consumeJoin,
                    title = { Text(if (code == household.joinCode) "כבר בבית הזה" else "כבר יש לך בית") },
                    text = {
                        Text(
                            if (code == household.joinCode) "הקישור הוא לבית \"${household.name}\", שכבר הצטרפת אליו."
                            else "כדי להצטרף לבית אחר צריך קודם לצאת מ\"${household.name}\" (הבית ← בני הבית ושיתוף ← יציאה מהבית), ואז לפתוח שוב את הקישור.",
                        )
                    },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = vm::consumeJoin) { Text("הבנתי") } },
                )
            }

            // the first run asks a few questions about the household's habits; everything can change later in the settings
            val featuresChosen by vm.featuresChosen.collectAsStateWithLifecycle()
            if (!featuresChosen) FirstRunQuestions(onDone = vm::finishFirstRun)

            // a conversation opened from the messages or a notification: its own page, with the product's page a tap away
            var openProduct by remember { mutableStateOf<Pair<String, String>?>(null) }
            openChat?.let { (itemId, name) ->
                ChatFoodPage(
                    vm, itemId = itemId, itemName = name,
                    onDismiss = { openChat = null },
                    onOpenItem = { id, n -> openChat = id to n },
                    onOpenProduct = { openProduct = itemId to name },
                )
            }
            openProduct?.let { (itemId, name) ->
                ItemSheet(vm, itemId = itemId, itemName = name, onDismiss = { openProduct = null })
            }
            ScanSheet(vm)
            }
        }
    }
}

/** The background download, shown above the tabs on every screen: animated icon, progress, stop. */
@Composable
private fun DownloadStrip(state: UpdateState.Downloading, onOpen: () -> Unit, onStop: () -> Unit) {
    val shown by animateFloatAsState(state.progress, label = "stripProgress")
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DownloadingIcon(size = 18.dp)
                Text(
                    "מוריד עדכון ${state.release.versionName} ברקע… ${(state.progress * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "עצירת ההורדה", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth().height(3.dp))
        }
    }
}

/** The tabs, in [TABS] order: הבית · תפריט · מחירים · רשימה (those turned on). The lit tab gets a tinted pill behind its icon. */
@Composable
private fun BottomBar(current: Dest, tabs: List<Dest>, homeBadge: String?, onSelect: (Dest) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        val icons = mapOf(
            Dest.LIST to Icons.AutoMirrored.Filled.List,
            Dest.MENU to Icons.Default.Restaurant,
            Dest.PRICES to Icons.Default.Sell,
            Dest.HOME to Icons.Default.Home,
        )
        tabs.map { it to icons.getValue(it) }.forEach { (tab, icon) ->
            val selected = current == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    val badge = if (tab == Dest.HOME) homeBadge else null
                    BadgedBox(badge = { if (badge != null) Badge { Text(badge) } }) { Icon(icon, contentDescription = null) }
                },
                label = { Text(tab.title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
