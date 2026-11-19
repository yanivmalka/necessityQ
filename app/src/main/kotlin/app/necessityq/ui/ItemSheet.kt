package app.necessityq.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.necessityq.domain.ConversationRetention
import app.necessityq.domain.MessageRules
import app.necessityq.domain.Waveform
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
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

private val timeFormat = DateTimeFormatter.ofPattern("d.M HH:mm")
private fun fmt(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat)

/**
 * Expanded information about one product plus its conversation. Works for an item that is on the list
 * now (with quick actions) and for one that is not (opened from the messages tab).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ItemSheet(
    vm: AppViewModel,
    itemId: String,
    itemName: String,
    onDismiss: () -> Unit,
    /** Opens another item's conversation (someone is typing there); null = not offered. */
    onOpenItem: ((itemId: String, itemName: String) -> Unit)? = null,
) {
    val typing by vm.typingNow.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val allMessages by vm.itemMessages.collectAsStateWithLifecycle()
    val names by vm.memberNames.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var confirmDeleteMessage by remember { mutableStateOf<ItemMessage?>(null) }
    var messageDetails by remember { mutableStateOf<ItemMessage?>(null) }
    var confirmDeleteChat by remember { mutableStateOf(false) }
    val chatStats by vm.chatStats.collectAsStateWithLifecycle()
    val archivedAll by vm.archivedMessages.collectAsStateWithLifecycle()
    var detailsOpen by remember(itemId) { mutableStateOf(false) }
    var pastOpen by remember(itemId) { mutableStateOf(false) }
    var confirmDeletePast by remember { mutableStateOf<List<ItemMessage>?>(null) }
    val pastThreads = archivedAll.filter { it.itemId == itemId }
        .groupBy { it.archivedAtMillis }.values
        .map { g -> g.sortedBy { it.atMillis } }
        .sortedByDescending { it.last().archivedAtMillis ?: 0L }

    val line = snapshot?.lines?.firstOrNull { it.itemId == itemId && it.status == LineStatus.NEEDED }
    val stats = snapshot?.stats?.get(itemId)
    val thread = allMessages.filter { it.itemId == itemId }
    val favorite = itemId in favorites
    val lastStore = purchases.firstOrNull { it.itemId == itemId && it.storeId != null }
        ?.storeId?.let { id -> stores.firstOrNull { it.id == id }?.name }

    // reading the sheet marks the conversation as read, also for messages that arrive while it is open
    LaunchedEffect(itemId, thread.size) { vm.markRead(itemId) }

    var replyTo by remember(itemId) { mutableStateOf<ItemMessage?>(null) }
    var editingMessage by remember(itemId) { mutableStateOf<ItemMessage?>(null) }
    val scroll = rememberScrollState()
    // a new message (sent or received) brings the end of the conversation into view, as in a chat app
    var seenMessages by remember(itemId) { androidx.compose.runtime.mutableIntStateOf(thread.size) }
    LaunchedEffect(thread.size) {
        if (thread.size > seenMessages) scroll.animateScrollTo(scroll.maxValue)
        seenMessages = thread.size
    }

    // the branch price list opens part-way; a tap on it or scrolling the page opens it fully
    var pricesOpen by remember(itemId) { mutableStateOf(false) }
    val openPricesOnScroll = remember(itemId) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0) pricesOpen = true
                return Offset.Zero
            }
        }
    }
    val scrolled by remember { androidx.compose.runtime.derivedStateOf { scroll.value > 0 } }
    // once there is a conversation, its latest messages stay docked above the message bar until the
    // conversation itself scrolls into view
    val density = LocalDensity.current
    var viewportBottom by remember { mutableFloatStateOf(0f) }
    var chatTop by remember(itemId) { mutableFloatStateOf(Float.MAX_VALUE) }
    val chatInView = chatTop < viewportBottom - with(density) { 140.dp.toPx() }
    val scope = rememberCoroutineScope()

    // a full page (not a sheet): it closes only with the back arrow or the system back, never by scrolling
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
      ItemPageWindow()
      Column(
          Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
              .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
      ) {
        // pinned while the page scrolls: back, the name with its actions, and the quantity values
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                }
                if (line != null) {
                    // bought: the same round check as on the list
                    Box(
                        Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable { vm.checkOff(line); onDismiss() }
                            .semantics { contentDescription = "נקנה" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(22.dp).border(2.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape))
                    }
                }
                if (favorite) Icon(Icons.Default.Star, "מועדף", Modifier.size(20.dp), tint = NqPalette.FavoriteStar)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        line?.displayName ?: itemName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).padding(horizontal = 4.dp),
                    )
                    if (line != null) {
                        IconButton(onClick = { editing = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "עריכת שם, כמות והערה", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // the exact product (for price only — the name stays as written); remembered for next time
                val scanProduct = rememberBarcodeScan(vm) { vm.linkScannedProduct(itemId, it) }
                IconButton(onClick = scanProduct, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "סריקת המוצר")
                }
                // ⋮ actions — on the left edge in Hebrew layout
                androidx.compose.foundation.layout.Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "פעולות") }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (favorite) "הסרה מהמועדפים" else "הוספה למועדפים") },
                            leadingIcon = { Icon(if (favorite) Icons.Default.StarBorder else Icons.Default.Star, null) },
                            onClick = { vm.toggleFavorite(itemId); menu = false },
                        )
                        if (line != null) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (line.missing) "ביטול חסר" else "חסר") },
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
                // quantity and its kind in one line; the kinds scroll sideways when they don't fit
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuantityStepper(line.quantity, onMinus = { vm.adjustQuantity(line, -1) }, onPlus = { vm.adjustQuantity(line, +1) }, onSet = { q, approved -> vm.changeQuantity(line, q, approved) },
                        itemName = line.displayName, check = { vm.quantityCheck(line, it) },
                    )
                    UnitChooser(line.quantity?.unit, Modifier.weight(1f)) { vm.setQuantityUnit(line, it) }
                }
            }
            if (scrolled) HorizontalDivider()
        }
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .onGloballyPositioned { viewportBottom = it.boundsInRoot().bottom }
                .nestedScroll(openPricesOnScroll)
                .verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (line != null) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.necessityq.domain.ItemTags.of(line, stats, favorite).forEach { TagBadge(it) }
                }
                app.necessityq.domain.QuantityNorms.warningFor(line, stats?.typicalQuantity)?.let { warning ->
                    LargeAmountNotice(warning.message(line.displayName)) { vm.changeQuantity(line, line.quantity, approved = true) }
                }
                // one row: missing (a toggle — filled while marked), remove
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val missingContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                        Icon(Icons.Default.ReportProblem, null, Modifier.padding(end = 4.dp).size(18.dp))
                        Text("חסר")
                    }
                    if (line.missing) {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { vm.setMissing(line, false) },
                            modifier = Modifier.semantics { contentDescription = "מסומן כחסר — ביטול" },
                            content = missingContent,
                        )
                    } else {
                        OutlinedButton(onClick = { vm.setMissing(line, true) }, content = missingContent)
                    }
                    OutlinedButton(onClick = { vm.delete(line); onDismiss() }) {
                        Icon(Icons.Default.Delete, null, Modifier.padding(end = 4.dp).size(18.dp))
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

            line?.note?.let { InfoLine("הערה", it) }

            BranchPriceSection(
                vm,
                itemId = itemId,
                name = line?.name ?: stats?.identity?.name ?: itemName,
                variant = line?.variant ?: stats?.identity?.variant,
                expanded = pricesOpen,
                onExpandedChange = { pricesOpen = it },
            )
            // the chosen product at every followed branch, like a stock ticker
            BranchQuotesStrip(
                vm,
                itemId = itemId,
                name = line?.name ?: stats?.identity?.name ?: itemName,
                variant = line?.variant ?: stats?.identity?.variant,
            )

            HorizontalDivider()
            // the household's rhythm, category, source and statistics: collapsed until asked for
            Row(
                Modifier.fillMaxWidth().clickable { detailsOpen = !detailsOpen },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("פרטים נוספים", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (detailsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (detailsOpen) "כיווץ" else "הרחבה")
            }
            if (detailsOpen) {
                stats?.let { s ->
                    RhythmCard(s, purchases.filter { it.itemId == itemId }.map { it.boughtAtMillis })
                }
                InfoLine("קטגוריה", (line?.category ?: stats?.identity?.category)?.label)
                line?.let { InfoLine("מקור", it.source.label) }
                line?.let { InfoLine(if (it.isAuto) "נוצר עבור" else "נוסף", "${names[it.addedBy] ?: "בן/בת בית"} · ${fmt(it.addedAtMillis)}") }
                stats?.let { s ->
                    val days = ChronoUnit.DAYS.between(s.lastPurchase, LocalDate.now())
                    InfoLine("נקנה לאחרונה", if (days <= 0) "היום" else "לפני $days ימים")
                    if (s.lastPurchaseEstimated) {
                        val est = ChronoUnit.DAYS.between(s.estimatedLastPurchase, LocalDate.now())
                        InfoLine("הערכה", "היו קניות שלא תועדו · כנראה נקנה לפני $est ימים")
                    }
                    // frequency and usual amount are in the rhythm card above
                    if (s.cadenceDays == null) InfoLine("תדירות", "אין עדיין מספיק היסטוריה")
                    InfoLine("נקנה", "${s.purchaseDays} פעמים")
                }
                InfoLine("נקנה לאחרונה ב", lastStore)
                chatStats[itemId]?.let {
                    InfoLine("שיחות שנמחקו", "${it.messages} הודעות ב-${it.threads.coerceAtLeast(1)} שיחות (נשמרו רק הספירות)")
                }
            }

            if (pastThreads.isNotEmpty()) {
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { pastOpen = !pastOpen },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("שיחות קודמות על המוצר (${pastThreads.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(if (pastOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (pastOpen) "כיווץ" else "הרחבה")
                }
                if (pastOpen) {
                    pastThreads.forEach { thread ->
                        Column(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, NqShapes.Panel).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "קנייה שהסתיימה ${fmt(thread.last().archivedAtMillis ?: 0L)} · ${thread.size} הודעות",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { confirmDeletePast = thread }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "מחיקת השיחה", Modifier.size(18.dp))
                                }
                            }
                            thread.forEach { m ->
                                Text(
                                    "${if (m.authorId == vm.me) "אני" else m.authorName} · ${fmt(m.atMillis)}" + (m.editedAtMillis?.let { " · נערכה ${fmt(it)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(m.preview, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                Modifier.onGloballyPositioned { chatTop = it.positionInRoot().y },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("שיחה על המוצר", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (thread.isNotEmpty()) {
                    IconButton(onClick = { confirmDeleteChat = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "מחיקת כל השיחה")
                    }
                }
                ChatSoundToggle(vm)
            }
            vm.receipts.collectAsStateWithLifecycle()
            // typing about another product: shown here too, one tap away
            val elsewhere = typing.filter { it.itemId != itemId }.groupBy { it.itemId }
            elsewhere.values.forEach { group ->
                val first = group.first()
                androidx.compose.material3.Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().then(
                        if (onOpenItem != null) Modifier.clickable { onOpenItem(first.itemId, first.itemName) } else Modifier,
                    ),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TypingDots()
                        Text(
                            "  " + app.necessityq.domain.Typing.label(group.map { it.name }).removeSuffix("…") + " על ${first.itemName}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (onOpenItem != null) Text("מעבר ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            ChatThread(
                thread, vm = vm,
                onReply = { replyTo = it; editingMessage = null },
                onEdit = { editingMessage = it; replyTo = null },
                onDelete = { confirmDeleteMessage = it },
                onDetails = { messageDetails = it },
            )
            typing.filter { it.itemId == itemId }.takeIf { it.isNotEmpty() }?.let { here ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypingDots()
                    Text("  " + app.necessityq.domain.Typing.label(here.map { it.name }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "השיחה שייכת לקנייה הנוכחית: כשהפריט נוקה מהרשימה היא עוברת ל\"שיחות מקניות קודמות\" בהודעות.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp))
        }
        AnimatedVisibility(visible = thread.isNotEmpty() && !chatInView) {
            DockedChatPeek(
                vm = vm,
                thread = thread,
                typingHere = typing.filter { it.itemId == itemId }.map { it.name },
                onOpen = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
            )
        }
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChatComposer(
                vm, itemId, line?.displayName ?: itemName,
                replyTo = replyTo, onReplyDone = { replyTo = null },
                editing = editingMessage, onEditDone = { editingMessage = null },
            )
        }
      }
    }

    confirmDeleteMessage?.let { m ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDeleteMessage = null },
            title = { Text("למחוק את ההודעה?") },
            text = { Text("ההודעה תימחק אצל כל בני הבית ולא ניתן לשחזר אותה.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.deleteMessage(m); confirmDeleteMessage = null }) { Text("מחיקה") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDeleteMessage = null }) { Text("ביטול") } },
        )
    }
    confirmDeletePast?.let { past ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDeletePast = null },
            title = { Text("למחוק את השיחה?") },
            text = { Text("${past.size} הודעות יימחקו אצל כל בני הבית, כולל תמונות והקלטות.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.deleteConversation(past); confirmDeletePast = null }) { Text("מחיקה") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDeletePast = null }) { Text("ביטול") } },
        )
    }
    if (confirmDeleteChat) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDeleteChat = false },
            title = { Text("למחוק את כל השיחה?") },
            text = { Text("${thread.size} הודעות על ${line?.displayName ?: itemName} יימחקו אצל כל בני הבית, כולל תמונות והקלטות.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.deleteConversation(thread); confirmDeleteChat = false }) { Text("מחיקה") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDeleteChat = false }) { Text("ביטול") } },
        )
    }
    messageDetails?.let { m -> MessageDetailsDialog(vm, m, onDismiss = { messageDetails = null }) }

    if (editing && line != null) {
        EditLineDialog(
            line = line,
            onDismiss = { editing = false },
            onSave = { name, quantity, note ->
                if (name != line.displayName) vm.renameLine(line, name)
                if (quantity != line.quantity) vm.changeQuantity(line, quantity)
                if (note != line.note) vm.changeNote(line, note)
                editing = false
            },
            onDelete = { vm.delete(line); editing = false; onDismiss() },
        )
    }
}

/** The latest messages of the conversation, docked above the message bar; a tap scrolls to the conversation. */
@Composable
private fun DockedChatPeek(vm: AppViewModel, thread: List<ItemMessage>, typingHere: List<String>, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "שיחה על המוצר · ${thread.size} הודעות",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ExpandLess, contentDescription = "מעבר לשיחה", tint = MaterialTheme.colorScheme.primary)
            }
            thread.takeLast(2).forEach { m ->
                val mine = m.authorId == vm.me
                Text(
                    "${if (mine) "אני" else m.authorName}: ${m.preview}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            NqShapes.Inset,
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (typingHere.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypingDots()
                    Text("  " + app.necessityq.domain.Typing.label(typingHere), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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

private val fullFormat = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
private fun fullFmt(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(fullFormat)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatThread(
    thread: List<ItemMessage>,
    vm: AppViewModel,
    onReply: (ItemMessage) -> Unit,
    onEdit: (ItemMessage) -> Unit,
    onDelete: (ItemMessage) -> Unit,
    onDetails: (ItemMessage) -> Unit,
) {
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
            androidx.compose.runtime.key(m.id) {
                val mine = m.authorId == vm.me
                var menu by remember { mutableStateOf(false) }
                Column(
                    Modifier.fillMaxWidth(0.85f)
                        .align(if (mine) Alignment.Start else Alignment.End)
                        .background(
                            if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            NqShapes.Panel,
                        )
                        .combinedClickable(onClick = {}, onLongClick = { menu = true })
                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildString {
                                append(if (mine) "אני" else m.authorName)
                                append(" · ").append(fmt(m.atMillis))
                                m.editedAtMillis?.let { append(" · נערכה ").append(fmt(it)) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onReply(m) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = "תגובה להודעה", Modifier.size(18.dp))
                        }
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "פעולות על ההודעה", Modifier.size(18.dp))
                            }
                            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("פרטי הודעה") },
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                    onClick = { menu = false; onDetails(m) },
                                )
                                val now = System.currentTimeMillis()
                                if (MessageRules.canEdit(m, vm.me, now)) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("עריכה (עוד ${MessageRules.minutesLeft(m, now)} דק')") },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                        onClick = { menu = false; onEdit(m) },
                                    )
                                }
                                if (MessageRules.canDelete(m, vm.me)) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("מחיקה") },
                                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                                        onClick = { menu = false; onDelete(m) },
                                    )
                                }
                            }
                        }
                    }
                    m.replyToId?.let { id ->
                        val quoted = byId[id]
                        Column(
                            Modifier.padding(end = 6.dp, bottom = 4.dp).fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), NqShapes.Inset)
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
                        MessageKind.AUDIO -> m.mediaId?.let { AudioAttachment(vm, it, m.durationMs, m.waveform) }
                        MessageKind.TEXT -> Unit
                    }
                    if (m.text.isNotBlank()) Text(m.text, Modifier.padding(end = 6.dp))
                    if (mine) Box(Modifier.align(Alignment.End).padding(end = 6.dp)) { DeliveryTicks(vm.deliveryStatus(m)) }
                }
            }
        }
    }
}

