package com.celtaury.cura

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.celtaury.cura.core.InstantDeletePrefs
import com.celtaury.cura.core.hasMediaAccess
import com.celtaury.cura.core.mediaPermissions
import com.celtaury.cura.ui.theme.InterBlack
import kotlinx.coroutines.launch

/** One-shot flag: onboarding shows only on a truly first launch.
 *
 * D1.5: lives in its OWN prefs file (cura_onboarding), which backup rules
 * EXCLUDE — with the old shared cura_settings file, android:allowBackup
 * restored the flag on every reinstall and nobody ever saw onboarding.
 */
object OnboardingPrefs {
    private const val PREFS = "cura_onboarding"
    private const val KEY = "onboarded"

    fun done(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, true).apply()
    }
}

/**
 * Onboarding, UX v3 (Batch M): FOUR pages, short dense text, no Skip.
 *  0 welcome + a small language picker on top ("Clean your phone. No ads.")
 *  1 an ANIMATED swipe demo — both gestures play on loop, one caption
 *  2 the instant-delete toggle (same pref as Settings)
 *  3 the permission ask, with the usual "Not now" escape
 * Every page reads in five seconds — no jargon walls, no paragraph decks.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // Batch L: the instant-delete page flips the SAME pref as Settings —
    // load it once so the toggle shows the stored value.
    InstantDeletePrefs.load(context)
    val pagerState = rememberPagerState(pageCount = { 4 })

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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> SwipeDemoPage()
                2 -> InstantDeletePage()
                else -> PermissionPage()
            }
        }

        // dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { i ->
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

        if (pagerState.currentPage == 3) {
            if (!granted) {
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
                            (pagerState.currentPage + 1).coerceAtMost(3)
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

/**
 * Page 0 — welcome. The whole pitch is two lines ("Clean your phone. Cura
 * has no ads."), and the small language picker sits on top: a user who
 * doesn't read English fixes the language BEFORE reading anything else.
 * Picking applies the per-app locale immediately (the activity recreates).
 */
@Composable
private fun WelcomePage() {
    val cs = MaterialTheme.colorScheme
    val configuration = LocalConfiguration.current

    // effective language = explicit override if set, else what the system gives
    val effective = AppCompatDelegate.getApplicationLocales().get(0)?.language
        ?: configuration.locales.get(0)?.language ?: "en"

    Box(modifier = Modifier.fillMaxSize()) {
        // small language picker, top of the page
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            LanguageChip(
                label = stringResource(R.string.lang_english),
                selected = effective == "en",
                selectedContainer = cs.primaryContainer,
                selectedContent = cs.onPrimaryContainer,
                idleContainer = cs.surfaceVariant,
                idleContent = cs.onSurfaceVariant
            ) {
                if (effective != "en") {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags("en")
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            LanguageChip(
                label = stringResource(R.string.lang_indo),
                selected = effective == "in",
                selectedContainer = cs.primaryContainer,
                selectedContent = cs.onPrimaryContainer,
                idleContainer = cs.surfaceVariant,
                idleContent = cs.onSurfaceVariant
            ) {
                if (effective != "in") {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags("in")
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🌿", fontSize = 72.sp)
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.onb_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = InterBlack,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.onb_welcome_sub),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    selectedContainer: Color,
    selectedContent: Color,
    idleContainer: Color,
    idleContent: Color,
    onPick: () -> Unit
) {
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        color = if (selected) selectedContent else idleContent,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) selectedContainer else idleContainer)
            .clickable(onClick = onPick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * Page 1 — the swipe demo. A mock photo card plays BOTH gestures on loop:
 * it slides right with a green Keep stamp, comes back, slides left with a
 * red Clean stamp. One dense caption underneath; the "-> more" line points
 * to the tool for anyone who wants the long version. No image assets — the
 * mock photo is a themed gradient + outline icon ("template image", Batch M).
 */
@Composable
private fun SwipeDemoPage() {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.onb_demo_title),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = InterBlack,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onb_demo_body),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Spacer(Modifier.height(24.dp))
        SwipeDemoCard()
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.onb_demo_hint),
            fontSize = 13.sp,
            color = cs.onSurfaceVariant
        )
    }
}

