package app.necessityq.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.necessityq.domain.MediaBlob
import app.necessityq.domain.MessageKind
import app.necessityq.platform.ImageCodec
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.necessityq.domain.ItemMessage
import app.necessityq.domain.LineStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val timeFormat = DateTimeFormatter.ofPattern("d.M HH:mm")
private fun fmt(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat)

/**
 * Expanded information about one product plus its conversation. Works for an item that is on the list
 * now (with quick actions) and for one that is not (opened from the messages tab).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ItemSheet(vm: AppViewModel, itemId: String, itemName: String, onDismiss: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val allMessages by vm.itemMessages.collectAsStateWithLifecycle()
    val names by vm.memberNames.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val prices by vm.prices.collectAsStateWithLifecycle()
    val price = prices[itemId]
    var editing by remember { mutableStateOf(false) }
    var editingPrice by remember { mutableStateOf(false) }

    val line = snapshot?.lines?.firstOrNull { it.itemId == itemId && it.status == LineStatus.NEEDED }
    val stats = snapshot?.stats?.get(itemId)
    val thread = allMessages.filter { it.itemId == itemId }
    val favorite = itemId in favorites
    val lastStore = purchases.firstOrNull { it.itemId == itemId && it.storeId != null }
        ?.storeId?.let { id -> stores.firstOrNull { it.id == id }?.name }

    // reading the sheet marks the conversation as read, also for messages that arrive while it is open
    LaunchedEffect(itemId, thread.size) { vm.markRead(itemId) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (favorite) Icon(Icons.Default.Star, "מועדף", tint = Color(0xFFC77700))
                Text(line?.displayName ?: itemName, style = ProductNameStyle, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                // ⋮ actions — on the left edge in Hebrew layout
                androidx.compose.foundation.layout.Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "פעולות") }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (favorite) "הסרה מהמועדפים" else "הוספה למועדפים") },
                            leadingIcon = { Icon(if (favorite) Icons.Default.StarBorder else Icons.Default.Star, null) },
                            onClick = { vm.toggleFavorite(itemId); menu = false },
                        )
                        if (line != null) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (line.missing) "ביטול סימון חסר" else "סימון כחסר") },
                                leadingIcon = { Icon(Icons.Default.ReportProblem, null) },
                                onClick = { vm.setMissing(line, !line.missing); menu = false },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("עריכת כמות והערה") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { editing = true; menu = false },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("הסרה מהרשימה") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { vm.delete(line); menu = false; onDismiss() },
                            )
                        }
                    }
                }
            }

            if (line != null) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.necessityq.domain.ItemTags.of(line, stats, favorite).forEach { TagBadge(it) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuantityStepper(line.quantity, onMinus = { vm.adjustQuantity(line, -1) }, onPlus = { vm.adjustQuantity(line, +1) })
                    PriceSlot(price = null, average = null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.checkOff(line); onDismiss() }) { Text("נקנה ✓") }
                    OutlinedButton(onClick = { vm.delete(line); onDismiss() }) {
                        Icon(Icons.Default.Delete, null, Modifier.padding(end = 4.dp))
                        Text("הסרה")
                    }
                }
                if (line.missing) {
                    Text(
                        "מסומן כחסר — בני הבית שנבחרו לקבל התראות יקבלו הודעה (בתפריט ← הבית ומשתתפים).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Button(onClick = { vm.addByName(itemName) }) { Text("הוספה לרשימה") }
            }

            HorizontalDivider()
            InfoLine("קטגוריה", (line?.category ?: stats?.identity?.category)?.label)
            line?.let { InfoLine("מקור", it.source.label) }
            line?.let { InfoLine(if (it.isAuto) "נוצר עבור" else "נוסף", "${names[it.addedBy] ?: "בן/בת בית"} · ${fmt(it.addedAtMillis)}") }
            line?.note?.let { InfoLine("הערה", it) }
            stats?.let { s ->
                val days = ChronoUnit.DAYS.between(s.lastPurchase, LocalDate.now())
                InfoLine("נקנה לאחרונה", if (days <= 0) "היום" else "לפני $days ימים")
                InfoLine("תדירות", s.cadenceDays?.let { "בערך כל ${it.roundToInt().coerceAtLeast(1)} ימים" } ?: "אין עדיין מספיק היסטוריה")
                InfoLine("כמות רגילה", s.typicalQuantity?.format())
                InfoLine("נקנה", "${s.purchaseDays} פעמים")
            }
            InfoLine("נקנה לאחרונה ב", lastStore)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("מחיר משוער: ", fontWeight = FontWeight.Bold)
                Text(price?.let { "₪%.2f".format(it) + unitHint(line?.quantity) } ?: "לא הוזן", Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { editingPrice = true }) { Text(if (price == null) "הזנה" else "עריכה") }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("שיחה על המוצר", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                ChatSoundToggle(vm)
            }
            vm.receipts.collectAsStateWithLifecycle()
            var replyTo by remember(itemId) { mutableStateOf<ItemMessage?>(null) }
            ChatThread(thread, vm = vm, onReply = { replyTo = it })
            ChatComposer(vm, itemId, line?.displayName ?: itemName, replyTo = replyTo, onReplyDone = { replyTo = null })
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    if (editingPrice) {
        PriceDialog(
            initial = price,
            unit = unitHint(line?.quantity),
            onDismiss = { editingPrice = false },
            onSave = { vm.setPrice(itemId, it); editingPrice = false },
        )
    }

    if (editing && line != null) {
        EditLineDialog(
            line = line,
            onDismiss = { editing = false },
            onSave = { quantity, note ->
                if (quantity != line.quantity) vm.changeQuantity(line, quantity)
                if (note != line.note) vm.changeNote(line, note)
                editing = false
            },
            onDelete = { vm.delete(line); editing = false; onDismiss() },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text("$label: ", fontWeight = FontWeight.Bold)
        Text(value)
    }
}

/** "per unit" wording for the entered price, by the line's unit. */
private fun unitHint(q: app.necessityq.domain.Quantity?): String = when (q?.unit) {
    app.necessityq.domain.QuantityUnit.KILOGRAM, app.necessityq.domain.QuantityUnit.GRAM -> " לק\"ג"
    app.necessityq.domain.QuantityUnit.LITER, app.necessityq.domain.QuantityUnit.MILLILITER -> " לליטר"
    app.necessityq.domain.QuantityUnit.PACKAGE -> " לחבילה"
    else -> " ליחידה"
}