/** Who wrote the message, when it was sent / edited, and who received and read it. */
@Composable
private fun MessageDetailsDialog(vm: AppViewModel, m: ItemMessage, onDismiss: () -> Unit) {
    val names by vm.memberNames.collectAsStateWithLifecycle()
    vm.receipts.collectAsStateWithLifecycle() // recompose when someone reads it
    val receipts = vm.receiptDetails(m)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("פרטי הודעה") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                InfoLine("נכתבה על ידי", if (m.authorId == vm.me) "אני" else (names[m.authorId] ?: m.authorName))
                InfoLine("נשלחה", fullFmt(m.atMillis))
                m.editedAtMillis?.let { InfoLine("נערכה", fullFmt(it)) }
                if (m.pending) Text("ממתינה לשליחה (אין חיבור).", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("מי קיבל וקרא", fontWeight = FontWeight.Bold)
                if (receipts.isEmpty()) Text("אין עוד משתתפים בבית.", style = MaterialTheme.typography.bodySmall)
                receipts.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { DeliveryTicks(r.state) }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (r.userId == vm.me) "אני" else (names[r.userId] ?: "משתתף"),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                when (r.state) {
                                    app.necessityq.domain.DeliveryStatus.READ -> "קרא/ה · פתח/ה את השיחה ב-${fullFmt(r.atMillis ?: 0L)}"
                                    app.necessityq.domain.DeliveryStatus.DELIVERED -> "התקבלה · ${fullFmt(r.atMillis ?: 0L)}"
                                    else -> "נשלחה, טרם התקבלה"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("סגירה") } },
    )
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
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), NqShapes.Inset),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(24.dp)) }
        return
    }
    Image(
        bmp, contentDescription = "תמונה",
        modifier = Modifier.padding(end = 6.dp).fillMaxWidth().heightIn(max = 220.dp).clip(NqShapes.Inset).clickable { full = true },
        contentScale = ContentScale.Crop,
    )
    if (full) ImageViewer(bmp) { full = false }
}

