package app.necessityq.ui

import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Add
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontStyle
import app.necessityq.domain.Reactions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.BubbleSlot
import app.necessityq.domain.ChatLayout
import app.necessityq.domain.ChatMember
import app.necessityq.domain.ItemMessage
import app.necessityq.domain.MediaBlob
import app.necessityq.domain.Mentions
import app.necessityq.domain.MessageKind
import app.necessityq.domain.MessageRules
import app.necessityq.domain.VoiceSpeed
import app.necessityq.domain.Waveform
import app.necessityq.platform.ImageCodec
import app.necessityq.platform.VoiceRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * צ'אט פוד — the conversation about a product, laid out like WhatsApp:
 *  - my messages on the left in green, everyone else's on the right in white, each writer's name
 *    in their own colour with a small initials avatar, runs of one writer grouped, a line per day;
 *  - swipe a message sideways to quote it (long-press for more);
 *  - voice messages: drag or tap the waveform to jump, pause / resume, and 1× · 1.5× · 2×;
 *  - "@" tags a member. Only tagged members and the author of a quoted message are alerted.
 */

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
private fun clock(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(clockFormat)

/**
 * The conversation itself.
 *  - long-press selects a message: the actions move to the top bar ([ChatSelectionBar]) and a row of
 *    emoji reactions opens above the message, as in WhatsApp;
 *  - tapping a quote jumps to the quoted message and flashes it;
 *  - [readOnly] (a conversation of a past shopping): reading, playing and jumping only.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatFoodThread(
    thread: List<ItemMessage>,
    vm: AppViewModel,
    selectedId: String?,
    onSelect: (ItemMessage?) -> Unit,
    onReply: (ItemMessage) -> Unit,
    readOnly: Boolean = false,
    /** Others typing in this conversation right now: shown at its end as WhatsApp does. */
    typing: List<app.necessityq.domain.TypingState> = emptyList(),
) {
    val names by vm.memberNames.collectAsStateWithLifecycle()
    val photos by vm.memberPhotos.collectAsStateWithLifecycle()
    val colors = NqTheme.colors
    val memberIds = names.keys.toList()
    fun colorOf(uid: String) = colors.senderColors[ChatLayout.colorSlot(uid, memberIds, colors.senderColors.size)]
    if (thread.isEmpty()) {
        if (typing.isNotEmpty()) {
            TypingBubble(typing, photos, ::colorOf)
            return
        }
        Text(
            "אין עדיין הודעות. כתבו שאלה, צלמו תמונה או הקליטו הודעה, וכל בני הבית יראו. " +
                "כדי שמישהו יקבל התראה, תייגו אותו עם @ או החליקו את ההודעה שלו כדי לצטט אותה.",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    val members by vm.chatMembers.collectAsStateWithLifecycle()
    val slots = remember(thread) { ChatLayout.slots(thread, System.currentTimeMillis()) }
    val byId = remember(thread) { thread.associateBy { it.id } }
    val requesters = remember { HashMap<String, BringIntoViewRequester>() }
    val scope = rememberCoroutineScope()
    var flashId by remember { mutableStateOf<String?>(null) }
    fun nameOf(uid: String, fallback: String) = if (uid == vm.me) "אני" else names[uid] ?: fallback
    fun jumpTo(id: String) {
        val target = requesters[id] ?: return
        scope.launch {
            target.bringIntoView()
            flashId = id
            delay(1_400)
            if (flashId == id) flashId = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxBubble = maxWidth * 0.8f
        Column {
            slots.forEach { slot ->
                key(slot.message.id) {
                    slot.dayHeader?.let { ChatDayChip(it) }
                    BubbleRow(
                        slot = slot,
                        vm = vm,
                        quoted = slot.message.replyToId?.let { byId[it] },
                        members = members,
                        maxBubble = maxBubble,
                        nameOf = ::nameOf,
                        colorOf = ::colorOf,
                        selected = selectedId == slot.message.id,
                        anySelected = selectedId != null,
                        flash = flashId == slot.message.id,
                        readOnly = readOnly,
                        requester = requesters.getOrPut(slot.message.id) { BringIntoViewRequester() },
                        onSelect = onSelect,
                        onReply = onReply,
                        onJump = ::jumpTo,
                        photo = photos[slot.message.authorId],
                    )
                }
            }
            if (typing.isNotEmpty()) TypingBubble(typing, photos, ::colorOf)
        }
    }
}

/**
 * Someone is typing here: their round picture (several overlap) and a bubble with three dots moving in
 * a wave, on the others' side — as in WhatsApp.
 */
@Composable
private fun TypingBubble(
    typing: List<app.necessityq.domain.TypingState>,
    photos: Map<String, androidx.compose.ui.graphics.ImageBitmap>,
    colorOf: (String) -> Color,
) {
    val label = app.necessityq.domain.Typing.label(typing.map { it.name })
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp).semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // overlapping circles, up to three
        Box {
            typing.take(3).forEachIndexed { i, t ->
                Box(Modifier.padding(start = (i * 16).dp)) {
                    Box(Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).padding(1.5.dp)) {
                        Avatar(t.name, colorOf(t.userId), photo = photos[t.userId])
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = NqTheme.colors.theirBubble,
            border = BorderStroke(1.dp, NqTheme.colors.hairline),
        ) {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                TypingDots(color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (typing.size > 1) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChatDayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.background(NqTheme.colors.dayChip, NqShapes.Pill).padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** The member's profile picture, or their initials in their colour when they have none. */
@Composable
private fun Avatar(name: String, color: Color, size: Dp = 28.dp, photo: androidx.compose.ui.graphics.ImageBitmap? = null) {
    if (photo != null) {
        androidx.compose.foundation.Image(
            photo, contentDescription = name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
        return
    }
    Box(
        Modifier.size(size).background(color.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(ChatLayout.initials(name), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubbleRow(
    slot: BubbleSlot,
    vm: AppViewModel,
    quoted: ItemMessage?,
    members: List<ChatMember>,
    maxBubble: Dp,
    nameOf: (String, String) -> String,
    colorOf: (String) -> Color,
    selected: Boolean,
    anySelected: Boolean,
    flash: Boolean,
    readOnly: Boolean,
    requester: BringIntoViewRequester,
    onSelect: (ItemMessage?) -> Unit,
    onReply: (ItemMessage) -> Unit,
    onJump: (String) -> Unit,
    photo: androidx.compose.ui.graphics.ImageBitmap? = null,
) {
    val m = slot.message
    val mine = m.authorId == vm.me
    val colors = NqTheme.colors
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    var showAllEmoji by remember { mutableStateOf(false) }
    LaunchedEffect(selected) { if (!selected) showReactions = false }
    val authorColor = colorOf(m.authorId)
    val authorName = nameOf(m.authorId, m.authorName)
    val canReply = !readOnly && !m.isDeleted
    val highlight by androidx.compose.animation.animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            flash -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
            else -> Color.Transparent
        },
        label = "bubbleHighlight",
    )

    // swipe sideways (either way) past the threshold to quote the message, as in WhatsApp
    val swipe = if (!canReply) Modifier else Modifier.draggable(
        orientation = Orientation.Horizontal,
        state = rememberDraggableState { delta ->
            dragX = (dragX + delta * 0.7f).coerceIn(-threshold * 1.4f, threshold * 1.4f)
            val nowArmed = abs(dragX) >= threshold
            if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            armed = nowArmed
        },
        onDragStopped = {
            if (abs(dragX) >= threshold) onReply(m)
            armed = false
            animate(dragX, 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { v, _ -> dragX = v }
        },
    )

    Box(
        Modifier.fillMaxWidth()
            .bringIntoViewRequester(requester)
            .background(highlight, NqShapes.Inset)
            .padding(top = if (slot.firstInRun) 6.dp else 2.dp, bottom = if (slot.lastInRun) 2.dp else 0.dp),
    ) {
        // the reply arrow shows in the space the bubble leaves while it is swiped
        val reveal = (abs(dragX) / threshold).coerceIn(0f, 1f)
        if (reveal > 0f) {
            Box(
                Modifier.align(if (dragX > 0) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)
                    .padding(horizontal = 8.dp)
                    .alpha(reveal)
                    .size(30.dp)
                    .background(if (armed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply, contentDescription = null, Modifier.size(18.dp),
                    tint = if (armed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().absoluteOffset { IntOffset(dragX.roundToInt(), 0) },
            // as in Hebrew WhatsApp: mine on the left (end), everyone else's on the right (start) with their avatar outside
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (!mine) {
                if (slot.firstInRun) Avatar(authorName, authorColor, photo = photo) else Spacer(Modifier.width(28.dp))
                Spacer(Modifier.width(6.dp))
            }
            Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                val outer = 4.dp
                val round = 16.dp
                val shape = RoundedCornerShape(
                    topStart = if (!mine && slot.firstInRun) outer else round,
                    topEnd = if (mine && slot.firstInRun) outer else round,
                    bottomStart = round,
                    bottomEnd = round,
                )
                val reacted = m.reactions.isNotEmpty() && !m.isDeleted
                Box {
                    Surface(
                        shape = shape,
                        color = if (mine) colors.myBubble else colors.theirBubble,
                        border = if (!mine) BorderStroke(1.dp, colors.hairline) else null,
                        modifier = Modifier
                            // room under the bubble for the part of the reactions that hangs below it
                            .padding(bottom = if (reacted) ReactionOverhang else 0.dp)
                            .widthIn(min = 64.dp, max = maxBubble)
                            .then(swipe)
                            .combinedClickable(
                                onClick = { if (anySelected) onSelect(if (selected) null else m) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelect(m)
                                    showReactions = !readOnly && !m.isDeleted
                                },
                                onLongClickLabel = "בחירת ההודעה",
                            ),
                    ) {
                        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 4.dp)) {
                            if (!mine && slot.firstInRun) {
                                Text(
                                    authorName,
                                    color = authorColor,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (m.isDeleted) {
                                Text(
                                    MessageRules.deletedLabel(m, vm.me, authorName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                if (m.replyToId != null) {
                                    QuoteBlock(
                                        vm, quoted,
                                        quoted?.let { nameOf(it.authorId, it.authorName) },
                                        quoted?.let { colorOf(it.authorId) },
                                        onClick = quoted?.let { q -> { onJump(q.id) } },
                                    )
                                }
                                // a picture or a recording takes its own taps (open / play), so a long press on it
                                // is caught here first — it selects the message, for delete and the other actions
                                if (m.kind != MessageKind.TEXT) Box(
                                    Modifier.longPressFirst {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelect(m)
                                        showReactions = !readOnly
                                    },
                                ) {
                                    when (m.kind) {
                                        MessageKind.IMAGE -> m.mediaId?.let { ImageAttachment(vm, it) }
                                        MessageKind.AUDIO -> m.mediaId?.let { VoiceNote(vm, it, m.durationMs, m.waveform, accent = if (mine) MaterialTheme.colorScheme.primary else authorColor) }
                                        MessageKind.TEXT -> Unit
                                    }
                                }
                                if (m.text.isNotBlank()) {
                                    Text(mentionText(m.text, members, vm.me), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            Row(
                                Modifier.align(Alignment.End).padding(top = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (vm.me in m.mentions) {
                                    Icon(Icons.Default.AlternateEmail, contentDescription = "תייגו אותך", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                if (m.editedAtMillis != null && !m.isDeleted) {
                                    Text("נערכה", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(clock(m.atMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (mine && !m.isDeleted) DeliveryTicks(vm.deliveryStatus(m))
                            }
                        }
                    }
                    // the reactions sit on the bubble's lower edge, half over it, as in WhatsApp
                    if (reacted) {
                        ReactionChips(
                            vm, m, nameOf, enabled = !readOnly,
                            modifier = Modifier.align(if (mine) Alignment.BottomEnd else Alignment.BottomStart).padding(horizontal = 10.dp),
                        )
                    }
                    if (selected && showReactions) {
                        ReactionPicker(
                            mine = m.reactions[vm.me],
                            onPick = { emoji ->
                                vm.react(m, Reactions.toggle(m.reactions[vm.me], emoji))
                                showReactions = false
                                onSelect(null)
                            },
                            onMore = { showReactions = false; showAllEmoji = true },
                            onDismiss = { showReactions = false },
                        )
                    }
                }
            }
        }
        // while a message is selected, a tap anywhere on a row (the highlight, a picture, a recording) answers
        // the selection first: on the selected message it clears it, on another it selects that one instead
        if (anySelected) {
            Box(
                Modifier.matchParentSize().combinedClickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { onSelect(if (selected) null else m) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(m)
                        showReactions = !readOnly && !m.isDeleted
                    },
                    onClickLabel = if (selected) "ביטול הבחירה" else "בחירת ההודעה",
                ),
            )
        }
    }
    if (showAllEmoji) {
        EmojiPickerSheet(
            current = m.reactions[vm.me],
            onPick = { emoji ->
                vm.react(m, Reactions.toggle(m.reactions[vm.me], emoji))
                showAllEmoji = false
                onSelect(null)
            },
            onDismiss = { showAllEmoji = false },
        )
    }
}

/** How far the reactions (30dp tall) hang below the bubble they sit on; the rest lies over its edge. */
private val ReactionOverhang = 18.dp

/** The row of quick emoji that opens above a long-pressed message; its "+" opens all the emoji. */
@Composable
private fun ReactionPicker(mine: String?, onPick: (String) -> Unit, onMore: () -> Unit, onDismiss: () -> Unit) {
    val lift = with(LocalDensity.current) { 52.dp.roundToPx() }
    androidx.compose.ui.window.Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, -lift),
        onDismissRequest = onDismiss,
    ) {
        Surface(shape = NqShapes.Pill, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp, tonalElevation = 2.dp) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Reactions.quick.forEach { emoji ->
                    Box(
                        Modifier.size(42.dp)
                            .background(if (emoji == mine) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                            .clickable { onPick(emoji) }
                            .semantics { contentDescription = "תגובה $emoji" },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                }
                // anything else: my reaction may already be an emoji from the full list, then "+" shows it
                val other = mine?.takeIf { it !in Reactions.quick }
                Box(
                    Modifier.padding(start = 2.dp).size(38.dp)
                        .background(
                            if (other != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        )
                        .clickable(onClick = onMore)
                        .semantics { contentDescription = "עוד אימוג'י" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (other != null) {
                        Text(other, style = MaterialTheme.typography.titleLarge)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The reactions under a message; a tap shows who reacted with what (and lets me take mine back). */
@Composable
private fun ReactionChips(
    vm: AppViewModel,
    m: ItemMessage,
    nameOf: (String, String) -> String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var details by remember { mutableStateOf(false) }
    val summary = Reactions.summary(m.reactions)
    // one reaction: a round badge, as in WhatsApp; more: a pill with the emoji and how many reacted
    val single = summary.size == 1 && m.reactions.size == 1
    // a plain (not clickable) Surface: a clickable one grows to 48dp and would no longer sit on the edge
    Surface(
        shape = if (single) CircleShape else NqShapes.Pill,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, NqTheme.colors.hairline),
        shadowElevation = 2.dp,
        modifier = modifier.height(30.dp).then(if (single) Modifier.width(30.dp) else Modifier),
    ) {
        Row(
            Modifier.fillMaxHeight()
                .clickable(onClickLabel = "מי הגיב") { details = true }
                .padding(horizontal = if (single) 0.dp else 8.dp)
                .then(if (single) Modifier.fillMaxWidth() else Modifier),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            summary.take(3).forEach { (emoji, _) -> Text(emoji, fontSize = 16.sp) }
            if (m.reactions.size > 1) {
                Text(" ${m.reactions.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (details) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { details = false },
            title = { Text("תגובות") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    m.reactions.entries.sortedBy { if (it.key == vm.me) "" else nameOf(it.key, "משתתף") }.forEach { (uid, emoji) ->
                        val me = uid == vm.me
                        Row(
                            Modifier.fillMaxWidth().then(
                                if (me && enabled) Modifier.clickable { vm.react(m, null); details = false } else Modifier,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(nameOf(uid, "משתתף"), fontWeight = FontWeight.SemiBold)
                                if (me && enabled) {
                                    Text("נגיעה להסרה", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(emoji, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { details = false }) { Text("סגירה") } },
        )
    }
}

/**
 * The top bar while a message is selected (long-press), as in WhatsApp: close, and the actions on
 * that message — quote, copy, details, edit, delete. In a past conversation only copy and details.
 */
@Composable
internal fun ChatSelectionBar(
    vm: AppViewModel,
    m: ItemMessage,
    readOnly: Boolean,
    onClose: () -> Unit,
    onReply: (ItemMessage) -> Unit,
    onEdit: (ItemMessage) -> Unit,
    onDelete: (ItemMessage) -> Unit,
    onDetails: (ItemMessage) -> Unit,
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "ביטול הבחירה") }
            Text("הודעה נבחרה", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!readOnly && !m.isDeleted) {
                IconButton(onClick = { onClose(); onReply(m) }) { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "ציטוט ותגובה") }
            }
            if (m.text.isNotBlank() && !m.isDeleted) {
                IconButton(onClick = {
                    runCatching {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("הודעה", m.text))
                    }
                    onClose()
                }) { Icon(Icons.Default.ContentCopy, contentDescription = "העתקת הטקסט") }
            }
            IconButton(onClick = { onClose(); onDetails(m) }) { Icon(Icons.Default.Info, contentDescription = "פרטי הודעה") }
            if (!readOnly && MessageRules.canEdit(m, vm.me, now)) {
                IconButton(onClick = { onClose(); onEdit(m) }) {
                    Icon(Icons.Default.Edit, contentDescription = "עריכה (עוד ${MessageRules.minutesLeft(m, now)} דק')")
                }
            }
            if (!readOnly && MessageRules.canDelete(m, vm.me)) {
                IconButton(onClick = { onClose(); onDelete(m) }) { Icon(Icons.Default.Delete, contentDescription = "מחיקה") }
            }
        }
    }
}

/** The quoted message inside a bubble: a bar in the quoted writer's colour, their name, the start of it and a picture thumbnail. Tap: jump to it. */
@Composable
private fun QuoteBlock(vm: AppViewModel, quoted: ItemMessage?, name: String?, color: Color?, onClick: (() -> Unit)?) {
    val accent = color ?: MaterialTheme.colorScheme.outline
    Row(
        Modifier.padding(top = 2.dp, bottom = 4.dp)
            .height(IntrinsicSize.Min)
            .clip(NqShapes.Inset)
            .background(accent.copy(alpha = 0.10f))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "מעבר להודעה המצוטטת", onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.weight(1f, fill = false).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(name ?: "הודעה", style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                quoted?.preview ?: "ההודעה המקורית אינה זמינה",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (quoted != null && !quoted.isDeleted && quoted.kind == MessageKind.IMAGE) {
            quoted.mediaId?.let { QuoteThumb(vm, it, 46.dp) }
        }
    }
}

/** A small square of a quoted picture, as WhatsApp shows next to a quote. */
@Composable
internal fun QuoteThumb(vm: AppViewModel, mediaId: String, size: Dp) {
    val thumb by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, mediaId) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            vm.loadMedia(mediaId)?.bytes?.let { ImageCodec.decodeThumb(it, 160) }?.asImageBitmap()
        }
    }
    Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant)) {
        thumb?.let {
            androidx.compose.foundation.Image(
                it, contentDescription = "תמונה מצוטטת",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** The text with every "@name" tag bold and coloured; a tag of me also gets a tinted background. */
@Composable
private fun mentionText(text: String, members: List<ChatMember>, me: String): AnnotatedString {
    val tagColor = MaterialTheme.colorScheme.primary
    val myTag = NqTheme.colors.mentionBackground
    return remember(text, members, me, tagColor, myTag) {
        val found = Mentions.find(text, members)
        if (found.isEmpty()) return@remember AnnotatedString(text)
        AnnotatedString.Builder(text).apply {
            found.forEach { (range, uid) ->
                val forMe = uid == me || uid == Mentions.EVERYONE
                addStyle(
                    SpanStyle(color = tagColor, fontWeight = FontWeight.Bold, background = if (forMe) myTag else Color.Unspecified),
                    range.first, range.last + 1,
                )
            }
        }.toAnnotatedString()
    }
}

/** Colours "@name" tags while typing (the text itself is unchanged). */
private class MentionHighlight(private val members: List<ChatMember>, private val style: SpanStyle) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val found = Mentions.find(text.text, members)
        if (found.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = AnnotatedString.Builder(text).apply {
            found.forEach { (range, _) -> addStyle(style, range.first, range.last + 1) }
        }.toAnnotatedString()
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is MentionHighlight && other.members == members && other.style == style
    override fun hashCode() = members.hashCode() * 31 + style.hashCode()
}

// ---------- voice messages ----------

/**
 * A voice message: play / pause, the waveform (tap or drag to jump to any point, also before playing),
 * the time, and the speed chip 1× → 1.5× → 2× (one speed for all recordings, remembered).
 */
@Composable
internal fun VoiceNote(vm: AppViewModel, mediaId: String, durationMs: Long?, waveform: List<Int>?, accent: Color) {
    // as in WhatsApp, also in Hebrew: play on the left and the recording runs left to right
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        VoiceNoteRow(vm, mediaId, durationMs, waveform, accent)
    }
}

@Composable
private fun VoiceNoteRow(vm: AppViewModel, mediaId: String, durationMs: Long?, waveform: List<Int>?, accent: Color) {
    val scope = rememberCoroutineScope()
    val speed by vm.voiceSpeed.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    /** While the finger drags on the waveform: where it is (0..1). */
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val total = durationMs ?: 0L
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val rest = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    // recordings from before the waveform existed get a neutral shape
    val bars = waveform ?: remember { List(Waveform.BARS) { 25 + (it * 7 % 5) * 8 } }
    val fraction = dragFraction ?: if (total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f

    LaunchedEffect(playing) {
        while (playing) {
            // another recording started: this one stopped
            if (vm.player.playingId != mediaId) { playing = false; break }
            if (dragFraction == null) position = vm.player.position()
            delay(50)
        }
    }
    DisposableEffect(mediaId) { onDispose { if (vm.player.playingId == mediaId) vm.player.stop() } }

    fun start() {
        if (vm.player.playingId == mediaId && vm.player.resume()) { playing = true; return }
        loading = true
        scope.launch {
            val media = vm.loadMedia(mediaId)
            loading = false
            if (media == null) {
                vm.reportError("לא ניתן לטעון את ההקלטה")
                return@launch
            }
            val from = if (position >= total - 250) 0L else position
            runCatching { vm.player.play(mediaId, media.bytes, startMs = from) { playing = false; position = 0L } }
                .onSuccess { playing = true }
                .onFailure { vm.reportError("ההשמעה נכשלה") }
        }
    }

    fun seek(f: Float) {
        val ms = VoiceSpeed.seekPosition(f, total)
        position = ms
        if (vm.player.playingId == mediaId) vm.player.seekTo(ms.toInt())
    }

    fun fractionAt(x: Float, width: Int): Float =
        (x / width.coerceAtLeast(1)).coerceIn(0f, 1f).let { if (rtl) 1f - it else it }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        IconButton(onClick = { if (playing) { vm.player.pause(); playing = false } else start() }) {
            when {
                loading -> CircularProgressIndicator(Modifier.size(20.dp))
                playing -> Icon(Icons.Default.Pause, contentDescription = "השהיה", tint = accent)
                else -> Icon(Icons.Default.PlayArrow, contentDescription = "השמעה", tint = accent)
            }
        }
        Column(Modifier.weight(1f)) {
            Canvas(
                Modifier.fillMaxWidth().height(34.dp)
                    .pointerInput(total, rtl) {
                        detectTapGestures { o -> if (total > 0) seek(fractionAt(o.x, size.width)) }
                    }
                    .pointerInput(total, rtl) {
                        detectHorizontalDragGestures(
                            onDragStart = { o -> if (total > 0) dragFraction = fractionAt(o.x, size.width) },
                            onHorizontalDrag = { change, _ ->
                                if (total > 0) {
                                    change.consume()
                                    dragFraction = fractionAt(change.position.x, size.width)
                                }
                            },
                            onDragEnd = { dragFraction?.let { seek(it) }; dragFraction = null },
                            onDragCancel = { dragFraction = null },
                        )
                    },
            ) {
                val n = bars.size
                val slotW = size.width / n
                val barW = slotW * 0.55f
                fun xOf(t: Float) = (if (rtl) 1f - t else t) * size.width
                bars.forEachIndexed { i, level ->
                    val t = (i + 0.5f) / n
                    val h = (size.height * level / 100f).coerceAtLeast(4.dp.toPx())
                    drawRoundRect(
                        color = if (t <= fraction) accent else rest,
                        topLeft = Offset(xOf(t) - barW / 2, (size.height - h) / 2),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2),
                    )
                }
                val r = (if (dragFraction != null) 9.dp else 6.dp).toPx()
                drawCircle(accent, radius = r, center = Offset(xOf(fraction).coerceIn(r, size.width - r), size.height / 2))
            }
            val shownMs = when {
                dragFraction != null -> VoiceSpeed.seekPosition(dragFraction!!, total)
                playing || position > 0 -> position
                else -> total
            }
            Text(ItemMessage.formatDuration(shownMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            onClick = { vm.cycleVoiceSpeed() },
            shape = NqShapes.Pill,
            color = accent.copy(alpha = 0.14f),
            modifier = Modifier.heightIn(min = 32.dp),
        ) {
            Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(
                    VoiceSpeed.label(speed),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.semantics { contentDescription = "מהירות השמעה ${VoiceSpeed.label(speed)}, נגיעה להחלפה" },
                )
            }
        }
    }
}

// ---------- the message box ----------

/**
 * Text box with picture, camera and voice; the message being answered or edited above it; member
 * suggestions while typing "@"; and a line that says who will be alerted.
 */
@Composable
internal fun ChatFoodComposer(
    vm: AppViewModel,
    itemId: String,
    itemName: String,
    replyTo: ItemMessage?,
    onReplyDone: () -> Unit,
    editing: ItemMessage?,
    onEditDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val members by vm.chatMembers.collectAsStateWithLifecycle()
    val names by vm.memberNames.collectAsStateWithLifecycle()
    var draft by remember(itemId) { mutableStateOf(TextFieldValue("")) }
    // leaving the conversation (or switching product) ends our "typing…"
    DisposableEffect(itemId) { onDispose { vm.stopTyping() } }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }

    fun setDraft(value: TextFieldValue) {
        val changed = value.text != draft.text
        draft = value
        if (changed) vm.onDraftChanged(itemId, itemName, value.text)
    }

    // starting an edit puts the message text in the box; quoting or editing moves the cursor there
    val focus = remember { FocusRequester() }
    LaunchedEffect(editing?.id) { if (editing != null) draft = TextFieldValue(editing.text, TextRange(editing.text.length)) }
    LaunchedEffect(replyTo?.id, editing?.id) { if (replyTo != null || editing != null) runCatching { focus.requestFocus() } }

    fun sendImage(bitmap: android.graphics.Bitmap?) {
        if (bitmap == null) return
        scope.launch {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { ImageCodec.compress(bitmap) }
            vm.sendMedia(itemId, itemName, MessageKind.IMAGE, MediaBlob("image/jpeg", bytes), caption = draft.text, replyToId = replyTo?.id)
            draft = TextFieldValue("")
            onReplyDone()
        }
    }

    fun sendRecording(r: VoiceRecorder.Recording) {
        vm.sendMedia(
            itemId, itemName, MessageKind.AUDIO, MediaBlob("audio/mp4", r.bytes),
            durationMs = r.durationMs, replyToId = replyTo?.id, waveform = r.waveform,
        )
        onReplyDone()
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            sendImage(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ImageCodec.fromUri(context, uri) })
        }
    }
    // the camera writes the full-size photo into a file of ours; kept across the activity being recreated
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val path = cameraPath
        cameraPath = null
        if (taken && path != null) scope.launch {
            val file = java.io.File(path)
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ImageCodec.fromUri(context, android.net.Uri.fromFile(file)).also { file.delete() }
            }
            if (bitmap == null) vm.reportError("לא ניתן לקרוא את התמונה") else sendImage(bitmap)
        }
    }
    fun openCamera() {
        runCatching {
            val (file, uri) = ImageCodec.newCameraFile(context)
            cameraPath = file.absolutePath
            camera.launch(uri)
        }.onFailure { cameraPath = null; vm.reportError("לא ניתן לפתוח את המצלמה") }
    }

    fun startRecording() {
        runCatching { vm.recorder.start() }
            .onSuccess { recording = true; elapsed = 0L }
            .onFailure { vm.reportError("ההקלטה לא הצליחה להתחיל") }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else vm.reportError("כדי להקליט צריך הרשאת מיקרופון")
    }
    LaunchedEffect(recording) {
        while (recording) {
            delay(VoiceRecorder.SAMPLE_MS)
            vm.recorder.sample()
            elapsed += VoiceRecorder.SAMPLE_MS
            if (elapsed >= VoiceRecorder.MAX_MS) {
                recording = false
                vm.recorder.stop()?.let { sendRecording(it) }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { if (vm.recorder.isRecording) vm.recorder.stopQuietly() } }

    replyTo?.let { m ->
        val palette = NqTheme.colors.senderColors
        val color = palette[ChatLayout.colorSlot(m.authorId, names.keys.toList(), palette.size)]
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, NqShapes.Strip)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(color, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(
                    if (m.authorId == vm.me) "ציטוט של הודעה שלי" else "ציטוט של ${names[m.authorId] ?: m.authorName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                Text(m.preview, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (m.kind == MessageKind.IMAGE && !m.isDeleted) m.mediaId?.let { QuoteThumb(vm, it, 44.dp) }
            IconButton(onClick = onReplyDone) { Icon(Icons.Default.Close, contentDescription = "ביטול הציטוט") }
        }
    }
    editing?.let { m ->
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer, NqShapes.Strip)
                .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text("עריכת הודעה", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("אפשר לערוך עוד ${MessageRules.minutesLeft(m, System.currentTimeMillis())} דק'", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { draft = TextFieldValue(""); onEditDone() }) { Icon(Icons.Default.Close, contentDescription = "ביטול העריכה") }
        }
    }

    if (recording) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = { vm.recorder.stopQuietly(); recording = false }) {
                        Icon(Icons.Default.Delete, contentDescription = "ביטול ההקלטה", tint = MaterialTheme.colorScheme.error)
                    }
                    Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                    Text(
                        "  " + ItemMessage.formatDuration(elapsed) + " / " + ItemMessage.formatDuration(VoiceRecorder.MAX_MS),
                        Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.size(6.dp))
            ComposerAction(Icons.AutoMirrored.Filled.Send, "שליחת ההקלטה") {
                recording = false
                val result = vm.recorder.stop()
                if (result == null) vm.reportError("ההקלטה קצרה מדי") else sendRecording(result)
            }
        }
        return
    }

    // "@" typed: members to tag
    val query = Mentions.activeQuery(draft.text, draft.selection.start)
    val suggestions = remember(query, members) { query?.let { Mentions.suggest(it, members, vm.me) }.orEmpty() }
    if (suggestions.isNotEmpty()) {
        val palette = NqTheme.colors.senderColors
        val photos by vm.memberPhotos.collectAsStateWithLifecycle()
        MentionSuggestions(suggestions, colorOf = { uid -> palette[ChatLayout.colorSlot(uid, names.keys.toList(), palette.size)] }, photos = photos) { member ->
            val (text, cursor) = Mentions.complete(draft.text, draft.selection.start, member.name)
            setDraft(TextFieldValue(text, TextRange(cursor)))
        }
    }

    // who will get an alert for this message: the tagged members and the author of the quoted message
    val alerted = remember(draft.text, members, replyTo) {
        (Mentions.resolve(draft.text, members, vm.me) + listOfNotNull(replyTo?.authorId?.takeIf { it != vm.me }))
            .distinct().mapNotNull { id -> members.firstOrNull { it.userId == id }?.name }
    }
    if (alerted.isNotEmpty() && editing == null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
            Icon(Icons.Default.NotificationsActive, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                "  התראה תישלח ל" + alerted.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    val tagStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    val highlight = remember(members, tagStyle) { MentionHighlight(members, tagStyle) }

    // WhatsApp-style bar: a rounded box with the text and the picture buttons, and beside it one
    // round button that records when the box is empty and sends once something is typed
    Row(verticalAlignment = Alignment.Bottom) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                TextField(
                    draft, { setDraft(it) },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text(if (editing != null) "עריכה…" else if (replyTo != null) "תגובה…" else "הודעה · @ לתיוג") },
                    maxLines = 5,
                    visualTransformation = highlight,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                if (editing == null) {
                    IconButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "שליחת תמונה מהגלריה", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // as in WhatsApp, the camera makes room once typing starts
                    if (draft.text.isBlank()) {
                        IconButton(onClick = { openCamera() }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "צילום תמונה", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        when {
            editing != null -> ComposerAction(
                Icons.Default.Check, "שמירת העריכה",
                enabled = draft.text.isNotBlank() || editing.kind != MessageKind.TEXT,
            ) { vm.editMessage(editing, draft.text); draft = TextFieldValue(""); onEditDone() }
            draft.text.isBlank() -> ComposerAction(Icons.Default.Mic, "הקלטת הודעה") {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> ComposerAction(Icons.AutoMirrored.Filled.Send, "שליחה") {
                vm.sendMessage(itemId, itemName, draft.text, replyTo?.id)
                draft = TextFieldValue("")
                onReplyDone()
            }
        }
    }
}

/** Members offered while typing "@": tap one to put the tag in the text. */
@Composable
private fun MentionSuggestions(
    members: List<ChatMember>,
    colorOf: (String) -> Color,
    photos: Map<String, androidx.compose.ui.graphics.ImageBitmap>,
    onPick: (ChatMember) -> Unit,
) {
    Surface(
        shape = NqShapes.Panel,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, NqTheme.colors.hairline),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            members.forEachIndexed { i, member ->
                if (i > 0) HorizontalDivider(color = NqTheme.colors.hairline)
                val everyone = member.userId == Mentions.EVERYONE
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(member) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (everyone) {
                        Box(
                            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.AlternateEmail, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        Avatar(member.name, colorOf(member.userId), photo = photos[member.userId])
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(member.name, fontWeight = FontWeight.SemiBold)
                        if (everyone) {
                            Text("כל בני הבית יקבלו התראה", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** The round button beside the message box (record / send / save), as in WhatsApp. */
@Composable
internal fun ComposerAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    androidx.compose.material3.FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
    ) {
        Icon(icon, contentDescription = description)
    }
}

// ---------- conversations of past shoppings ----------

private val pastDayFormat = DateTimeFormatter.ofPattern("d.M.yyyy")
private fun pastDay(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(pastDayFormat)

/**
 * A conversation whose shopping ended (the item was bought, cleared or removed): opened in full, as it
 * was — pictures, recordings, quotes that jump, reactions — but read-only. Until the retention period
 * deletes it.
 */
@Composable
internal fun ArchivedChatScreen(vm: AppViewModel, thread: List<ItemMessage>, onDelete: () -> Unit, onDismiss: () -> Unit) {
    if (thread.isEmpty()) return
    val pageWindow = rememberSlidingPage(onClosed = onDismiss)
    val retention by vm.chatRetention.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<ItemMessage?>(null) }
    val last = thread.last()
    val endedAt = last.archivedAtMillis ?: last.atMillis
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { scroll.scrollTo(scroll.maxValue) }
    SlidingPageDialog(pageWindow) {
        androidx.activity.compose.BackHandler(enabled = selectedId != null) { selectedId = null }
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val selected = selectedId?.let { id -> thread.firstOrNull { it.id == id } }
            if (selected != null) {
                ChatSelectionBar(
                    vm, selected, readOnly = true,
                    onClose = { selectedId = null },
                    onReply = {}, onEdit = {}, onDelete = {},
                    onDetails = { details = it },
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = pageWindow::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
                    Column(Modifier.weight(1f)) {
                        Text(last.itemName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val deleteAt = retention?.let { app.necessityq.domain.ConversationRetention.deleteAt(endedAt, it) }
                        Text(
                            "צ'אט פוד · קנייה שהסתיימה ${pastDay(endedAt)} · ${thread.size} הודעות" +
                                (deleteAt?.let { " · תימחק ב-${pastDay(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "מחיקת השיחה") }
                }
            }
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(horizontal = 12.dp, vertical = 4.dp)) {
                ChatFoodThread(thread, vm, selectedId = selectedId, onSelect = { selectedId = it?.id }, onReply = {}, readOnly = true)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "שיחה מקנייה שהסתיימה: אפשר לקרוא, לראות תמונות ולשמוע הקלטות. שיחה חדשה על המוצר נפתחת כשהוא שוב ברשימה.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
    details?.let { MessageDetailsDialog(vm, it, onDismiss = { details = null }) }
}

/**
 * A long press over content whose children handle their own taps: watched before the children see the touch, and once
 * it fires the rest of that touch is taken, so the picture does not also open / the recording does not also play.
 * A move beyond the touch slop (a scroll, a swipe to reply) or an early lift leaves the touch to the children.
 */
private fun Modifier.longPressFirst(onLongPress: () -> Unit): Modifier = composed {
  val latest by androidx.compose.runtime.rememberUpdatedState(onLongPress)
  pointerInput(Unit) {
    val pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
        val slop = viewConfiguration.touchSlop
        // finishes (non-null) when the finger lifts or moves first; times out (null) when it is held: a long press
        val leftEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var done = false
            while (!done) {
                val change = awaitPointerEvent(pass).changes.firstOrNull { it.id == down.id }
                done = change == null || !change.pressed || (change.position - down.position).getDistance() > slop
            }
            true
        }
        val fired = leftEarly == null
        if (fired) {
            latest()
            // the rest of this touch is ours
            do {
                val event = awaitPointerEvent(pass)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
}
