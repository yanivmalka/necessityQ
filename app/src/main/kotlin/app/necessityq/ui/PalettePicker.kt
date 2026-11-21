package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** "ערכת צבעים": the palettes side by side, each as a small sample of its ground, card and main colour. */
@Composable
fun PalettePicker(vm: AppViewModel) {
    val current by vm.palette.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ערכת צבעים", style = MaterialTheme.typography.titleMedium)
        Text(
            "הצבע הראשי של האפליקציה ושל הרקע, בתצוגה בהירה וכהה. כתום של \"נגמר\" ומבצעים נשאר בכל הערכות.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppPalette.entries.forEach { p ->
                val selected = p == current
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.setPalette(p) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.border(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ).padding(4.dp),
                    ) {
                        // the ground, with the main colour and its light tint on it
                        Box(Modifier.size(52.dp).clip(CircleShape).background(p.background), contentAlignment = Alignment.Center) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(Modifier.size(16.dp).clip(CircleShape).background(p.primary))
                                Box(Modifier.size(16.dp).clip(CircleShape).background(p.container).border(1.dp, p.primary, CircleShape))
                            }
                        }
                    }
                    Text(
                        p.label + if (p == AppPalette.MARKET) "\n(ברירת מחדל)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