/** Full-screen picture: fills the screen, pinch to zoom, drag to look around, double-tap to zoom in / out. */
@Composable
private fun ImageViewer(bmp: androidx.compose.ui.graphics.ImageBitmap, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var zoom by remember { mutableStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val z = (zoom * zoomChange).coerceIn(1f, 5f)
                        // keep the picture covering the screen: at most half the extra size each way
                        val maxX = size.width * (z - 1f) / 2f
                        val maxY = size.height * (z - 1f) / 2f
                        zoom = z
                        pan = Offset(
                            (pan.x + panChange.x).coerceIn(-maxX, maxX),
                            (pan.y + panChange.y).coerceIn(-maxY, maxY),
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoom > 1f) { zoom = 1f; pan = Offset.Zero } else zoom = 2.5f
                        },
                        onTap = { if (zoom == 1f) onClose() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bmp,
                contentDescription = "תמונה",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                },
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(AbsoluteAlignment.TopRight).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) { Icon(Icons.Default.Close, contentDescription = "סגירה", tint = Color.White) }
        }
    }
}

/** Play / stop, loudness bars with a dot at the running time (tap the bars to jump), and the time. */
@Composable
private fun AudioAttachment(vm: AppViewModel, mediaId: String, durationMs: Long?, waveform: List<Int>?) {
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    val total = durationMs ?: 0L
    val progress = if (playing && total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    // recordings from before the waveform existed get a neutral shape
    val bars = waveform ?: remember { List(Waveform.BARS) { 25 + (it * 7 % 5) * 8 } }

    LaunchedEffect(playing) {
        while (playing) {
            position = vm.player.position()
            kotlinx.coroutines.delay(60)
        }
        position = 0L
    }
    DisposableEffect(mediaId) { onDispose { if (vm.player.playingId == mediaId) vm.player.stop() } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
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
        Canvas(
            Modifier.weight(1f).height(36.dp).pointerInput(playing, total, rtl) {
                detectTapGestures { offset ->
                    if (playing && total > 0) {
                        val f = (offset.x / size.width).coerceIn(0f, 1f).let { if (rtl) 1f - it else it }
                        vm.player.seekTo((f * total).toInt())
                    }
                }
            },
        ) {
            val n = bars.size
            val slot = size.width / n
            val barW = slot * 0.55f
            fun xOf(t: Float) = (if (rtl) 1f - t else t) * size.width
            bars.forEachIndexed { i, level ->
                val t = (i + 0.5f) / n
                val h = (size.height * level / 100f).coerceAtLeast(4.dp.toPx())
                drawRoundRect(
                    color = if (t <= progress) played else rest,
                    topLeft = Offset(xOf(t) - barW / 2, (size.height - h) / 2),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2),
                )
            }
            val r = 6.dp.toPx()
            drawCircle(played, radius = r, center = Offset(xOf(progress).coerceIn(r, size.width - r), size.height / 2))
        }
        Text(
            if (playing) ItemMessage.formatDuration(position) + " / " + ItemMessage.formatDuration(total) else ItemMessage.formatDuration(total),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Text box with picture, camera and voice; shows the message being answered or edited above it. */
@Composable
private fun ChatComposer(
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
    var draft by remember(itemId) { mutableStateOf("") }
    // leaving the conversation (or switching product) ends our "typing…"
    androidx.compose.runtime.DisposableEffect(itemId) { onDispose { vm.stopTyping() } }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }

    // starting an edit puts the message text in the box
    LaunchedEffect(editing?.id) { if (editing != null) draft = editing.text }

    fun sendImage(bitmap: android.graphics.Bitmap?) {
        if (bitmap == null) return
        scope.launch {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { ImageCodec.compress(bitmap) }
            vm.sendMedia(itemId, itemName, MessageKind.IMAGE, MediaBlob("image/jpeg", bytes), caption = draft, replyToId = replyTo?.id)
            draft = ""
            onReplyDone()
        }
    }

    fun sendRecording(r: app.necessityq.platform.VoiceRecorder.Recording) {
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
    // while the camera app is open
    var cameraPath by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
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
            kotlinx.coroutines.delay(app.necessityq.platform.VoiceRecorder.SAMPLE_MS)
            vm.recorder.sample()
            elapsed += app.necessityq.platform.VoiceRecorder.SAMPLE_MS
            if (elapsed >= app.necessityq.platform.VoiceRecorder.MAX_MS) {
                recording = false
                vm.recorder.stop()?.let { sendRecording(it) }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { if (vm.recorder.isRecording) vm.recorder.stopQuietly() } }

    replyTo?.let { m ->
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, NqShapes.Strip)
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
    editing?.let { m ->
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer, NqShapes.Strip)
                .padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text("עריכת הודעה", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(
                    "אפשר לערוך עוד ${MessageRules.minutesLeft(m, System.currentTimeMillis())} דק'",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = { draft = ""; onEditDone() }) { Icon(Icons.Default.Close, contentDescription = "ביטול העריכה") }
        }
    }

    if (recording) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = { vm.recorder.stopQuietly(); recording = false }) {
                        Icon(Icons.Default.Delete, contentDescription = "ביטול ההקלטה", tint = MaterialTheme.colorScheme.error)
                    }
                    Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape))
                    Text(
                        "  " + ItemMessage.formatDuration(elapsed) + " / " + ItemMessage.formatDuration(app.necessityq.platform.VoiceRecorder.MAX_MS),
                        Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
            ComposerAction(Icons.AutoMirrored.Filled.Send, "שליחת ההקלטה") {
                recording = false
                val result = vm.recorder.stop()
                if (result == null) vm.reportError("ההקלטה קצרה מדי") else sendRecording(result)
            }
        }
        return
    }

    // WhatsApp-style bar: a rounded box with the text and the picture buttons, and beside it one
    // round button that records when the box is empty and sends once something is typed
    Row(verticalAlignment = Alignment.Bottom) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                androidx.compose.material3.TextField(
                    draft, { draft = it; vm.onDraftChanged(itemId, itemName, it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (editing != null) "עריכה…" else if (replyTo != null) "תגובה…" else "הודעה") },
                    maxLines = 5,
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
                if (editing == null) {
                    IconButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "שליחת תמונה מהגלריה", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // as in WhatsApp, the camera makes room once typing starts
                    if (draft.isBlank()) {
                        IconButton(onClick = { openCamera() }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "צילום תמונה", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        when {
            editing != null -> ComposerAction(
                Icons.Default.Check, "שמירת העריכה",
                enabled = draft.isNotBlank() || editing.kind != MessageKind.TEXT,
            ) { vm.editMessage(editing, draft); draft = ""; onEditDone() }
            draft.isBlank() -> ComposerAction(Icons.Default.Mic, "הקלטת הודעה") {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> ComposerAction(Icons.AutoMirrored.Filled.Send, "שליחה") {
                vm.sendMessage(itemId, itemName, draft, replyTo?.id)
                draft = ""
                onReplyDone()
            }
        }
    }
}

/** The round button beside the message box (record / send / save), as in WhatsApp. */
@Composable
private fun ComposerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    androidx.compose.material3.FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
    ) {
        Icon(icon, contentDescription = description)
    }
}

// ---------- messages hub ----------

private val dayFormat = DateTimeFormatter.ofPattern("d.M")
private fun fmtDay(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dayFormat)

private fun retentionText(days: Int?): String = when {
    days == null -> ""
    days < 0 -> "שיחות שהסתיימו נשמרות ללא הגבלה."
    days == 0 -> "שיחות שהסתיימו נמחקות מיד; נשארות רק סטטיסטיקות."
    else -> "שיחות שהסתיימו נשמרות $days ימים ואז נמחקות; נשארות רק סטטיסטיקות."
}

/**
 * All product conversations of the current shopping, newest first, with unread counts — and below them
 * the conversations of finished shoppings, which wait out the retention period before they are deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(vm: AppViewModel, modifier: Modifier = Modifier, onOpen: (itemId: String, itemName: String) -> Unit) {
    val typing by vm.typingNow.collectAsStateWithLifecycle()
    fun typingHere(id: String): String? =
        typing.filter { it.itemId == id }.takeIf { it.isNotEmpty() }?.let { t -> app.necessityq.domain.Typing.label(t.map { it.name }) }
    val all by vm.itemMessages.collectAsStateWithLifecycle()
    val archived by vm.archivedMessages.collectAsStateWithLifecycle()
    val retention by vm.chatRetention.collectAsStateWithLifecycle()
    vm.unreadTotal.collectAsStateWithLifecycle() // recompose when read markers change
    val threads = all.groupBy { it.itemId }.values
        .map { it.maxBy { m -> m.atMillis } to it }
        .sortedByDescending { it.first.atMillis }
    val archivedThreads = archived.groupBy { it.itemId to it.archivedAtMillis }.values
        .map { group -> group.sortedBy { it.atMillis } }
        .sortedByDescending { it.last().archivedAtMillis ?: 0L }
    var showRetention by remember { mutableStateOf(false) }
    var openArchived by remember { mutableStateOf<List<ItemMessage>?>(null) }
    var confirmDelete by remember { mutableStateOf<List<ItemMessage>?>(null) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { BackNavButton() },
            title = { Text("הודעות על מוצרים", fontWeight = FontWeight.ExtraBold) },
            actions = {
                IconButton(onClick = { showRetention = true }) { Icon(Icons.Default.Schedule, contentDescription = "משך שמירת שיחות שהסתיימו") }
                ChatSoundToggle(vm)
            },
        )
        LazyColumn {
            if (threads.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "כאן יופיעו שיחות הקנייה הנוכחית. פותחים שיחה בהקשה על מוצר ברשימה.",
                        Modifier.padding(16.dp),
                    )
                }
            }
            items(threads, key = { it.first.itemId }) { (last, _) ->
                val unread = vm.unreadCount(last.itemId, all)
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(last.itemId, last.itemName) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(last.itemName, style = ProductNameStyle)
                        typingHere(last.itemId)?.let { label ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TypingDots()
                                Text("  $label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
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
            if (archivedThreads.isNotEmpty()) {
                item(key = "archived_title") {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)) {
                        Text("שיחות מקניות קודמות (${archivedThreads.size})", style = MaterialTheme.typography.titleSmall)
                        Text(retentionText(retention), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(archivedThreads, key = { "arch_${it.first().itemId}_${it.last().archivedAtMillis}" }) { thread ->
                    val last = thread.last()
                    Row(
                        Modifier.fillMaxWidth().clickable { openArchived = thread }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(last.itemName, style = MaterialTheme.typography.titleMedium)
                            Text("${last.authorName}: ${last.preview}", maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                            val endedAt = last.archivedAtMillis ?: 0L
                            val deleteAt = retention?.let { ConversationRetention.deleteAt(endedAt, it) }
                            Text(
                                "${thread.size} הודעות · הסתיימה ${fmtDay(endedAt)}" + (deleteAt?.let { " · תימחק ב-${fmtDay(it)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { confirmDelete = thread }) { Icon(Icons.Default.Delete, contentDescription = "מחיקת השיחה") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    openArchived?.let { thread ->
        ModalBottomSheet(onDismissRequest = { openArchived = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(thread.last().itemName, style = MaterialTheme.typography.titleLarge)
                thread.forEach { m ->
                    Column {
                        Text(
                            "${if (m.authorId == vm.me) "אני" else m.authorName} · ${fmt(m.atMillis)}" +
                                (m.editedAtMillis?.let { " · נערכה ${fmt(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(m.preview)
                    }
                }
                OutlinedButton(onClick = { confirmDelete = thread; openArchived = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("מחיקת השיחה")
                }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
            }
        }
    }
    confirmDelete?.let { thread ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("למחוק את השיחה?") },
            text = { Text("${thread.size} הודעות על ${thread.last().itemName} יימחקו אצל כל בני הבית, כולל תמונות והקלטות.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.deleteConversation(thread); confirmDelete = null }) { Text("מחיקה") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) { Text("ביטול") } },
        )
    }
    if (showRetention) {
        val options = listOf(
            0 to "מיד כשהקנייה מסתיימת",
            7 to "שבוע",
            30 to "30 ימים",
            90 to "90 ימים",
            -1 to "ללא הגבלה",
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRetention = false },
            title = { Text("כמה זמן לשמור שיחות שהסתיימו?") },
            text = {
                Column {
                    Text(
                        "שיחה שייכת לקנייה. כשהפריט נוקה מהרשימה (או שהרשימה אופסה) השיחה יוצאת מהרשימה, " +
                            "ואחרי הזמן שנבחר היא נמחקת ונשארים רק נתונים סטטיסטיים: כמה הודעות ושיחות היו על כל מוצר. ההגדרה משותפת לכל בני הבית.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    options.forEach { (days, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.setChatRetention(days); showRetention = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = retention == days, onClick = { vm.setChatRetention(days); showRetention = false })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showRetention = false }) { Text("סגירה") } },
        )
    }
}


/**
 * What the item costs at the household's primary branch. By default only the typical range of the
 * matching products ("קוטג'" → ₪5.90–₪7.80); choosing an exact product is optional and tucked away.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BranchPriceSection(
    vm: AppViewModel,
    itemId: String,
    name: String,
    variant: String?,
    /** false = a peek at the first products; a tap (or scrolling the page) opens the full list */
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val primary by vm.primaryBranch.collectAsStateWithLifecycle()
    val infos by vm.itemPriceInfo.collectAsStateWithLifecycle()
    val loaded by vm.branchPrices.collectAsStateWithLifecycle()
    val branch = primary ?: return
    val comparisons by vm.itemComparisons.collectAsStateWithLifecycle()
    val followed by vm.selectedPriceBranches.collectAsStateWithLifecycle()
    val allBranches by vm.priceBranches.collectAsStateWithLifecycle()
    // an item not on the list is matched in the background (searching a branch's products takes a moment)
    val fallback by produceState<app.necessityq.domain.ItemPriceInfo?>(null, loaded, infos, branch.id, itemId) {
        value = if (infos[itemId] != null) null else vm.priceInfoFor(itemId, name, variant)
    }
    val info = infos[itemId] ?: fallback
    val comparedFallback by produceState<app.necessityq.domain.PriceComparison?>(null, loaded, info, comparisons, itemId) {
        value = if (comparisons[itemId] != null || info == null) null else vm.comparisonFor(itemId, name, variant)
    }
    val comparison = comparisons[itemId] ?: comparedFallback
    var brand by remember(itemId) { mutableStateOf<String?>(null) }
    var pct by remember(itemId) { mutableStateOf<String?>(null) }
    var showAll by remember(itemId) { mutableStateOf(false) }

    HorizontalDivider()
    Row(Modifier.fillMaxWidth().clickable(enabled = info != null) { onExpandedChange(!expanded) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("מחיר ב${branch.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                when {
                    !loaded.containsKey(branch.id) -> "המחירים של הסניף נטענים…"
                    info == null -> "לא נמצאו מוצרים מתאימים בסניף"
                    info.preferred != null -> "${app.necessityq.domain.PriceData.formatPrice(info.preferred!!.price)} · ${info.preferred!!.name}"
                    else -> "${app.necessityq.domain.PriceData.formatRange(info.low, info.high)} · ${info.candidates.size} מוצרים"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (info != null) {
            Text(
                if (expanded) "כווץ הכל" else "פתח הכל",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
    // against the other followed branches; with a chosen product the per-branch strip below says the same
    if (comparison != null && info?.preferred == null) {
        BranchComparison(comparison, allBranches)
    } else if (info != null && info.preferred == null && followed.size > 1 && followed.any { it != branch.id && !loaded.containsKey(it) }) {
        Text("טוען את המחירים של הסניפים האחרים שלך להשוואה…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (info == null || info.candidates.isEmpty()) return

    if (!expanded) {
        // part-way open: the chosen product first, then the next ones, fading out; a tap opens the list
        val peek = (listOfNotNull(info.preferred) + info.candidates.filter { it.code != info.preferred?.code }).take(3)
        val fadeTo = MaterialTheme.colorScheme.surface // the page's background
        Box(Modifier.fillMaxWidth().clickable { onExpandedChange(true) }) {
            Column {
                peek.forEach { c -> BranchProductRow(c, chosen = info.preferred?.code == c.code, onChoose = null) }
            }
            if (info.candidates.size > peek.size) {
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to fadeTo)))
            }
        }
        Text(
            if (info.candidates.size > peek.size) "עוד ${info.candidates.size - peek.size} מוצרים · נגיעה להרחבה" else "נגיעה לבחירת מוצר",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clickable { onExpandedChange(true) }.padding(vertical = 2.dp),
        )
        return
    }

    Text("דיוק לפי מוצר (לא חובה) — המוצר שתבחרו יוצג ברשימה במקום הטווח.", style = MaterialTheme.typography.bodySmall)
    val brands = remember(info) { app.necessityq.domain.ItemPrices.brands(info.candidates) }
    val percents = remember(info) { app.necessityq.domain.ItemPrices.percents(info.candidates) }
    if (percents.size > 1) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            percents.forEach { p -> androidx.compose.material3.FilterChip(selected = pct == p, onClick = { pct = if (pct == p) null else p }, label = { Text(p) }) }
        }
    }
    if (brands.size > 1) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            brands.take(12).forEach { b -> androidx.compose.material3.FilterChip(selected = brand == b, onClick = { brand = if (brand == b) null else b }, label = { Text(b) }) }
        }
    }
    val filtered = info.candidates.filter { c ->
        (brand == null || c.brand.trim() == brand) && (pct == null || app.necessityq.domain.ItemPrices.hasPercent(c, pct!!))
    }
    val shown = if (showAll) filtered else filtered.take(15)
    shown.forEach { c ->
        val chosen = info.preferred?.code == c.code
        BranchProductRow(c, chosen, onChoose = { vm.setPreferredProduct(itemId, if (chosen) null else c.code) })
    }
    if (filtered.size > shown.size) {
        androidx.compose.material3.TextButton(onClick = { showAll = true }) { Text("הצגת כל ${filtered.size} המוצרים") }
    }
    if (info.preferred != null) {
        androidx.compose.material3.TextButton(onClick = { vm.setPreferredProduct(itemId, null) }) { Text("ביטול הבחירה — הצגת טווח בלבד") }
    }
}