@Composable
private fun PriceDialog(initial: Double?, unit: String, onDismiss: () -> Unit, onSave: (Double?) -> Unit) {
    var text by remember { mutableStateOf(initial?.let { "%.2f".format(it) } ?: "") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("מחיר משוער$unit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    text, { text = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("₪") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                )
                Text(
                    "משמש לחישוב המחיר המשוער של הרשימה, עד שיחובר מאגר מחירים עדכני. משותף לכל בני הבית.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSave(text.toDoubleOrNull()) }) { Text("שמירה") } },
        dismissButton = {
            Row {
                if (initial != null) androidx.compose.material3.TextButton(onClick = { onSave(null) }) { Text("מחיקה") }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("ביטול") }
            }
        },
    )
}

/** Quick setting: sound on new messages. */
@Composable
fun ChatSoundToggle(vm: AppViewModel) {
    val on by vm.chatSound.collectAsStateWithLifecycle()
    IconButton(onClick = { vm.setChatSound(!on) }) {
        Icon(
            if (on) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = if (on) "צליל להודעות חדשות: פועל" else "צליל להודעות חדשות: כבוי",
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatThread(thread: List<ItemMessage>, vm: AppViewModel, onReply: (ItemMessage) -> Unit) {
    if (thread.isEmpty()) {
        Text(
            "אין עדיין הודעות. כתבו שאלה, צלמו תמונה או הקליטו הודעה — וכל בני הבית יראו.",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    val byId = thread.associateBy { it.id }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        thread.forEach { m ->
            val mine = m.authorId == vm.me
            Column(
                Modifier.fillMaxWidth(0.85f)
                    .align(if (mine) Alignment.Start else Alignment.End)
                    .background(
                        if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .combinedClickable(onClick = {}, onLongClick = { onReply(m) })
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (mine) "אני" else m.authorName} · ${fmt(m.atMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onReply(m) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "תגובה להודעה", Modifier.size(18.dp))
                    }
                }
                m.replyToId?.let { id ->
                    val quoted = byId[id]
                    Column(
                        Modifier.padding(end = 6.dp, bottom = 4.dp).fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            quoted?.let { if (it.authorId == vm.me) "אני" else it.authorName } ?: "הודעה",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(quoted?.preview ?: "ההודעה המקורית אינה זמינה", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
                when (m.kind) {
                    MessageKind.IMAGE -> m.mediaId?.let { ImageAttachment(vm, it) }
                    MessageKind.AUDIO -> m.mediaId?.let { AudioAttachment(vm, it, m.durationMs) }
                    MessageKind.TEXT -> Unit
                }
                if (m.text.isNotBlank()) Text(m.text, Modifier.padding(end = 6.dp))
                if (mine) Box(Modifier.align(Alignment.End).padding(end = 6.dp)) { DeliveryTicks(vm.deliveryStatus(m)) }
            }
        }
    }
}

@Composable
private fun ImageAttachment(vm: AppViewModel, mediaId: String) {
    var full by remember { mutableStateOf(false) }
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, mediaId) {
        value = vm.loadMedia(mediaId)?.bytes?.let { ImageCodec.decode(it) }?.asImageBitmap()
    }
    val bmp = image
    if (bmp == null) {
        Box(
            Modifier.padding(end = 6.dp).fillMaxWidth().height(140.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(24.dp)) }
        return
    }
    Image(
        bmp, contentDescription = "תמונה",
        modifier = Modifier.padding(end = 6.dp).fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(8.dp)).clickable { full = true },
        contentScale = ContentScale.Crop,
    )
    if (full) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { full = false }) {
            Image(bmp, contentDescription = "תמונה", modifier = Modifier.fillMaxWidth().clickable { full = false }, contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun AudioAttachment(vm: AppViewModel, mediaId: String, durationMs: Long?) {
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    DisposableEffect(mediaId) { onDispose { if (vm.player.playingId == mediaId) vm.player.stop() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (playing) {
                vm.player.stop(); playing = false
            } else {
                loading = true
                scope.launch {
                    val media = vm.loadMedia(mediaId)
                    loading = false
                    if (media == null) {
                        vm.reportError("לא ניתן לטעון את ההקלטה")
                    } else {
                        runCatching { vm.player.play(mediaId, media.bytes) { playing = false } }
                            .onSuccess { playing = true }
                            .onFailure { vm.reportError("ההשמעה נכשלה") }
                    }
                }
            }
        }) {
            when {
                loading -> CircularProgressIndicator(Modifier.size(20.dp))
                playing -> Icon(Icons.Default.Stop, contentDescription = "עצירה")
                else -> Icon(Icons.Default.PlayArrow, contentDescription = "השמעה")
            }
        }
        Text("🎤 " + (durationMs?.let { ItemMessage.formatDuration(it) } ?: "הקלטה"))
    }
}

/** Text box with picture, camera and voice; shows the message being answered above it. */
@Composable
private fun ChatComposer(vm: AppViewModel, itemId: String, itemName: String, replyTo: ItemMessage?, onReplyDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember(itemId) { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }

    fun sendImage(bitmap: android.graphics.Bitmap?) {
        if (bitmap == null) return
        scope.launch {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { ImageCodec.compress(bitmap) }
            vm.sendMedia(itemId, itemName, MessageKind.IMAGE, MediaBlob("image/jpeg", bytes), caption = draft, replyToId = replyTo?.id)
            draft = ""
            onReplyDone()
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            sendImage(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ImageCodec.fromUri(context, uri) })
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> sendImage(bitmap) }

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
            kotlinx.coroutines.delay(250)
            elapsed += 250
            if (elapsed >= app.necessityq.platform.VoiceRecorder.MAX_MS) {
                recording = false
                vm.recorder.stop()?.let { (bytes, ms) ->
                    vm.sendMedia(itemId, itemName, MessageKind.AUDIO, MediaBlob("audio/mp4", bytes), durationMs = ms, replyToId = replyTo?.id)
                    onReplyDone()
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { if (vm.recorder.isRecording) vm.recorder.stopQuietly() } }

    replyTo?.let { m ->
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text("תגובה ל" + (if (m.authorId == vm.me) "הודעה שלי" else m.authorName), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(m.preview, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            IconButton(onClick = onReplyDone) { Icon(Icons.Default.Close, contentDescription = "ביטול התגובה") }
        }
    }

    if (recording) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.recorder.stopQuietly(); recording = false }) { Icon(Icons.Default.Delete, contentDescription = "ביטול ההקלטה") }
            Text(
                "מקליט… " + ItemMessage.formatDuration(elapsed) + " / " + ItemMessage.formatDuration(app.necessityq.platform.VoiceRecorder.MAX_MS),
                Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = {
                recording = false
                val result = vm.recorder.stop()
                if (result == null) vm.reportError("ההקלטה קצרה מדי")
                else {
                    vm.sendMedia(itemId, itemName, MessageKind.AUDIO, MediaBlob("audio/mp4", result.first), durationMs = result.second, replyToId = replyTo?.id)
                    onReplyDone()
                }
            }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "שליחת ההקלטה") }
        }
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = "שליחת תמונה מהגלריה")
        }
        IconButton(onClick = { camera.launch(null) }) { Icon(Icons.Default.PhotoCamera, contentDescription = "צילום תמונה") }
        OutlinedTextField(
            draft, { draft = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(if (replyTo != null) "תגובה…" else "למשל: לקנות את הגדול?") },
            maxLines = 4,
        )
        if (draft.isBlank()) {
            IconButton(onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }) { Icon(Icons.Default.Mic, contentDescription = "הקלטת הודעה") }
        } else {
            IconButton(onClick = {
                vm.sendMessage(itemId, itemName, draft, replyTo?.id)
                draft = ""
                onReplyDone()
            }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "שליחה") }
        }
    }
}

