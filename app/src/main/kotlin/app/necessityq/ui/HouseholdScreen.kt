package app.necessityq.ui

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Stop
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.BuildConfig
import app.necessityq.domain.Account
import app.necessityq.domain.Household
import app.necessityq.domain.Member
import app.necessityq.domain.MemberRole
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import app.necessityq.platform.AvatarCodec
import app.necessityq.platform.ImageCodec
import kotlin.math.roundToInt

// ---------- Household: participants, account, updates ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdScreen(
    vm: AppViewModel,
    household: Household,
    account: Account,
    userId: String,
    modifier: Modifier = Modifier,
    /** Opens a family member's profile (null = a new member). */
    onOpenFamilyMember: (memberId: String?) -> Unit = {},
    /** Opens the account page ("הבית" ← "חשבון"). */
    onOpenAccount: () -> Unit = {},
) {
    val context = LocalContext.current
    val members by vm.members.collectAsStateWithLifecycle()
    val isOwner = household.isOwner(userId)
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmSample by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<Member?>(null) }
    var confirmNewCode by remember { mutableStateOf(false) }

    Column(modifier) {
        TopAppBar(
            navigationIcon = { BackNavButton() },
            title = { Text(household.name) },
            actions = {
                if (vm.supportsAccounts && isOwner) {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, contentDescription = "שינוי שם הבית") }
                }
            },
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // the account has its own page; here only a small row that leads to it
            if (vm.supportsAccounts) {
                Row(
                    Modifier.fillMaxWidth().clip(NqShapes.Panel).clickable(onClickLabel = "לדף החשבון", onClick = onOpenAccount)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "החשבון שלי" + (account.displayName?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text("לדף החשבון ›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            // the family's profiles (who eats, tastes, ratings) are managed here, with the participants
            FamilyCard(vm, onOpenMember = onOpenFamilyMember)
            if (vm.isShared) {
                SectionTitle("קוד הצטרפות")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(household.joinCode, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val text = "הצטרפו לרשימת הקניות של \"${household.name}\" ב-necessityQ עם הקוד: ${household.joinCode}"
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, "שיתוף קוד"))
                    }) { Icon(Icons.Default.Share, contentDescription = "שיתוף") }
                    if (isOwner) {
                        IconButton(onClick = { confirmNewCode = true }) {
                            Icon(Icons.Default.Refresh, contentDescription = "קוד חדש")
                        }
                    }
                }
                Text("כל שינוי ברשימה מופיע מיד אצל כל בני הבית, גם כשעובדים במקביל.", style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()
                SectionTitle("משתתפים (${household.members.size})")
                members.forEach { member ->
                    MemberRow(
                        member = member,
                        isMe = member.userId == userId,
                        canRemove = isOwner && member.userId != userId,
                        onRemove = { memberToRemove = member },
                    )
                }
                if (!isOwner) {
                    Text("רק מנהל הבית יכול להסיר משתתפים או להחליף את הקוד.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text("מצב מכשיר יחיד: הנתונים נשמרים במכשיר בלבד.", style = MaterialTheme.typography.bodyMedium)
            }

            if (vm.isShared) {
                HorizontalDivider()
                NotifyCard(vm, members, userId)
            }

            HorizontalDivider()
            SectionTitle("נתוני דוגמה")
            Text(
                "טוען לבית את היסטוריית הקניות מ-Microsoft To Do: מה שסומן כ\"נקנה\" הופך להיסטוריה, והשאר לרשימה הנוכחית. אפשר ללחוץ שוב בלי ליצור כפילויות.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = { confirmSample = true }) { Text("טעינת נתוני דוגמה") }

            if (vm.isShared) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { confirmLeave = true }) { Text("יציאה מהבית") }
            }
        }
    }

    // sample data changes the household's history for good, so it is explained before anything is loaded
    if (confirmSample) {
        AlertDialog(
            onDismissRequest = { confirmSample = false },
            title = { Text("לטעון נתוני דוגמה?") },
            text = {
                Text(
                    "ייטענו לבית היסטוריית קניות לדוגמה (מה שסומן שם כ\"נקנה\") ופריטים לרשימה הנוכחית (השאר). " +
                        "ההיסטוריה הזאת משפיעה מעכשיו על ההצעות (\"כנראה נגמר בקרוב\", \"קונים בדרך כלל\") ועל הסטטיסטיקה, " +
                        "כאילו הבית קנה את המוצרים האלה. טעינה חוזרת לא יוצרת כפילויות.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmSample = false; vm.importSample() }) { Text("טעינה") }
            },
            dismissButton = { TextButton(onClick = { confirmSample = false }) { Text("ביטול") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("לצאת מהבית?") },
            text = {
                Text(
                    if (isOwner && household.members.size > 1) "הרשימה וההיסטוריה יישארו אצל שאר בני הבית, והניהול יעבור למשתתף אחר."
                    else "הרשימה וההיסטוריה יישארו אצל שאר בני הבית.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leaveHousehold() }) { Text("יציאה") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("ביטול") } },
        )
    }
    if (renaming) {
        var name by remember { mutableStateOf(household.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("שם הבית") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { renaming = false; vm.renameHousehold(name) }) { Text("שמירה") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("ביטול") } },
        )
    }
    memberToRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("להסיר את ${member.displayName}?") },
            text = { Text("המשתתף לא יראה יותר את הרשימה. כדי שיחזור, יצטרך את קוד ההצטרפות.") },
            confirmButton = { TextButton(onClick = { memberToRemove = null; vm.removeMember(member.userId) }) { Text("הסרה") } },
            dismissButton = { TextButton(onClick = { memberToRemove = null }) { Text("ביטול") } },
        )
    }
    if (confirmNewCode) {
        AlertDialog(
            onDismissRequest = { confirmNewCode = false },
            title = { Text("להחליף את קוד ההצטרפות?") },
            text = { Text("הקוד הנוכחי יפסיק לעבוד. משתתפים קיימים לא מושפעים.") },
            confirmButton = { TextButton(onClick = { confirmNewCode = false; vm.regenerateJoinCode() }) { Text("החלפה") } },
            dismissButton = { TextButton(onClick = { confirmNewCode = false }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

@Composable
private fun MemberRow(member: Member, isMe: Boolean, canRemove: Boolean, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileAvatar(member.displayName, member.photo, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(member.displayName + if (isMe) " (את/ה)" else "")
            val sub = listOfNotNull(if (member.role == MemberRole.OWNER) "מנהל הבית" else null, member.email).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canRemove) {
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "הסרה") }
        }
    }
}

// ---------- Account (Google) ----------

@Composable
fun AccountCard(vm: AppViewModel, account: Account) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingName by remember { mutableStateOf(false) }
    var photoMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val photo = withContext(Dispatchers.IO) { ImageCodec.fromUri(context, uri)?.let(AvatarCodec::encode) }
            if (photo != null) vm.updatePhoto(photo) else vm.reportError("לא הצלחנו לקרוא את התמונה")
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("החשבון שלי")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // the picture: tap to change it
                Box {
                    Box(Modifier.clip(CircleShape).clickable { photoMenu = true }) {
                        ProfileAvatar(account.displayName ?: "?", account.photo, size = 64.dp)
                    }
                    Box(
                        Modifier.align(Alignment.BottomStart).size(22.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "שינוי תמונה", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp))
                    }
                    DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("בחירת תמונה") },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                            onClick = { photoMenu = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        )
                        if (account.customPhoto) {
                            DropdownMenuItem(
                                text = { Text(if (account.isAnonymous) "הסרת התמונה" else "התמונה מחשבון Google") },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = { photoMenu = false; vm.updatePhoto(null) },
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            account.displayName ?: if (account.isAnonymous) "אורח/ת" else "חשבון Google",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(onClick = { editingName = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "שינוי שם תצוגה", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (account.isAnonymous) {
                        Text("לא מחובר לחשבון Google", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        account.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        account.googleName?.takeIf { it != account.displayName }?.let {
                            Text("ב-Google: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (account.isAnonymous) {
                Text(
                    "כרגע את/ה מחובר/ת כאורח/ת. התחברו עם חשבון Google כדי שהשם, הכתובת והתמונה יגיעו מהחשבון, ושהבית לא יאבד כשמחליפים מכשיר או מתקינים מחדש. הרשימה וההיסטוריה נשמרות.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                requestGoogleIdToken(context)?.let { vm.signInWithGoogle(it) }
                            } catch (e: Exception) {
                                vm.reportError(e.message ?: "ההתחברות נכשלה")
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("התחברות עם Google") }
            } else {
                Text("השם והתמונה כאן הם מה שבני הבית רואים. אפשר לשנות אותם בלי לגעת בחשבון Google.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = vm::signOut) { Text("התנתקות") }
                Text("אחרי התנתקות המכשיר ממשיך כאורח חדש, ואפשר להתחבר שוב כדי לחזור לבית.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (editingName) {
        var name by remember { mutableStateOf(account.displayName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("שם תצוגה") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("כך יראו אותך בני הבית") })
                    account.googleName?.takeIf { it != name }?.let { g ->
                        TextButton(onClick = { name = g }) { Text("השם מ-Google: $g") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editingName = false; vm.updateDisplayName(name) }) { Text("שמירה") } },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("ביטול") } },
        )
    }
}

/** A participant's round picture, or the first letter of the name on a coloured circle when there is none. */
@Composable
fun ProfileAvatar(name: String, photo: String?, size: androidx.compose.ui.unit.Dp = 40.dp, modifier: Modifier = Modifier) {
    val bitmap = remember(photo) { photo?.let(AvatarCodec::decode)?.asImageBitmap() }
    if (bitmap != null) {
        Image(
            bitmap, contentDescription = name, contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier.size(size).background(avatarColor(name), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().firstOrNull()?.toString() ?: "?",
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF3A3A3A),
            )
        }
    }
}

// ---------- App updates (GitHub Releases) ----------

@Composable
fun UpdateCard(vm: AppViewModel) {
    val context = LocalContext.current
    val state by vm.update.collectAsStateWithLifecycle()
    val autoCheck by vm.autoCheckUpdates.collectAsStateWithLifecycle()
    val autoInstall by vm.autoInstallUpdates.collectAsStateWithLifecycle()

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("עדכוני אפליקציה")
            Text("גרסה מותקנת: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            when (val s = state) {
                UpdateState.Idle -> OutlinedButton(onClick = { vm.checkForUpdates(manual = true) }) { Text("בדיקת עדכון") }
                UpdateState.Checking -> {
                    Text("בודק…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                UpdateState.UpToDate -> {
                    Text("האפליקציה מעודכנת.")
                    TextButton(onClick = { vm.checkForUpdates(manual = true) }) { Text("בדיקה חוזרת") }
                }
                is UpdateState.Available -> {
                    Text("גרסה חדשה זמינה: ${s.release.versionName}", fontWeight = FontWeight.Bold)
                    if (s.release.sizeBytes > 0) Text("גודל: ${megabytes(s.release.sizeBytes)} MB", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = vm::downloadUpdate) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("הורדה ברקע")
                    }
                }
                is UpdateState.Downloading -> {
                    val shown by animateFloatAsState(s.progress, label = "updateProgress")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DownloadingIcon()
                        Column(Modifier.weight(1f)) {
                            Text("מוריד את ${s.release.versionName}… ${(s.progress * 100).roundToInt()}%", fontWeight = FontWeight.Bold)
                            if (s.release.sizeBytes > 0) {
                                Text(
                                    "${megabytes((s.release.sizeBytes * s.progress).toLong())} / ${megabytes(s.release.sizeBytes)} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        OutlinedButton(onClick = vm::stopDownload) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("עצירה")
                        }
                    }
                    LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "ההורדה ממשיכה ברקע — אפשר להמשיך להשתמש באפליקציה או לצאת ממנה.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is UpdateState.Stopped -> {
                    Text("ההורדה של ${s.release.versionName} נעצרה.")
                    Button(onClick = vm::downloadUpdate) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("הורדה מחדש")
                    }
                }
                is UpdateState.Ready -> {
                    Text("הגרסה ${s.release.versionName} הורדה ומוכנה להתקנה.")
                    Button(onClick = { startInstall(vm, context, s.file) }) { Text("התקנה") }
                }
                is UpdateState.Failed -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { vm.checkForUpdates(manual = true) }) { Text("ניסיון חוזר") }
                }
            }
            HorizontalDivider()
            UpdateOption("בדיקת עדכונים אוטומטית בפתיחת האפליקציה", autoCheck, vm::setAutoCheckUpdates)
            UpdateOption("התקנה אוטומטית בסיום ההורדה", autoInstall, vm::setAutoInstallUpdates)
            Text("העדכונים נלקחים מגרסאות האפליקציה ב-GitHub.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun UpdateOption(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

/** A download arrow that keeps dropping into its tray while the update downloads. */
@Composable
fun DownloadingIcon(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val transition = rememberInfiniteTransition(label = "downloading")
    val drop by transition.animateFloat(
        initialValue = -0.25f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "drop",
    )
    val fade by transition.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Restart),
        label = "fade",
    )
    Box(
        modifier.size(size + 12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Download, contentDescription = "מוריד",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size).graphicsLayer {
                translationY = drop * this.size.height
                alpha = fade
            },
        )
    }
}

