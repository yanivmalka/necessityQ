package app.necessityq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.necessityq.ui.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.ItemMessage
import app.necessityq.domain.LineStatus
import kotlinx.coroutines.launch

/**
 * צ'אט פוד about one product, on a page of its own: it opens from the product page ("צ'אט פוד על המוצר", next to
 * "חסר" and "הסרה"), from the messages and from a notification, and slides in and out like every other page.
 * [onOpenProduct]: the product's own page, offered in the title when the conversation was opened from elsewhere;
 * null when this page was opened from the product page (back returns there).
 * [onOpenItem]: another product's conversation, from its "typing…" banner.
 */
@Composable
fun ChatFoodPage(
    vm: AppViewModel,
    itemId: String,
    itemName: String,
    onDismiss: () -> Unit,
    onOpenItem: ((itemId: String, itemName: String) -> Unit)? = null,
    onOpenProduct: (() -> Unit)? = null,
) {
    val pageWindow = rememberSlidingPage(onClosed = onDismiss)
    val typing by vm.typingNow.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val allMessages by vm.itemMessages.collectAsStateWithLifecycle()
    vm.receipts.collectAsStateWithLifecycle()
    val line = snapshot?.lines?.firstOrNull { it.itemId == itemId && it.status == LineStatus.NEEDED }
    val name = line?.displayName ?: itemName
    val thread = allMessages.filter { it.itemId == itemId }

    var selectedMessageId by remember(itemId) { mutableStateOf<String?>(null) }
    var replyTo by remember(itemId) { mutableStateOf<ItemMessage?>(null) }
    var editingMessage by remember(itemId) { mutableStateOf<ItemMessage?>(null) }
    var confirmDeleteMessage by remember { mutableStateOf<ItemMessage?>(null) }
    var messageDetails by remember { mutableStateOf<ItemMessage?>(null) }
    var confirmDeleteChat by remember { mutableStateOf(false) }
    val selectedMessage = selectedMessageId?.let { id -> thread.firstOrNull { it.id == id } }

    // no alert sound for the conversation that is open on screen
    DisposableEffect(itemId) {
        app.necessityq.platform.MentionAlerter.openItemId = itemId
        vm.setOpenChat(itemId) // the whole conversation, not only what the household-wide window holds
        onDispose {
            if (app.necessityq.platform.MentionAlerter.openItemId == itemId) app.necessityq.platform.MentionAlerter.openItemId = null
            vm.setOpenChat(null)
        }
    }
    // when this conversation was last read, taken before the page marks it as read now: the "unread" divider
    val openedReadAt = remember(itemId) { vm.lastReadAt(itemId) }
    // reading the page marks the conversation as read, also for messages that arrive while it is open
    LaunchedEffect(itemId, thread.size) { vm.markRead(itemId) }

    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // opens at the latest message; a new message (sent or received) brings the end into view, as in a chat app
    LaunchedEffect(itemId) { scroll.scrollTo(scroll.maxValue) }
    var seenMessages by remember(itemId) { mutableIntStateOf(thread.size) }
    // messages that arrived while reading older ones: counted on the jump-down button, not forced on screen
    var awayMessages by remember(itemId) { mutableIntStateOf(0) }
    LaunchedEffect(thread.size) {
        if (thread.size > seenMessages) {
            val nearEnd = scroll.maxValue - scroll.value < 600
            val mine = thread.lastOrNull()?.authorId == vm.me
            if (nearEnd || mine) scroll.animateScrollTo(scroll.maxValue) else awayMessages += thread.size - seenMessages
        }
        seenMessages = thread.size
    }
    val farFromEnd by remember { derivedStateOf { scroll.maxValue - scroll.value > 500 } }
    LaunchedEffect(farFromEnd) { if (!farFromEnd) awayMessages = 0 }
    val typingHere = typing.filter { it.itemId == itemId }
    LaunchedEffect(typingHere.size) {
        if (typingHere.isNotEmpty() && scroll.maxValue - scroll.value < 600) scroll.animateScrollTo(scroll.maxValue)
    }

    SlidingPageDialog(pageWindow) {
        ItemPageWindow()
        androidx.activity.compose.BackHandler(enabled = selectedMessage != null) { selectedMessageId = null }
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (selectedMessage != null) {
                ChatSelectionBar(
                    vm, selectedMessage, readOnly = false,
                    onClose = { selectedMessageId = null },
                    onReply = { replyTo = it; editingMessage = null },
                    onEdit = { editingMessage = it; replyTo = null },
                    onDelete = { confirmDeleteMessage = it },
                    onDetails = { messageDetails = it },
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = pageWindow::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
                    Column(
                        Modifier.weight(1f).then(if (onOpenProduct != null) Modifier.clickable(onClick = onOpenProduct) else Modifier),
                    ) {
                        Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "צ'אט פוד" + (if (thread.isNotEmpty()) " · ${thread.size} הודעות" else "") + (if (onOpenProduct != null) " · לדף המוצר ›" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (onOpenProduct != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (thread.isNotEmpty()) {
                        IconButton(onClick = { confirmDeleteChat = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "מחיקת כל השיחה")
                        }
                    }
                    ChatSoundToggle(vm)
                }
            }
            HorizontalDivider()
            // typing about another product: shown here too, one tap away
            typing.filter { it.itemId != itemId }.groupBy { it.itemId }.values.forEach { group ->
                val first = group.first()
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).then(
                        if (onOpenItem != null) Modifier.clickable { onOpenItem(first.itemId, first.itemName) } else Modifier,
                    ),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TypingDots()
                        Text(
                            "  " + app.necessityq.domain.Typing.labelFor(group).removeSuffix("…") + " על ${first.itemName}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (onOpenItem != null) Text("מעבר ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier.fillMaxSize().fadingVerticalScroll(scroll).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (thread.isEmpty() && typingHere.isEmpty()) {
                    Text(
                        "עוד אין הודעות על $name. כתבו כאן שאלה, תמונה או הקלטה לבני הבית — למשל איזה מותג לקנות.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                ChatFoodThread(
                    thread, vm = vm,
                    typing = typingHere,
                    unreadAfter = openedReadAt,
                    selectedId = selectedMessageId,
                    onSelect = { selectedMessageId = it?.id },
                    onReply = { replyTo = it; editingMessage = null },
                )
                Text(
                    "השיחה שייכת לקנייה הנוכחית: כשהפריט נוקה מהרשימה היא עוברת ל\"שיחות מקניות קודמות\" בהודעות.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(bottom = 8.dp))
            }
            if (farFromEnd) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "לסוף השיחה")
                        if (awayMessages > 0) Text("$awayMessages", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            }
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ChatFoodComposer(
                    vm, itemId, name,
                    replyTo = replyTo, onReplyDone = { replyTo = null },
                    editing = editingMessage, onEditDone = { editingMessage = null },
                )
            }
        }
    }

    confirmDeleteMessage?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDeleteMessage = null },
            title = { Text("למחוק את ההודעה?") },
            text = { Text("ההודעה תימחק אצל כל בני הבית, ובמקומה ייכתב \"מחקת את ההודעה\". לא ניתן לשחזר אותה.") },
            confirmButton = { TextButton(onClick = { vm.deleteMessage(m); confirmDeleteMessage = null }) { Text("מחיקה") } },
            dismissButton = { TextButton(onClick = { confirmDeleteMessage = null }) { Text("ביטול") } },
        )
    }
    if (confirmDeleteChat) {
        AlertDialog(
            onDismissRequest = { confirmDeleteChat = false },
            title = { Text("למחוק את כל השיחה?") },
            text = { Text("${thread.size} הודעות על $name יימחקו אצל כל בני הבית, כולל תמונות והקלטות.") },
            confirmButton = { TextButton(onClick = { vm.deleteConversation(thread); confirmDeleteChat = false }) { Text("מחיקה") } },
            dismissButton = { TextButton(onClick = { confirmDeleteChat = false }) { Text("ביטול") } },
        )
    }
    messageDetails?.let { m -> MessageDetailsDialog(vm, m, onDismiss = { messageDetails = null }) }
}

/** "צ'אט פוד על המוצר" on the product page: how many messages, and a badge for unread ones. */
@Composable
internal fun ChatFoodButtonLabel(messages: Int, unread: Int) {
    Text(
        when {
            messages == 0 -> "התחלת צ'אט פוד"
            else -> "צ'אט פוד ($messages)"
        },
        fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
    if (unread > 0) {
        Spacer(Modifier.padding(start = 4.dp))
        androidx.compose.material3.Badge { Text("$unread") }
    }
}
