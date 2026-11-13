package com.celtaury.cura

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.ThemePrefs
import com.celtaury.cura.ui.summary.SummaryScreen
import com.celtaury.cura.ui.deck.DeckFlowForRoute
import com.celtaury.cura.ui.theme.CuraTheme

/**
 * AppCompatActivity (D1.5): required for the per-app-language backport —
 * AppCompatDelegate applies + persists the in-app language on every API
 * level, and hands over to the framework implementation on Android 13+.
 * Needs a Theme.AppCompat descendant (see values/themes.xml).
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                CuraApp()
            }
        }
    }
}

@Composable
fun CuraApp() {
    val context = LocalContext.current
    // Warm the saved-counter once at start (it is read on the dashboard)
    // and load the theme override before the first frame picks a scheme.
    LaunchedEffect(Unit) {
        SavedStats.savedBytes(context)
        ThemePrefs.load(context)
    }

    val dark = when (ThemePrefs.mode) {
        ThemePrefs.LIGHT -> false
        ThemePrefs.DARK -> true
        else -> isSystemInDarkTheme()
    }

    CuraTheme(darkTheme = dark) {
        Surface(modifier = Modifier.fillMaxSize()) {
            CuraNav()
            // Batch M: the one-time donation prompt floats above every route.
            DonationPopupDialog()
        }
    }
}

@Composable
private fun CuraNav() {
    val context = LocalContext.current

    val backStack = remember { mutableStateListOf<String>() }
    // route survives activity recreation — switching the language in
    // Settings re-renders Settings in the new language instead of
    // bouncing back to the dashboard (D1.5).
    var route by rememberSaveable {
        mutableStateOf(if (OnboardingPrefs.done(context)) "dashboard" else "onboarding")
    }

    val navigate: (String) -> Unit = { next ->
        if (next != route) {
            backStack.add(route)
            route = next
        }
    }
    val goBack: () -> Unit = {
        val previous = backStack.removeLastOrNull()
        route = previous ?: "dashboard"
    }
    // back = pop the stack; enabled whenever there IS a stack, so back
    // from a REPLAYED onboarding (Settings → Replay intro) returns to
    // Settings. On a true first launch the stack is empty and back exits.
    BackHandler(enabled = backStack.isNotEmpty()) { goBack() }

    when {
        route == "onboarding" -> OnboardingScreen(onDone = {
            backStack.clear()
            route = "dashboard"
        })
        route == "quick_clean" -> QuickCleanScreen(onBack = goBack, onNavigate = navigate)
        route == "large_files" -> LargeFilesScreen(onBack = goBack)
        route == "screenshots" -> OldScreenshotsScreen(onBack = goBack)
        route == "suggest" -> SuggestScreen(onBack = goBack)
        route == "old_media" -> OldMediaScreen(onBack = goBack)
        route == "organize" -> OrganizeImagesScreen(onBack = goBack)
        route == "compress" -> CompressScreen(onBack = goBack)
        route == "social" -> SocialCleanerScreen(onBack = goBack, onNavigate = navigate)
        route == "keepsafe" -> KeepSafeScreen(onBack = goBack)
        route == "trash" -> TrashScreen(onBack = goBack)
        route == "more" -> MoreToolsScreen(onBack = goBack, onNavigate = navigate)
        route == "settings" -> SettingsScreen(onBack = goBack, onNavigate = navigate)
        route == "summary" -> SummaryScreen(onBack = goBack)
        route.startsWith("deck_") -> DeckFlowForRoute(route = route, onBack = goBack)
        else -> DashboardScreen(onNavigate = navigate)
    }
}
