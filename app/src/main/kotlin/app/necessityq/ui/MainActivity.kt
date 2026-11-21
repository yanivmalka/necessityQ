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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.SnackbarHostState
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
            }
        }
        openChatFrom(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openChatFrom(intent)
    }

    /** A tapped צ'אט פוד notification opens its conversation. */
    private fun openChatFrom(intent: android.content.Intent?) {
        val itemId = intent?.getStringExtra(app.necessityq.platform.MentionAlerter.EXTRA_ITEM_ID) ?: return
        val itemName = intent.getStringExtra(app.necessityq.platform.MentionAlerter.EXTRA_ITEM_NAME).orEmpty()
        intent.removeExtra(app.necessityq.platform.MentionAlerter.EXTRA_ITEM_ID)
        vm.requestOpenChat(itemId, itemName)
    }

    override fun onStart() {
        super.onStart()
        app.necessityq.platform.MentionAlerter.appVisible = true
        // Android often keeps the app in memory, so "opening" it is usually a return to the foreground
        vm.onForeground()
    }

    override fun onStop() {
        app.necessityq.platform.MentionAlerter.appVisible = false
        super.onStop()
    }
}

/** Screens of the app. The first four are the bottom-bar tabs; the rest open from the "הבית" hub. */
private enum class Dest(val title: String) {
    LIST("רשימה"), MENU("תפריט"), PRICES("מחירים"), HOME("הבית"),
    HOUSEHOLD("הבית ומשתתפים"), MESSAGES("צ'אט פוד"), HISTORY("היסטוריה"), ACCOUNT("חשבון"),
    SETTINGS("הגדרות תצוגה"), APPEARANCE("מראה"), UPDATES("עדכונים"), ABOUT("אודות"),
    FAMILY_MEMBER("בן/בת משפחה"),
}

/** The bottom-bar tabs, in order (the first is on the right): הבית on the right, the list on the left. */
private val TABS = listOf(Dest.HOME, Dest.MENU, Dest.PRICES, Dest.LIST)

/** The tab that is lit while a screen is open. */
private val Dest.tab: Dest
    get() = when (this) {
        Dest.LIST, Dest.MENU, Dest.PRICES -> this
        else -> Dest.HOME
    }

