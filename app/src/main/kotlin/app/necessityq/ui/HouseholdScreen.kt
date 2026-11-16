package app.necessityq.ui

import android.content.Intent
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
) {
    val context = LocalContext.current
    val members by vm.members.collectAsStateWithLifecycle()
    val isOwner = household.isOwner(userId)
    var confirmLeave by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<Member?>(null) }
    var confirmNewCode by remember { mutableStateOf(false) }

    Column(modifier) {
        TopAppBar(
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

            if (vm.supportsAccounts) {
                HorizontalDivider()
                AccountCard(vm, account)
            }

            HorizontalDivider()
            UpdateCard(vm)

            HorizontalDivider()
            SectionTitle("נתוני דוגמה")
            Text(
                "טוען לבית את היסטוריית הקניות מ-Microsoft To Do: מה שסומן כ\"נקנה\" הופך להיסטוריה, והשאר לרשימה הנוכחית. אפשר ללחוץ שוב בלי ליצור כפילויות.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = vm::importSample) { Text("טעינת נתוני דוגמה") }

            if (vm.isShared) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { confirmLeave = true }) { Text("יציאה מהבית") }
            }
        }
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
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(member.displayName.trim().firstOrNull()?.toString() ?: "?", style = MaterialTheme.typography.titleMedium)
        }
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
    var busy by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("החשבון שלי")
            if (account.isAnonymous) {
                Text(
                    "כרגע את/ה מחובר/ת כאורח/ת. כדי לא לאבד את הבית כשמחליפים מכשיר או מתקינים מחדש, התחברו עם חשבון Google. הרשימה וההיסטוריה נשמרות.",
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
                TextButton(onClick = { editingName = true }) { Text("קביעת שם תצוגה") }
            } else {
                Text(account.displayName ?: "חשבון Google", style = MaterialTheme.typography.titleMedium)
                account.email?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editingName = true }) { Text("שינוי שם תצוגה") }
                    OutlinedButton(onClick = vm::signOut) { Text("התנתקות") }
                }
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
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("כך יראו אותך בני הבית") })
            },
            confirmButton = { TextButton(onClick = { editingName = false; vm.updateDisplayName(name) }) { Text("שמירה") } },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("ביטול") } },
        )
    }
}

// ---------- App updates (GitHub Releases) ----------

@Composable
fun UpdateCard(vm: AppViewModel) {
    val context = LocalContext.current
    val state by vm.update.collectAsStateWithLifecycle()

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    if (s.release.sizeBytes > 0) Text("גודל: ${(s.release.sizeBytes / 1_000_000.0 * 10).roundToInt() / 10.0} MB", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = vm::downloadUpdate) { Text("הורדה") }
                }
                is UpdateState.Downloading -> {
                    Text("מוריד… ${(s.progress * 100).roundToInt()}%")
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Ready -> {
                    Text("הגרסה ${s.release.versionName} הורדה ומוכנה להתקנה.")
                    Button(onClick = {
                        val updater = vm.updater
                        if (updater.canInstall()) {
                            context.startActivity(updater.installIntent(s.file))
                        } else {
                            vm.reportError("יש לאשר לאפליקציה להתקין עדכונים, ואז ללחוץ שוב על \"התקנה\"")
                            context.startActivity(updater.permissionIntent())
                        }
                    }) { Text("התקנה") }
                }
                is UpdateState.Failed -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { vm.checkForUpdates(manual = true) }) { Text("ניסיון חוזר") }
                }
            }
            Text("העדכונים נלקחים מגרסאות האפליקציה ב-GitHub.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
