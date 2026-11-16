package app.necessityq.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.necessityq.domain.ListMode
import kotlinx.coroutines.launch
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
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
            NecessityTheme(bright) { Root(vm) }
        }
    }
}

/**
 * Two looks:
 *  - bright (default): for supermarkets and sunlight — pure white, near-black text, saturated
 *    dark accents, bold large product names, strong outlines; never switches to dark.
 *  - system: the regular light/dark palette following the phone setting.
 */
@Composable
fun NecessityTheme(bright: Boolean, content: @Composable () -> Unit) {
    val colors = when {
        // modern, high-contrast: white tiles floating on a cool light-grey canvas, ink-dark text
        bright -> lightColorScheme(
            primary = Color(0xFF00875A),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD3F4E4),
            onPrimaryContainer = Color(0xFF00301F),
            secondary = Color(0xFFE8590C),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFE8D9),
            onSecondaryContainer = Color(0xFF3D1300),
            tertiary = Color(0xFF3B5BDB),
            onTertiary = Color.White,
            background = Color(0xFFF2F4F7),
            onBackground = Color(0xFF101828),
            surface = Color.White,
            onSurface = Color(0xFF101828),
            surfaceVariant = Color(0xFFEEF1F5),
            onSurfaceVariant = Color(0xFF344054),
            surfaceContainer = Color.White,
            surfaceContainerLow = Color(0xFFF9FAFB),
            surfaceContainerHigh = Color.White,
            outline = Color(0xFF667085),
            outlineVariant = Color(0xFF98A2B3),
            error = Color(0xFFD92D20),
        )
        isSystemInDarkTheme() -> darkColorScheme(primary = Color(0xFF81C784), secondary = Color(0xFFFFB74D))
        else -> lightColorScheme(primary = Color(0xFF2E7D32), secondary = Color(0xFFEF6C00), background = Color(0xFFF7FAF5))
    }
    val base = Typography()
    val typography = if (bright) {
        base.copy(
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
            bodyLarge = base.bodyLarge.copy(fontSize = 18.sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            bodySmall = base.bodySmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        )
    } else base
    // The app is Hebrew-first: force RTL regardless of the device language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

/** Product names on tiles: bold and large in every theme. */
val ProductNameStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)

/** Destinations of the navigation drawer. */
private enum class Dest(val title: String) {
    PLAN("הכנת רשימה"), SHOP("בקנייה"), MENU("תפריט"), MESSAGES("הודעות"), HISTORY("היסטוריה"),
    HOUSEHOLD("הבית ומשתתפים"), ACCOUNT("חשבון"), SETTINGS("הגדרות תצוגה"), APPEARANCE("מראה"),
    UPDATES("עדכונים"), ABOUT("אודות"),
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

    when (val s = session) {
        Session.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Session.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message) }
        is Session.SignedIn -> {
            val household = s.household
            if (household == null) {
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                    OnboardingScreen(vm, s.account, Modifier.padding(padding))
                }
                return
            }
            val mode by vm.mode.collectAsStateWithLifecycle()
            var dest by rememberSaveable { mutableStateOf(if (mode == ListMode.SHOP) Dest.SHOP else Dest.PLAN) }
            val unread by vm.unreadTotal.collectAsStateWithLifecycle()
            val update by vm.update.collectAsStateWithLifecycle()
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            var openChat by remember { mutableStateOf<Pair<String, String>?>(null) }

            fun go(d: Dest) {
                when (d) {
                    Dest.PLAN -> vm.setMode(ListMode.PLAN)
                    Dest.SHOP -> vm.setMode(ListMode.SHOP)
                    else -> Unit
                }
                dest = d
                scope.launch { drawer.close() }
            }

            BackHandler(enabled = drawer.isOpen || (dest != Dest.PLAN && dest != Dest.SHOP)) {
                if (drawer.isOpen) scope.launch { drawer.close() } else dest = if (mode == ListMode.SHOP) Dest.SHOP else Dest.PLAN
            }

            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                            Text(
                                household.name,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            )
                            DrawerItem("הכנת רשימה", Icons.AutoMirrored.Filled.List, dest == Dest.PLAN) { go(Dest.PLAN) }
                            DrawerItem("בקנייה", Icons.Default.ShoppingCart, dest == Dest.SHOP) { go(Dest.SHOP) }
                            DrawerItem("תפריט", Icons.Default.Restaurant, dest == Dest.MENU) { go(Dest.MENU) }
                            DrawerItem("הודעות", Icons.AutoMirrored.Filled.Chat, dest == Dest.MESSAGES, badge = unread.takeIf { it > 0 }?.toString()) { go(Dest.MESSAGES) }
                            DrawerItem("היסטוריה", Icons.Default.History, dest == Dest.HISTORY) { go(Dest.HISTORY) }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
                            DrawerItem("הבית ומשתתפים", Icons.Default.Group, dest == Dest.HOUSEHOLD) { go(Dest.HOUSEHOLD) }
                            DrawerItem("חשבון", Icons.Default.AccountCircle, dest == Dest.ACCOUNT) { go(Dest.ACCOUNT) }
                            DrawerItem("הגדרות תצוגה", Icons.Default.Tune, dest == Dest.SETTINGS) { go(Dest.SETTINGS) }
                            DrawerItem("מראה", Icons.Default.Palette, dest == Dest.APPEARANCE) { go(Dest.APPEARANCE) }
                            DrawerItem(
                                "עדכונים", Icons.Default.SystemUpdate, dest == Dest.UPDATES,
                                badge = if (update is UpdateState.Available) "חדש" else null,
                            ) { go(Dest.UPDATES) }
                            DrawerItem("אודות", Icons.Default.Info, dest == Dest.ABOUT) { go(Dest.ABOUT) }
                        }
                    }
                },
            ) {
                CompositionLocalProvider(
                    LocalOpenDrawer provides { scope.launch { drawer.open() } },
                    LocalUnreadTotal provides unread,
                ) {
                    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                        val modifier = Modifier.padding(padding)
                        when (dest) {
                            Dest.PLAN, Dest.SHOP -> ListScreen(vm, household.name, modifier, onOpenMessages = { go(Dest.MESSAGES) })
                            Dest.MENU -> MenuScreen(vm, modifier)
                            Dest.MESSAGES -> MessagesScreen(vm, modifier, onOpen = { itemId, name -> openChat = itemId to name })
                            Dest.HISTORY -> HistoryScreen(vm, modifier)
                            Dest.HOUSEHOLD -> HouseholdScreen(vm, household, s.account, s.userId, modifier)
                            Dest.ACCOUNT -> AccountScreen(vm, s.account, modifier)
                            Dest.SETTINGS -> DisplaySettingsScreen(vm, modifier)
                            Dest.APPEARANCE -> AppearanceScreen(vm, modifier)
                            Dest.UPDATES -> UpdatesScreen(vm, modifier)
                            Dest.ABOUT -> AboutScreen(modifier)
                        }
                    }
                }
            }
            openChat?.let { (itemId, name) ->
                ItemSheet(vm, itemId = itemId, itemName = name, onDismiss = { openChat = null })
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean, badge: String? = null, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        icon = { Icon(icon, null) },
        selected = selected,
        onClick = onClick,
        badge = badge?.let { { Badge { Text(it) } } },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
