package app.necessityq.ui

import android.Manifest
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
import androidx.compose.material3.SnackbarHost
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
        bright -> lightColorScheme(
            primary = Color(0xFF0B5D1E),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC8F2CF),
            onPrimaryContainer = Color(0xFF00210A),
            secondary = Color(0xFFB34700),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFE0C7),
            onSecondaryContainer = Color(0xFF331100),
            tertiary = Color(0xFF0040A0),
            background = Color.White,
            onBackground = Color(0xFF0A0A0A),
            surface = Color.White,
            onSurface = Color(0xFF0A0A0A),
            surfaceVariant = Color(0xFFF0F0F0),
            onSurfaceVariant = Color(0xFF262626),
            surfaceContainerLow = Color(0xFFFAFAFA),
            outline = Color(0xFF3D3D3D),
            outlineVariant = Color(0xFF8A8A8A),
            error = Color(0xFFB00020),
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

@Composable
private fun Root(vm: AppViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

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
            } else {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val unread by vm.unreadTotal.collectAsStateWithLifecycle()
                // a conversation opened from the messages tab
                var openChat by remember { mutableStateOf<Pair<String, String>?>(null) }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("רשימה") })
                            NavigationBarItem(
                                tab == 1, { tab = 1 },
                                {
                                    BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, null)
                                    }
                                },
                                label = { Text("הודעות") },
                            )
                            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.History, null) }, label = { Text("היסטוריה") })
                            NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.Home, null) }, label = { Text("הבית") })
                        }
                    },
                ) { padding ->
                    val modifier = Modifier.padding(padding)
                    when (tab) {
                        0 -> ListScreen(vm, household.name, modifier)
                        1 -> MessagesScreen(vm, modifier, onOpen = { itemId, name -> openChat = itemId to name })
                        2 -> HistoryScreen(vm, modifier)
                        else -> HouseholdScreen(vm, household, s.account, s.userId, modifier)
                    }
                }
                openChat?.let { (itemId, name) ->
                    ItemSheet(vm, itemId = itemId, itemName = name, onDismiss = { openChat = null })
                }
            }
        }
    }
}