@Composable
private fun SwipeDemoCard() {
    val cs = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "swipeDemo")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "swipeDemoT"
    )
    // one loop: slide right (keep) & back, then slide left (clean) & back
    val x = when {
        t < 0.38f -> (t / 0.38f) * 190f
        t < 0.50f -> (1f - (t - 0.38f) / 0.12f) * 190f
        t < 0.88f -> -((t - 0.50f) / 0.38f) * 190f
        else -> -(1f - (t - 0.88f) / 0.12f) * 190f
    }
    val keepOn = t > 0.10f && t < 0.46f
    val cleanOn = t > 0.58f && t < 0.94f

    Box(contentAlignment = Alignment.Center) {
        // two static sheets peeking behind — the deck look, in miniature
        for (depth in 2 downTo 1) {
            Box(
                modifier = Modifier
                    .width(206.dp)
                    .aspectRatio(0.72f)
                    .graphicsLayer {
                        val shrink = 1f - 0.06f * depth
                        scaleX = shrink
                        scaleY = shrink
                        translationY = 32f * depth
                        alpha = 1f - 0.3f * depth
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(cs.surfaceVariant)
            )
        }
        // the moving mock photo card
        Box(
            modifier = Modifier
                .width(206.dp)
                .aspectRatio(0.72f)
                .graphicsLayer {
                    translationX = x
                    rotationZ = (x / 24f).coerceIn(-8f, 8f)
                }
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(cs.primaryContainer, cs.tertiaryContainer)
                    )
                )
                .border(2.dp, cs.outlineVariant, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = cs.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(64.dp)
            )
        }
        // the two stamps — never visible at the same time
        StampBadge(
            visible = keepOn,
            container = cs.tertiaryContainer,
            content = cs.onTertiaryContainer,
            icon = Icons.Filled.CheckCircle,
            label = stringResource(R.string.common_btn_keep)
        )
        StampBadge(
            visible = cleanOn,
            container = cs.errorContainer,
            content = cs.onErrorContainer,
            icon = Icons.Filled.Delete,
            label = stringResource(R.string.common_btn_clean)
        )
    }
}

@Composable
private fun StampBadge(
    visible: Boolean,
    container: Color,
    content: Color,
    icon: ImageVector,
    label: String
) {
    // names dodge the GraphicsLayerScope members — `alpha` inside the
    // graphicsLayer lambda would otherwise shadow the outer state and the
    // stamp would never fade.
    val stampAlpha by animateFloatAsState(if (visible) 1f else 0f, label = "stampAlpha")
    val stampScale by animateFloatAsState(if (visible) 1f else 0.7f, label = "stampScale")
    Row(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = stampAlpha
                scaleX = stampScale
                scaleY = stampScale
            }
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = content, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/**
 * Page 2 — the instant-delete choice, ON the startup flow. Users who want
 * speed get it on day one; three short lines say exactly what changes.
 * Flips the same pref as Settings.
 */
@Composable
private fun InstantDeletePage() {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⚡", fontSize = 72.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.onb_instant_title),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = InterBlack,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onb_instant_body),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(22.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.onb_instant_toggle),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(
                            if (InstantDeletePrefs.enabled) R.string.onb_instant_on
                            else R.string.onb_instant_off
                        ),
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant
                    )
                }
                Switch(
                    checked = InstantDeletePrefs.enabled,
                    onCheckedChange = { InstantDeletePrefs.set(context, it) }
                )
            }
        }
    }
}

/**
 * Page 3 — the permission ask, last. Short dense body, Grant access button,
 * the usual "Not now" escape (the dashboard keeps a persistent grant banner).
 */
@Composable
private fun PermissionPage() {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📷", fontSize = 72.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.onb_perm_title),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = InterBlack,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onb_perm_body),
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
