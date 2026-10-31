package com.celtaury.cura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.celtaury.cura.core.SavedStats
import com.celtaury.cura.ui.theme.CuraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CuraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CuraApp()
                }
            }
        }
    }
}

@Composable
fun CuraApp() {
    val context = LocalContext.current
    // Warm the saved-counter once at start (it is read on the dashboard)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        SavedStats.savedBytes(context)
    }

    val backStack = remember { mutableStateListOf<String>() }
    var route by remember { mutableStateOf("dashboard") }

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
    BackHandler(enabled = route != "dashboard" || backStack.isNotEmpty()) { goBack() }

    when (route) {
        "quick_clean" -> QuickCleanScreen(onBack = goBack)
        "large_files" -> LargeFilesScreen(onBack = goBack)
        "screenshots" -> OldScreenshotsScreen(onBack = goBack)
        "organize" -> OrganizeImagesScreen(onBack = goBack)
        "compress" -> CompressScreen(onBack = goBack)
        "social" -> SocialCleanerScreen(onBack = goBack)
        "keepsafe" -> KeepSafeScreen(onBack = goBack)
        "more" -> MoreToolsScreen(onBack = goBack, onNavigate = navigate)
        else -> DashboardScreen(onNavigate = navigate)
    }
}
