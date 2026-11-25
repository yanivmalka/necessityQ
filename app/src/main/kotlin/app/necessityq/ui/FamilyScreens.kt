package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import app.necessityq.ui.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.FamilyMember
import app.necessityq.domain.FlavorPref
import app.necessityq.domain.Gender
import app.necessityq.domain.Rating
import java.time.LocalDate
import java.util.UUID

private val avatarPalette = listOf(
    Color(0xFFFFE0B2), Color(0xFFC8E6C9), Color(0xFFBBDEFB), Color(0xFFF8BBD0),
    Color(0xFFD1C4E9), Color(0xFFB2EBF2), Color(0xFFFFF9C4), Color(0xFFFFCCBC),
)

/** Emoji icons a family member can choose from. */
val memberEmojis = listOf(
    "👨", "👩", "👦", "👧", "🧒", "👶", "👴", "👵", "🧑", "🧔",
    "🐻", "🦁", "🐱", "🐶", "🦊", "🐼", "🐸", "🦄", "🐢", "🐝",
    "⭐", "🌸", "⚽", "🎨", "🚀", "🎸", "📚", "🍓",
)

val hebrewMonths = listOf("ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני", "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר")

fun avatarColor(id: String): Color = avatarPalette[Math.floorMod(id.hashCode(), avatarPalette.size)]

/**
 * Round icon of a family member: the profile picture of the participant linked to them (so the family shows the
 * person as they appear among the participants), else their emoji, or the first letter of the name.
 */
@Composable
fun MemberAvatar(member: FamilyMember, size: Dp = 48.dp, selected: Boolean = false, modifier: Modifier = Modifier, photo: String? = null) {
    if (photo != null) {
        ProfileAvatar(
            member.name, photo, size = size,
            modifier = modifier.then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
        )
        return
    }
    Box(
        modifier
            .size(size)
            .background(avatarColor(member.id), CircleShape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val emoji = member.avatar
        Text(
            emoji ?: member.initial,
            fontSize = (size.value * if (emoji != null) 0.5f else 0.42f).sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3A3A3A),
        )
    }
}