@Composable
private fun Root(vm: AppViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        vm.undo.collect { event ->
            val result = snackbar.showSnackbar(event.text, actionLabel = "ביטול", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) event.undo()
        }
    }

    // notifications (Android 13+) — asked once
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
    var started by rememberSaveable { mutableStateOf(false) }
    if (!started) {
        val step by vm.startupStep.collectAsStateWithLifecycle()
        StartupScreen(step) { started = true }
        return
    }

    when (val s = session) {
        Session.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Session.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message) }
        is Session.SignedIn -> {
            val household = s.household
            if (household == null) {
                Scaffold(snackbarHost = { SwipeableSnackbarHost(snackbar) }) { padding ->
                    OnboardingScreen(vm, s.account, Modifier.padding(padding))
                }
                return
            }
            val mode by vm.mode.collectAsStateWithLifecycle()
            var dest by rememberSaveable { mutableStateOf(Dest.LIST) }
            /** Where the back arrow of a secondary page leads; null on the tabs. */
            var backTo by rememberSaveable { mutableStateOf<Dest?>(null) }
            /** The family member whose profile is open ("" = a new member). */
            var familyMember by rememberSaveable { mutableStateOf("") }
            val unread by vm.unreadTotal.collectAsStateWithLifecycle()
            val update by vm.update.collectAsStateWithLifecycle()
            var openChat by remember { mutableStateOf<Pair<String, String>?>(null) }
            val chatRequest by vm.openChatRequest.collectAsStateWithLifecycle()
            LaunchedEffect(chatRequest) {
                chatRequest?.let { openChat = it; vm.consumeOpenChatRequest() }
            }

            val tabs = TABS
            val pager = rememberPagerState(initialPage = tabs.indexOf(dest.tab)) { tabs.size }
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
                val to = backTo ?: Dest.LIST
                // a page under "הבית" (the household, from a family member's profile) keeps its own way back
                go(to, from = if (to !in tabs && to.tab == Dest.HOME) Dest.HOME else null)
            }

            // a swipe between the tabs (as between WhatsApp's chats, updates, communities and calls) selects the tab it lands on
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.collect { page ->
                    if (dest in tabs && dest != tabs[page]) go(tabs[page])
                }
            }

            val shoppingOnList = dest == Dest.LIST && mode == ListMode.SHOP
            BackHandler(enabled = dest != Dest.LIST || shoppingOnList) {
                if (shoppingOnList) vm.setMode(ListMode.PLAN) else back()
            }

            val hubEntries = listOf(
                listOf(
                    HubEntry(Dest.HOUSEHOLD.title, Icons.Default.Group) { go(Dest.HOUSEHOLD, from = Dest.HOME) },
                    HubEntry(Dest.MESSAGES.title, Icons.AutoMirrored.Filled.Chat, badge = unread.takeIf { it > 0 }?.toString()) {
                        go(Dest.MESSAGES, from = Dest.HOME)
                    },
                    HubEntry(Dest.HISTORY.title, Icons.Default.History) { go(Dest.HISTORY, from = Dest.HOME) },
                ),
                listOf(
                    HubEntry(Dest.ACCOUNT.title, Icons.Default.AccountCircle) { go(Dest.ACCOUNT, from = Dest.HOME) },
                    HubEntry(Dest.SETTINGS.title, Icons.Default.Tune) { go(Dest.SETTINGS, from = Dest.HOME) },
                    HubEntry(Dest.APPEARANCE.title, Icons.Default.Palette) { go(Dest.APPEARANCE, from = Dest.HOME) },
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
                    snackbarHost = { SwipeableSnackbarHost(snackbar) },
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
                                    current = if (dest in tabs) tabs[pager.targetPage] else dest.tab,
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
                                    key = { tabs[it] },
                                    userScrollEnabled = !shoppingOnList && !(menuInnerPage && tabs[pager.currentPage] == Dest.MENU),
                                    modifier = Modifier.fillMaxSize(),
                                ) { index ->
                                    when (tabs[index]) {
                                        Dest.LIST -> ListScreen(
                                            vm, household.name, modifier,
                                            onOpenMessages = { go(Dest.MESSAGES, from = Dest.LIST) },
                                            onOpenDisplaySettings = { go(Dest.SETTINGS, from = Dest.LIST) },
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
                                )
                                Dest.ACCOUNT -> AccountScreen(vm, s.account, modifier)
                                Dest.SETTINGS -> DisplaySettingsScreen(vm, modifier)
                                Dest.APPEARANCE -> AppearanceScreen(vm, modifier)
                                Dest.UPDATES -> UpdatesScreen(vm, modifier)
                                Dest.ABOUT -> AboutScreen(modifier)
                                Dest.FAMILY_MEMBER -> MemberScreen(vm, familyMember.ifEmpty { null }, onBack = { back() }, modifier = modifier)
                                // the tabs are never a page of their own (they live in the pager)
                                Dest.LIST, Dest.MENU, Dest.PRICES, Dest.HOME -> Unit
                            }
                        }
                    }
                }
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

/** The four tabs, in [TABS] order: הבית · תפריט · מחירים · רשימה. The lit tab gets a tinted pill behind its icon. */
@Composable
private fun BottomBar(current: Dest, homeBadge: String?, onSelect: (Dest) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        val icons = mapOf(
            Dest.LIST to Icons.AutoMirrored.Filled.List,
            Dest.MENU to Icons.Default.Restaurant,
            Dest.PRICES to Icons.Default.Sell,
            Dest.HOME to Icons.Default.Home,
        )
        TABS.map { it to icons.getValue(it) }.forEach { (tab, icon) ->
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
