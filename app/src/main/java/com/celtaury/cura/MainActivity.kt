package com.celtaury.cura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.core.ThemePrefs
import com.celtaury.cura.ui.browse.BrowseGalleryScreen
import com.celtaury.cura.ui.deck.DeckFlowForRoute
import com.celtaury.cura.ui.theme.CuraTheme

class MainActivity : ComponentActivity() {
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
        }
    }
}

@Composable
private fun CuraNav() {
    val context = LocalContext.current

    val backStack = remember { mutableStateListOf<String>() }
    var route by remember {
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
    BackHandler(enabled = route != "dashboard" && route != "onboarding") { goBack() }

    when {
        route == "onboarding" -> OnboardingScreen(onDone = { route = "dashboard" })
        route == "quick_clean" -> QuickCleanScreen(onBack = goBack, onNavigate = navigate)
        route == "large_files" -> LargeFilesScreen(onBack = goBack)
        route == "screenshots" -> OldScreenshotsScreen(onBack = goBack)
        route == "suggest" -> SuggestScreen(onBack = goBack)
        route == "old_media" -> OldMediaScreen(onBack = goBack)
        route == "organize" -> OrganizeImagesScreen(onBack = goBack)
        route == "compress" -> CompressScreen(onBack = goBack)
        route == "social" -> SocialCleanerScreen(onBack = goBack, onNavigate = navigate)
        route == "keepsafe" -> KeepSafeScreen(onBack = goBack)
        route == "more" -> MoreToolsScreen(onBack = goBack, onNavigate = navigate)
        route == "settings" -> SettingsScreen(onBack = goBack, onNavigate = navigate)
        route == "browse_gallery" -> BrowseGalleryScreen(onBack = goBack)
        route.startsWith("deck_") -> DeckFlowForRoute(route = route, onBack = goBack)
        else -> DashboardScreen(onNavigate = navigate)
    }
}
