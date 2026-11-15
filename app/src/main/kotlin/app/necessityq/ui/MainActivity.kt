package app.necessityq.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
        setContent { NecessityTheme { Root(vm) } }
    }
}

@Composable
fun NecessityTheme(content: @Composable () -> Unit) {
    val green = Color(0xFF2E7D32)
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF81C784), secondary = Color(0xFFFFB74D))
    } else {
        lightColorScheme(primary = green, secondary = Color(0xFFEF6C00), background = Color(0xFFF7FAF5))
    }
    // The app is Hebrew-first: force RTL regardless of the device language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

@Composable
private fun Root(vm: AppViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    when (val s = session) {
        Session.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Session.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message) }
        is Session.SignedIn -> {
            val household = s.household
            if (household == null) {
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                    OnboardingScreen(vm, Modifier.padding(padding))
                }
            } else {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("רשימה") })
                            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.History, null) }, label = { Text("היסטוריה") })
                            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Home, null) }, label = { Text("הבית") })
                        }
                    },
                ) { padding ->
                    val modifier = Modifier.padding(padding)
                    when (tab) {
                        0 -> ListScreen(vm, household.name, modifier)
                        1 -> HistoryScreen(vm, modifier)
                        else -> HouseholdScreen(vm, household, modifier)
                    }
                }
            }
        }
    }
}
