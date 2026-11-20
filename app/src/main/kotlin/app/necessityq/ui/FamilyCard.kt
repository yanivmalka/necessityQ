package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.FamilyMember
import java.time.LocalDate

/**
 * The family on the "הבית" tab: each member's profile (age, tastes, ratings), adding a member, and linking
 * my account to my profile. The menu uses these profiles (who eats, who likes a dish) but no longer shows
 * them itself; a recipe shows only what the family thinks of that recipe.
 */
@Composable
fun FamilyCard(vm: AppViewModel, onOpenMember: (memberId: String?) -> Unit) {
    val data by vm.meals.collectAsStateWithLifecycle()
    val participants by vm.members.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    var claimMe by remember { mutableStateOf(false) }
    // the participant and their family profile are one person: mine is linked (or made) as soon as I'm here
    LaunchedEffect(data, participants) { vm.ensureMyFamilyProfile() }

    Column(
        Modifier.fillMaxWidth().clip(NqShapes.Tile).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, NqTheme.colors.hairline, NqShapes.Tile).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("המשפחה", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(data.members, key = { it.id }) { m ->
                val isMe = m.linkedUserId != null && m.linkedUserId == vm.me
                val photo = m.linkedUserId?.let { uid -> participants.firstOrNull { it.userId == uid }?.photo }
                MemberBubble(m, isMe = isMe, age = m.ageLabel(today), photo = photo) { onOpenMember(m.id) }
            }
            item {
                Column(Modifier.clickable { onOpenMember(null) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                    Text("הוספה", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        val mine = data.memberFor(vm.me)
        when {
            mine != null -> AssistChip(
                onClick = { onOpenMember(mine.id) },
                label = { Text("הדירוגים שלי (${mine.name})") },
                leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(18.dp)) },
            )
            vm.isShared && data.members.any { it.linkedUserId == null } ->
                TextButton(onClick = { claimMe = true }) { Text("אני ברשימה? קישור לחשבון שלי") }
        }
    }

    if (claimMe) {
        AlertDialog(
            onDismissRequest = { claimMe = false },
            title = { Text("מי את/ה?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("בחירה מקשרת את החשבון שלך לבן/בת המשפחה, כדי שתוכל/י לדרג מאכלים בעצמך.", style = MaterialTheme.typography.bodySmall)
                    data.members.filter { it.linkedUserId == null }.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                vm.updateMeals { it.withMember(m.copy(linkedUserId = vm.me)) }
                                claimMe = false
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MemberAvatar(m, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(m.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { claimMe = false }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun MemberBubble(member: FamilyMember, isMe: Boolean, age: String?, photo: String?, onClick: () -> Unit) {
    Column(Modifier.width(64.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        MemberAvatar(member, size = 56.dp, selected = isMe, photo = photo)
        Text(member.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(age, if (isMe) "אני" else null).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