/** The item page's own window: drawn edge to edge with no dimming, status-bar icons readable on the page. */
@Composable
private fun ItemPageWindow() {
    val view = androidx.compose.ui.platform.LocalView.current
    val lightPage = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    androidx.compose.runtime.SideEffect {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: return@SideEffect
        window.setDimAmount(0f)
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightPage
            isAppearanceLightNavigationBars = lightPage
        }
    }
}

/**
 * The household's chosen product at each followed branch, side by side (scrolling sideways when needed):
 * price, and like a stock ticker the last change — red ▲ when it went up, green ▼ when it went down.
 * Shown only once a product is chosen for the item.
 */
@Composable
private fun BranchQuotesStrip(vm: AppViewModel, itemId: String, name: String, variant: String?) {
    val primary by vm.primaryBranch.collectAsStateWithLifecycle()
    val infos by vm.itemPriceInfo.collectAsStateWithLifecycle()
    val loaded by vm.branchPrices.collectAsStateWithLifecycle()
    val followed by vm.selectedPriceBranches.collectAsStateWithLifecycle()
    val all by vm.priceBranches.collectAsStateWithLifecycle()
    val branch = primary ?: return
    val fallback by produceState<app.necessityq.domain.ItemPriceInfo?>(null, loaded, infos, branch.id, itemId) {
        value = if (infos[itemId] != null) null else vm.priceInfoFor(itemId, name, variant)
    }
    val chosen = (infos[itemId] ?: fallback)?.preferred ?: return
    // the other followed branches are downloaded only once there is a chosen product to compare
    LaunchedEffect(followed) { followed.forEach { vm.loadPrices(it) } }
    val quotes = remember(chosen, loaded, followed, all, branch.id) {
        // the branch the list shows first, then the others in the order they were followed
        val branches = (listOf(branch.id) + followed).distinct().mapNotNull { id -> all.firstOrNull { it.id == id } ?: branch.takeIf { it.id == id } }
        app.necessityq.domain.PriceData.quotes(chosen.code, chosen.internalCode, branch.chainKey, branches, loaded)
    }
    if (quotes.isEmpty()) return
    val cheapest = quotes.minOf { it.price }

    Text(
        "${chosen.name} בסניפים שבמעקב",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        quotes.forEach { q ->
            val here = q.branch.id == branch.id
            Column(
                Modifier.widthIn(min = 104.dp, max = 168.dp).clip(NqShapes.Panel)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (here) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, NqShapes.Panel) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(q.branch.chainName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(q.branch.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.necessityq.domain.PriceData.formatPrice(q.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    q.change?.let { c ->
                        val up = c > 0
                        val tint = if (up) NqPalette.PriceUp else NqPalette.PriceDown
                        Icon(
                            if (up) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = if (up) "עלה" else "ירד",
                            tint = tint,
                        )
                        Text(app.necessityq.domain.PriceData.formatPrice(kotlin.math.abs(c)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tint)
                    }
                }
                if (quotes.size > 1 && q.price == cheapest) {
                    Text("הכי זול", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** One product of the branch: name, brand (and past range), price; choosable when [onChoose] is given. */
@Composable
private fun BranchProductRow(c: app.necessityq.domain.BranchProduct, chosen: Boolean, onChoose: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onChoose != null) Modifier.clickable(onClick = onChoose) else Modifier).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onChoose != null) androidx.compose.material3.RadioButton(selected = chosen, onClick = onChoose)
        Column(Modifier.weight(1f)) {
            Text(c.name, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal, maxLines = if (onChoose == null) 1 else Int.MAX_VALUE)
            val range = if (c.high > c.low) " · היה ${app.necessityq.domain.PriceData.formatRange(c.low, c.high)}" else ""
            Text(listOf(c.brand, range.removePrefix(" · ")).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        Text(app.necessityq.domain.PriceData.formatPrice(c.price), fontWeight = FontWeight.Bold)
    }
}

/**
 * The item here against the household's other followed branches: a verdict pill (significantly dearer / cheaper,
 * or about the same), then each branch's price with the cheapest marked.
 */
@Composable
private fun BranchComparison(c: app.necessityq.domain.PriceComparison, branches: List<app.necessityq.domain.PriceBranch>) {
    fun title(id: String) = branches.firstOrNull { it.id == id }?.title ?: id
    fun price(q: app.necessityq.domain.BranchPriceRange) = app.necessityq.domain.PriceData.formatRange(q.low, q.high)
    val cheapest = c.cheapestBranchId
    Column(
        Modifier.fillMaxWidth().clip(NqShapes.Panel).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("השוואה לסניפים שלך", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            PriceComparisonPill(c)
        }
        (listOf(c.here) + c.others).forEach { q ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title(q.branchId),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (q.branchId == c.branchId) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (q.branchId == cheapest && c.others.isNotEmpty()) {
                    Text("הזול  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Text(price(q), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            when (c.basis) {
                app.necessityq.domain.CompareBasis.SAME_PRODUCT -> "לפי אותו מוצר (ברקוד) בכל סניף"
                app.necessityq.domain.CompareBasis.SHARED_PRODUCTS -> "לפי המוצרים שנמכרים גם כאן וגם בסניפים האחרים"
                app.necessityq.domain.CompareBasis.TYPICAL -> "לפי המחיר הטיפוסי של המוצרים המתאימים בכל סניף"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Kind of quantity: none / piece / package / kilo / gram / litre (millilitres only once in use). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun UnitChooser(
    current: app.necessityq.domain.QuantityUnit?,
    modifier: Modifier = Modifier,
    onChoose: (app.necessityq.domain.QuantityUnit?) -> Unit,
) {
    val label = { u: app.necessityq.domain.QuantityUnit -> if (u == app.necessityq.domain.QuantityUnit.PACKAGE) "מארז" else u.label }
    val shown = app.necessityq.domain.QuantitySteps.choices.filter { it != app.necessityq.domain.QuantityUnit.MILLILITER || current == it }
    // one line: scrolls sideways rather than wrapping
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.material3.FilterChip(selected = current == null, onClick = { onChoose(null) }, label = { Text("ללא") })
        shown.forEach { u ->
            androidx.compose.material3.FilterChip(selected = current == u, onClick = { onChoose(u) }, label = { Text(label(u)) })
        }
    }
}

/** Three dots rising and fading in turn — the "typing…" animation. */
@Composable
fun TypingDots(color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.keyframes {
                        durationMillis = 1_200
                        0f at 0
                        1f at 300
                        0f at 600
                    },
                    initialStartOffset = androidx.compose.animation.core.StartOffset(i * 200),
                ),
                label = "dot$i",
            )
            androidx.compose.foundation.layout.Box(
                Modifier
                    .padding(bottom = (phase * 4).dp)
                    .size(6.dp)
                    .alpha(0.35f + 0.65f * phase)
                    .background(color, androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}

/** "More than usual" on the item page: change the amount above, or confirm it is meant (the list tag then goes). */
@Composable
private fun LargeAmountNotice(message: String, onConfirm: () -> Unit) {
    val (bg, fg) = NqTheme.colors.tag(app.necessityq.domain.TagKind.LARGE_AMOUNT)
    Row(
        Modifier.fillMaxWidth().clip(NqShapes.Panel).background(bg).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = fg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = onConfirm) { Text("כן, זו הכמות", color = fg, fontWeight = FontWeight.Bold) }
    }
}
