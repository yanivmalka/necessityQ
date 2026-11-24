package app.necessityq.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.platform.AppIcon
import app.necessityq.platform.AppIcons

/**
 * "סמל האפליקציה": the icon shapes to choose from, each in the app's colour palette, the current one ringed. Choosing
 * one switches the launcher icon; choosing another palette recolours it ([PalettePicker]).
 */
@Composable
fun AppIconPicker(vm: AppViewModel) {
    val context = LocalContext.current
    val palette by vm.palette.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf(AppIcons.current(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("סמל האפליקציה", style = MaterialTheme.typography.titleMedium)
        Text(
            "הסמל שמופיע במסך הבית של הטלפון, בצבע של ערכת הצבעים. אחרי ההחלפה ייתכן שיעברו כמה שניות עד שהסמל יתעדכן, " +
                "ובחלק מהטלפונים צריך להוסיף את קיצור הדרך למסך הבית מחדש.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon.entries.forEach { icon ->
                val selected = icon == current
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable {
                        if (!selected) { AppIcons.set(context, icon, palette); current = icon }
                    }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.border(
                            if (selected) 3.dp else 0.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                            CircleShape,
                        ).padding(4.dp),
                    ) { LauncherIcon(icon, palette, 56.dp) }
                    Text(
                        icon.label + if (icon == AppIcon.DEFAULT) "\n(ברירת מחדל)" else "",
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

/** A launcher icon as the phone shows it: [icon] in [palette], cropped to the visible 72 of 108 units. */
@Composable
fun LauncherIcon(icon: AppIcon, palette: AppPalette, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        val layer = Modifier.requiredSize(size * 1.5f)
        Image(painterResource(launcherBackground(palette)), null, layer)
        Image(painterResource(launcherForeground(icon, palette)), null, layer)
    }
}
