package app.necessityq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.necessityq.domain.ItemStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private const val TIMELINE_DAYS = 30

/**
 * "הקצב של הבית" on the item page: how often it is bought, the usual amount and when it will run out,
 * as three plain numbers, then the purchases of the last 30 days on a line (today on the left in RTL).
 */
@Composable
fun RhythmCard(stats: ItemStats, purchaseMillis: List<Long>) {
    val today = remember { LocalDate.now() }
    val zone = remember { ZoneId.systemDefault() }
    val daysAgo = remember(purchaseMillis) {
        purchaseMillis.map { ChronoUnit.DAYS.between(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), today).toInt() }
            .filter { it in 0 until TIMELINE_DAYS }
            .toSet()
    }
    val dueIn = stats.nextDue?.let { ChronoUnit.DAYS.between(today, it) }
    val dueSoon = dueIn != null && dueIn <= 1
    val dueText = when {
        dueIn == null -> "—"
        dueIn < 0 -> "כנראה נגמר"
        dueIn == 0L -> "היום"
        dueIn == 1L -> "מחר"
        else -> "בעוד $dueIn ימים"
    }

    Column(
        Modifier.fillMaxWidth().clip(NqShapes.Tile).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, NqTheme.colors.hairline, NqShapes.Tile).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("הקצב של הבית", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RhythmTile(
                "קונים",
                stats.cadenceDays?.let { "כל ${it.roundToInt().coerceAtLeast(1)} ימים" } ?: "—",
                highlight = false,
                modifier = Modifier.weight(1f),
            )
            RhythmTile("בדרך כלל", stats.typicalQuantity?.format() ?: "—", highlight = false, modifier = Modifier.weight(1f))
            RhythmTile("ייגמר", dueText, highlight = dueSoon, modifier = Modifier.weight(1f))
        }

        val line = MaterialTheme.colorScheme.outlineVariant
        val dot = MaterialTheme.colorScheme.primary
        val ring = MaterialTheme.colorScheme.surface
        val due = MaterialTheme.colorScheme.secondary
        Canvas(
            Modifier.fillMaxWidth().height(24.dp).semantics {
                contentDescription = "${daysAgo.size} קניות ב-$TIMELINE_DAYS הימים האחרונים"
            },
        ) {
            val r = 6.dp.toPx()
            val y = size.height / 2
            val usable = size.width - 2 * r
            // RTL: today at the left edge, a month ago at the right edge
            fun x(daysBack: Int) = r + usable * daysBack / (TIMELINE_DAYS - 1)
            drawLine(line, Offset(r, y), Offset(size.width - r, y), strokeWidth = 2.dp.toPx())
            daysAgo.forEach { d ->
                drawCircle(ring, radius = r + 2.dp.toPx(), center = Offset(x(d), y))
                drawCircle(dot, radius = r, center = Offset(x(d), y))
            }
            if (dueSoon && 0 !in daysAgo) {
                drawCircle(ring, radius = r, center = Offset(x(0), y))
                drawCircle(
                    due, radius = r - 1.dp.toPx(), center = Offset(x(0), y),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("לפני חודש", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("היום", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RhythmTile(label: String, value: String, highlight: Boolean, modifier: Modifier = Modifier) {
    val bg: Color = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background
    val ink: Color = if (highlight) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier.clip(NqShapes.Panel).background(bg).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) ink else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
    }
}