// ---------- messages hub ----------

/** All product conversations in one place, newest first, with unread counts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(vm: AppViewModel, modifier: Modifier = Modifier, onOpen: (itemId: String, itemName: String) -> Unit) {
    val all by vm.itemMessages.collectAsStateWithLifecycle()
    vm.unreadTotal.collectAsStateWithLifecycle() // recompose when read markers change
    val threads = all.groupBy { it.itemId }.values
        .map { it.maxBy { m -> m.atMillis } to it }
        .sortedByDescending { it.first.atMillis }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { DrawerButton() },
            title = { Text("הודעות על מוצרים", fontWeight = FontWeight.ExtraBold) },
            actions = { ChatSoundToggle(vm) },
        )
        if (threads.isEmpty()) {
            Text(
                "כאן יופיעו כל השיחות על מוצרים. פותחים שיחה בהקשה על מוצר ברשימה.",
                Modifier.padding(16.dp),
            )
            return
        }
        LazyColumn {
            items(threads, key = { it.first.itemId }) { (last, _) ->
                val unread = vm.unreadCount(last.itemId, all)
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(last.itemId, last.itemName) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(last.itemName, style = ProductNameStyle)
                        Text(
                            "${if (last.authorId == vm.me) "אני" else last.authorName}: ${last.preview}",
                            maxLines = 2,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(fmt(last.atMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (unread > 0) Badge { Text("$unread") } else if (last.authorId == vm.me) DeliveryTicks(vm.deliveryStatus(last))
                }
                HorizontalDivider()
            }
        }
    }
}
