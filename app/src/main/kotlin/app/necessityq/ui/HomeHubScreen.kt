package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import app.necessityq.ui.icons.Icons
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** One row of the "הבית" hub. */
class HubEntry(val title: String, val icon: ImageVector, val badge: String? = null, val onClick: () -> Unit)

/** The "הבית" tab: the household, then the pages that used to live in the side drawer, in grouped cards. */
@Composable
fun HomeHubScreen(
    vm: AppViewModel,
    householdName: String,
    modifier: Modifier = Modifier,
    entries: List<List<HubEntry>>,
) {
    val names by vm.memberNames.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .fadingVerticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("הבית", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(householdName, style = MaterialTheme.typography.titleLarge)
        }
        if (names.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(NqShapes.Tile).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, NqTheme.colors.hairline, NqShapes.Tile).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (members.isNotEmpty()) {
                    val now = rememberNowMillis()
                    MemberAvatars(
                        members.map { it.displayName }, size = 36.dp, max = 5, photos = members.map { it.photo },
                        active = members.map { it.userId == vm.me || it.isActive(now) },
                    )
                } else {
                    MemberAvatars(names.values.toList(), size = 36.dp, max = 5)
                }
                Text(
                    if (names.size == 1) "רק את/ה ברשימה כרגע" else "${names.size} בני בית משתפים את הרשימה",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        entries.forEach { group ->
            Column(
                Modifier.fillMaxWidth().clip(NqShapes.Tile).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, NqTheme.colors.hairline, NqShapes.Tile),
            ) {
                group.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = NqTheme.colors.hairline)
                    HubRow(entry)
                }
            }
        }
    }
}

@Composable
private fun HubRow(entry: HubEntry) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = entry.onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(entry.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        entry.badge?.let {
            Badge(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary) { Text(it) }
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

private val AvatarColors = listOf(Color(0xFF0B6E4F), Color(0xFFB45309), Color(0xFF3B5BDB), Color(0xFF7048E8), Color(0xFF1098AD))

/** Overlapping initials of household members; the first name sits on the right. */
@Composable
fun MemberAvatars(names: List<String>, size: Dp = 32.dp, max: Int = 3, photos: List<String?> = emptyList(), active: List<Boolean> = emptyList()) {
    val shown = names.take(max)
    val ring = MaterialTheme.colorScheme.background
    Row(Modifier.semantics { contentDescription = "${names.size} בני בית" }) {
        shown.forEachIndexed { index, name ->
            Box(Modifier.offset(x = if (index == 0) 0.dp else (-10 * index).dp)) {
            Box(
                Modifier
                    .size(size).clip(CircleShape).background(ring).padding(2.dp)
                    .clip(CircleShape).background(AvatarColors[index % AvatarColors.size]),
                contentAlignment = Alignment.Center,
            ) {
                val photo = photos.getOrNull(index)
                if (photo != null) ProfileAvatar(name, photo, size = size - 4.dp) else Text(
                    name.trim().take(1).ifEmpty { "?" },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.42f).sp,
                )
            }
            // in the app right now
            if (active.getOrNull(index) == true) ConnectedDot(Modifier.align(Alignment.TopEnd), size = (size.value * 0.36f).dp)
            }
        }
    }
}