/** Hands a downloaded update to the system installer, or first asks for the install permission. */
fun startInstall(vm: AppViewModel, context: android.content.Context, file: java.io.File) {
    val updater = vm.updater
    if (updater.canInstall()) {
        context.startActivity(updater.installIntent(file))
    } else {
        vm.reportError("יש לאשר לאפליקציה להתקין עדכונים, ואז ללחוץ שוב על \"התקנה\"")
        context.startActivity(updater.permissionIntent())
    }
}

private fun megabytes(bytes: Long): String = "${(bytes / 1_000_000.0 * 10).roundToInt() / 10.0}"

// ---------- display ----------

@Composable
fun DisplayCard(vm: AppViewModel) {
    val bright by vm.brightTheme.collectAsStateWithLifecycle()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("תצוגה בהירה במיוחד")
                Text("רקע לבן, טקסט כהה ובולט — קריא בסופר ובשמש. כבוי: לפי הגדרת הטלפון.", style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.Switch(checked = bright, onCheckedChange = vm::setBrightTheme)
        }
    }
}

// ---------- who is notified ----------

@Composable
private fun NotifyCard(vm: AppViewModel, members: List<Member>, userId: String) {
    val settings by vm.notifySettings.collectAsStateWithLifecycle()
    // nobody chosen yet = everyone
    val chosen = settings.missingRecipients.ifEmpty { members.map { it.userId }.toSet() }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("התראות על מוצר חסר")
            Text("מי יקבל התראה כשמישהו מסמן מוצר כחסר בקנייה הזו:", style = MaterialTheme.typography.bodySmall)
            members.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = m.userId in chosen,
                        onCheckedChange = { on ->
                            vm.setMissingRecipients(if (on) chosen + m.userId else chosen - m.userId)
                        },
                    )
                    Text(m.displayName + if (m.userId == userId) " (את/ה)" else "")
                }
            }
            Text(
                "ההתראות מגיעות כשהאפליקציה פתוחה או רצה ברקע. הודעות על מוצרים מגיעות לכל בני הבית.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
