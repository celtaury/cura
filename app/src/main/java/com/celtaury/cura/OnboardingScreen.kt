package com.celtaury.cura

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celtaury.cura.core.hasMediaAccess
import com.celtaury.cura.core.mediaPermissions
import com.celtaury.cura.ui.theme.InterBlack
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

/** One-shot flag: onboarding shows only on a truly first launch. */
object OnboardingPrefs {
    private const val PREFS = "cura_settings"
    private const val KEY = "onboarded"

    fun done(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, true).apply()
    }
}

/**
 * Onboarding, UX v2: intro → swipe tutorial → permission.
 * "Not now" skips gracefully — the dashboard keeps a persistent grant banner.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    var granted by remember { mutableStateOf(hasMediaAccess(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        granted = hasMediaAccess(context)
        if (granted) {
            OnboardingPrefs.markDone(context)
            onDone()
        }
    }

    fun finish() {
        OnboardingPrefs.markDone(context)
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { finish() }) { Text(stringResource(R.string.onb_skip)) }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    emoji = "🌿",
                    title = stringResource(R.string.onb_1_title),
                    body = stringResource(R.string.onb_1_body)
                )
                1 -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            stringResource(R.string.onb_gesture),
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = InterBlack,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(22.dp))
                        SwipeLegendCard(
                            emoji = "🛡️",
                            title = stringResource(R.string.onb_2_title),
                            body = stringResource(R.string.onb_2_body),
                            container = cs.tertiaryContainer,
                            onContainer = cs.onTertiaryContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        SwipeLegendCard(
                            emoji = "🧹",
                            title = stringResource(R.string.onb_3_title),
                            body = stringResource(R.string.onb_3_body),
                            container = cs.errorContainer,
                            onContainer = cs.onErrorContainer
                        )
                    }
                }
                else -> OnboardingPage(
                    emoji = "🛡️",
                    title = stringResource(R.string.onb_4_title),
                    body = stringResource(R.string.onb_4_body)
                )
            }
        }

        // dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { i ->
                val active = pagerState.currentPage == i
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (active) cs.primary else cs.outlineVariant)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        if (pagerState.currentPage == 2) {
            if (!granted) {
                Text(
                    stringResource(R.string.onb_perm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { launcher.launch(mediaPermissions()) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(stringResource(R.string.onb_grant), fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = { finish() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.onb_not_now)) }
            } else {
                Button(
                    onClick = { finish() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(stringResource(R.string.onb_go), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Button(
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            (pagerState.currentPage + 1).coerceAtMost(2)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(R.string.onb_next), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun OnboardingPage(emoji: String, title: String, body: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 72.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = InterBlack,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun SwipeLegendCard(
    emoji: String,
    title: String,
    body: String,
    container: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 34.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = onContainer, fontSize = 17.sp)
                Spacer(Modifier.height(3.dp))
                Text(body, fontSize = 13.sp, color = onContainer)
            }
        }
    }
}