/** Five stars (tap the current star again to clear) and "בכלל לא". */
@Composable
fun StarsRow(value: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier, starSize: Dp = 28.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val never = value == Rating.NEVER
        (1..Rating.MAX).forEach { n ->
            val on = !never && value != null && n <= value
            Icon(
                if (on) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "$n כוכבים",
                tint = if (on) Color(0xFFF5A623) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(starSize + 8.dp).clickable { onChange(if (value == n) null else n) }.padding(4.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        FilterChip(
            selected = never,
            onClick = { onChange(if (never) null else Rating.NEVER) },
            label = { Text("בכלל לא") },
            leadingIcon = { Icon(Icons.Default.Block, null, Modifier.size(16.dp)) },
        )
    }
}

/**
 * A family member's profile: icon, age (birth month and year), gender, salty/sweet preference,
 * strong dislikes, a linked app account (so they rate for themselves) and their star ratings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemberScreen(vm: AppViewModel, memberId: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val data by vm.meals.collectAsStateWithLifecycle()
    val participants by vm.members.collectAsStateWithLifecycle()
    val existing = memberId?.let { id -> data.members.firstOrNull { it.id == id } }
    val id = remember(memberId) { memberId ?: UUID.randomUUID().toString() }
    val today = remember { LocalDate.now() }

    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var avatar by remember(existing?.id) { mutableStateOf(existing?.avatar) }
    var year by remember(existing?.id) { mutableStateOf(existing?.birthYear?.toString().orEmpty()) }
    var month by remember(existing?.id) { mutableStateOf(existing?.birthMonth) }
    var gender by remember(existing?.id) { mutableStateOf(existing?.gender) }
    var flavor by remember(existing?.id) { mutableStateOf(existing?.flavor) }
    val dislikes = remember(existing?.id) { mutableStateListOf<String>().apply { addAll(existing?.dislikes.orEmpty()) } }
    var linked by remember(existing?.id) { mutableStateOf(existing?.linkedUserId) }
    var newDislike by remember { mutableStateOf("") }
    var monthMenu by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val yearValue = year.trim().toIntOrNull()?.takeIf { it in 1900..today.year }
    val draft = FamilyMember(
        id = id,
        name = name.trim(),
        avatar = avatar,
        birthYear = yearValue,
        birthMonth = month,
        gender = gender,
        flavor = flavor,
        dislikes = dislikes.toList(),
        linkedUserId = linked,
    )

    fun save() {
        if (draft.name.isBlank()) return
        vm.updateMeals { d ->
            // one family member per account
            val freed = if (draft.linkedUserId == null) d else d.copy(
                members = d.members.map { if (it.id != draft.id && it.linkedUserId == draft.linkedUserId) it.copy(linkedUserId = null) else it },
            )
            freed.withMember(draft)
        }
        // my own profile: my account takes the same name, so the household and צ'אט פוד agree with the family
        if (draft.linkedUserId != null && draft.linkedUserId == vm.me) vm.syncAccountName(draft.name)
        onBack()
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") } },
            title = { Text(if (existing == null) "בן/בת משפחה חדש/ה" else existing.name, fontWeight = FontWeight.ExtraBold) },
            actions = { TextButton(enabled = name.isNotBlank(), onClick = ::save) { Text("שמירה", fontWeight = FontWeight.Bold) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.fadingVerticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MemberAvatar(draft.copy(name = name.ifBlank { "?" }), size = 80.dp, photo = participants.firstOrNull { it.userId == linked }?.photo)
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("שם") }, singleLine = true, shape = NqShapes.Field)
                    draft.ageLabel(today)?.let { Text("גיל: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            Section("אייקון") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = avatar == null, onClick = { avatar = null }, label = { Text("אות ראשונה") })
                    memberEmojis.forEach { e ->
                        Box(
                            Modifier.size(44.dp)
                                .background(if (avatar == e) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clickable { avatar = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
            }

            Section("מין") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Gender.entries.forEach { g -> FilterChip(selected = gender == g, onClick = { gender = if (gender == g) null else g }, label = { Text(g.label) }) }
                }
            }

            Section("תאריך לידה (חודש ושנה)") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        OutlinedButton(onClick = { monthMenu = true }) { Text(month?.let { hebrewMonths[it - 1] } ?: "חודש") }
                        DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                            DropdownMenuItem(text = { Text("לא ידוע") }, onClick = { month = null; monthMenu = false })
                            hebrewMonths.forEachIndexed { i, m -> DropdownMenuItem(text = { Text(m) }, onClick = { month = i + 1; monthMenu = false }) }
                        }
                    }
                    OutlinedTextField(
                        year, { v -> year = v.filter { it.isDigit() }.take(4) },
                        label = { Text("שנה") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp),
                        isError = year.isNotEmpty() && yearValue == null,
                        trailingIcon = if (year.isNotEmpty()) { { IconButton(onClick = { year = "" }) { Icon(Icons.Default.Close, contentDescription = "מחיקת הטקסט") } } } else null,
                    )
                }
            }

            Section("מעדיף/ה") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlavorPref.entries.forEach { f -> FilterChip(selected = flavor == f, onClick = { flavor = if (flavor == f) null else f }, label = { Text(f.label) }) }
                }
            }

            Section("לא אוכל/ת בשום אופן") {
                Text(
                    "מאכלים עם המרכיבים האלה לא יוצעו כשהוא/היא אוכלים.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dislikes.forEach { d ->
                        InputChip(
                            selected = false,
                            onClick = { dislikes.remove(d) },
                            label = { Text(d) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "הסרה", Modifier.size(16.dp)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newDislike, { newDislike = it },
                        placeholder = { Text("למשל: פטריות") },
                        singleLine = true,
                        shape = NqShapes.Field,
                        modifier = Modifier.weight(1f),
                        trailingIcon = if (newDislike.isNotEmpty()) { { IconButton(onClick = { newDislike = "" }) { Icon(Icons.Default.Close, contentDescription = "מחיקת הטקסט") } } } else null,
                    )
                    IconButton(
                        enabled = newDislike.isNotBlank(),
                        onClick = {
                            val v = newDislike.trim()
                            if (dislikes.none { it == v }) dislikes.add(v)
                            newDislike = ""
                        },
                    ) { Icon(Icons.Default.Add, contentDescription = "הוספה") }
                }
            }

            if (vm.isShared) {
                Section("חשבון באפליקציה") {
                    val linkedName = participants.firstOrNull { it.userId == linked }?.let { it.email ?: it.displayName }
                    Text(
                        when {
                            linked == null -> "לא מקושר. ילד/ה עם חשבון Google יכול/ה להתחבר באפליקציה משלו/ה, להצטרף לבית בקוד ההצטרפות, ולבחור כאן \"זה אני\" — ואז לדרג מאכלים בעצמו/ה."
                            linked == vm.me -> "מקושר לחשבון שלך"
                            else -> "מקושר ל: ${linkedName ?: "משתתף בבית"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (linked != vm.me && data.memberFor(vm.me)?.id.let { it == null || it == id }) {
                            Button(onClick = { linked = vm.me }) { Icon(Icons.Default.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("זה אני") }
                        }
                        Box {
                            OutlinedButton(onClick = { accountMenu = true }) { Text("בחירת משתתף") }
                            DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                                participants.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(listOfNotNull(p.displayName, p.email).joinToString(" · ")) },
                                        onClick = { linked = p.userId; accountMenu = false },
                                    )
                                }
                                if (participants.isEmpty()) DropdownMenuItem(text = { Text("אין עוד משתתפים בבית") }, onClick = { accountMenu = false })
                            }
                        }
                        if (linked != null) TextButton(onClick = { linked = null }) { Text("ביטול קישור") }
                    }
                }
            }

            if (existing != null && data.dishes.isNotEmpty()) {
                Section("דירוג מאכלים") {
                    data.dishes.sortedBy { it.name }.forEach { dish ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(dish.name, fontWeight = FontWeight.Bold)
                            StarsRow(data.rating(existing.id, dish.id), onChange = { stars -> vm.updateMeals { it.withRating(existing.id, dish.id, stars) } }, starSize = 22.dp)
                        }
                        HorizontalDivider()
                    }
                }
            }

            Button(onClick = ::save, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("שמירה") }
            if (existing != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("הסרה מהמשפחה")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("להסיר את ${existing.name}?") },
            text = { Text("גם הדירוגים שלו/ה יימחקו.") },
            confirmButton = { TextButton(onClick = { vm.updateMeals { it.withoutMember(existing.id) }; confirmDelete = false; onBack() }) { Text("הסרה") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        content()
    }
}
