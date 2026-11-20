package app.necessityq.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.necessityq.R
import app.necessityq.ui.AppViewModel.StartupStep
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** After this long a "continue" button appears, and after [GIVE_UP_MS] the app opens with whatever has loaded. */
private const val OFFER_SKIP_MS = 4_000L
private const val GIVE_UP_MS = 15_000L
private const val READY_PAUSE_MS = 650L

/** One round of the basket animation: the three items drop in one after another, sit a moment, then fade for the next round. */
private const val CYCLE_MS = 2_600f
private val DROP_STARTS = listOf(0f, 350f, 700f)
private const val FALL_MS = 380f
private const val BOUNCE_MS = 140f
private const val FADE_MS = 300f
/** How high above its place an item starts (icon units, of 108): above the visible part of the picture. */
private const val DROP_HEIGHT = 60f

/**
 * The opening screen: the basket with groceries jumping in, and the three things being loaded (account, list,
 * prices), each ticked as it arrives. [onDone] is called once everything is in, on "continue", or after
 * [GIVE_UP_MS] (a slow or missing connection never locks the user out).
 */
@Composable
fun StartupScreen(step: StartupStep, onDone: () -> Unit) {
    var offerSkip by remember { mutableStateOf(false) }
    LaunchedEffect(step) {
        if (step == StartupStep.READY) {
            delay(READY_PAUSE_MS) // the tick badge pops onto the basket, then the app opens
            onDone()
        }
    }
    LaunchedEffect(Unit) {
        delay(OFFER_SKIP_MS)
        offerSkip = true
        delay(GIVE_UP_MS - OFFER_SKIP_MS)
        onDone()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            BasketLoading(step == StartupStep.READY, 128.dp)
            Spacer(Modifier.height(20.dp))
            Text("necessityQ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "רשימת קניות שלומדת את הקצב של הבית",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StepRow("מתחברים לחשבון", StartupStep.ACCOUNT, step)
                StepRow("טוענים את הרשימה של הבית", StartupStep.LIST, step)
                StepRow("טוענים מחירים מהסניף", StartupStep.PRICES, step)
            }
            Spacer(Modifier.height(24.dp))
            // fades in after a few seconds; until then it keeps its place, so nothing jumps
            val skipAlpha by animateFloatAsState(if (offerSkip && step != StartupStep.READY) 1f else 0f, label = "skip")
            TextButton(onClick = onDone, enabled = skipAlpha > 0.5f, modifier = Modifier.height(48.dp).graphicsLayer { alpha = skipAlpha }) {
                Text("להמשיך בלי לחכות")
            }
        }
    }
}

/** One loading stage: ticked when done, a spinner while running, a faint dot while waiting its turn. */
@Composable
private fun StepRow(label: String, mine: StartupStep, current: StartupStep) {
    val done = current.ordinal > mine.ordinal
    val running = current == mine
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Box(
                    Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
                running -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else -> Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (done || running) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (running) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * The basket picture (the app's former icon) with its groceries jumping in one after another, over and over, while the
 * data loads; when everything is in ([ready]) the amber tick badge pops onto it. Drawn from layers that share the
 * 108-unit adaptive-icon grid: handle behind, the items, then the basket's front, so an item lands *inside* the basket.
 */
@Composable
private fun BasketLoading(ready: Boolean, size: Dp) {
    val t by rememberInfiniteTransition(label = "basket").animateFloat(
        initialValue = 0f, targetValue = CYCLE_MS,
        animationSpec = infiniteRepeatable(tween(CYCLE_MS.toInt(), easing = LinearEasing)), label = "t",
    )
    val badge by animateFloatAsState(
        if (ready) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "badge",
    )
    val unit = with(LocalDensity.current) { size.toPx() * 1.5f / 108f }
    // once ready the items stay in the basket instead of fading for another round
    val clock = if (ready) CYCLE_MS - FADE_MS - 1f else t
    val drops = DROP_STARTS.map { drop(clock - it) }
    val itemAlpha = roundAlpha(clock)
    // the basket gives a little as each item lands
    val give = DROP_STARTS.sumOf { landing(clock - it).toDouble() }.toFloat() * 1.5f

    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)), contentAlignment = Alignment.Center) {
        val layer = Modifier.requiredSize(size * 1.5f)
        Image(painterResource(R.drawable.ic_launcher_background), null, layer)
        val basket = layer.graphicsLayer { translationY = give * unit }
        Image(painterResource(R.drawable.startup_basket_back), null, basket)
        listOf(R.drawable.startup_item_greens, R.drawable.startup_item_milk, R.drawable.startup_item_bread).forEachIndexed { i, res ->
            Image(painterResource(res), null, layer.graphicsLayer { translationY = (drops[i] + give) * unit; alpha = itemAlpha })
        }
        Image(painterResource(R.drawable.startup_basket_front), null, basket)
        Image(
            painterResource(R.drawable.startup_badge), null,
            layer.graphicsLayer {
                scaleX = badge; scaleY = badge; alpha = badge.coerceIn(0f, 1f)
                // grow from the badge's own centre (70, 69.5 of 108)
                transformOrigin = TransformOrigin(70f / 108f, 69.5f / 108f)
            },
        )
    }
}

/** Offset (icon units, up is negative) of an item [local] ms after its drop began. */
private fun drop(local: Float): Float = when {
    local < 0f -> -DROP_HEIGHT
    local < FALL_MS -> -DROP_HEIGHT + DROP_HEIGHT * (local / FALL_MS) * (local / FALL_MS) // falling, faster and faster
    local < FALL_MS + BOUNCE_MS -> -5f * sin(PI.toFloat() * (local - FALL_MS) / BOUNCE_MS) // a small hop on landing
    else -> 0f
}

/** All items fade out together during the last [FADE_MS] of a round ([clock] is the round's time). */
private fun roundAlpha(clock: Float): Float = ((CYCLE_MS - clock) / FADE_MS).coerceIn(0f, 1f)

/** 0..1..0 while an item lands (the basket's give), 0 otherwise. */
private fun landing(local: Float): Float =
    if (local in FALL_MS..(FALL_MS + BOUNCE_MS)) sin(PI.toFloat() * (local - FALL_MS) / BOUNCE_MS) else 0f
