package app.necessityq.ui

import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import app.necessityq.ui.icons.Icons
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
 * The product page: the quantity, the branch's products and prices (with the brand chooser), the comparison with the
 * household's other branches, then "חסר", "הסרה" and the product's צ'אט פוד — which opens on a page of its own
 * ([ChatFoodPage]). Works for an item that is on the list now and for one that is not.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ItemSheet(
    vm: AppViewModel,
    itemId: String,
    itemName: String,
    onDismiss: () -> Unit,
) {
    // the page slides in from the left and, on closing, slides back out before it is removed
    val pageWindow = rememberSlidingPage(onClosed = onDismiss)
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val allMessages by vm.itemMessages.collectAsStateWithLifecycle()
    val names by vm.memberNames.collectAsStateWithLifecycle()
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val chatStats by vm.chatStats.collectAsStateWithLifecycle()
    val archivedAll by vm.archivedMessages.collectAsStateWithLifecycle()
    var detailsOpen by remember(itemId) { mutableStateOf(false) }
    var pastOpen by remember(itemId) { mutableStateOf(false) }
    var pastChat by remember(itemId) { mutableStateOf<List<ItemMessage>?>(null) }
    var confirmDeletePast by remember { mutableStateOf<List<ItemMessage>?>(null) }
    /** The צ'אט פוד page open over this one: this product's, or another one reached from a "typing…" banner. */
    var chat by remember(itemId) { mutableStateOf<Pair<String, String>?>(null) }
    val pastThreads = archivedAll.filter { it.itemId == itemId }
        .groupBy { it.archivedAtMillis }.values
        .map { g -> g.sortedBy { it.atMillis } }
        .sortedByDescending { it.last().archivedAtMillis ?: 0L }

    val line = snapshot?.lines?.firstOrNull { it.itemId == itemId && it.status == LineStatus.NEEDED }
    val stats = snapshot?.stats?.get(itemId)
    val thread = allMessages.filter { it.itemId == itemId }
    val unread = vm.unreadCount(itemId, allMessages)
    val favorite = itemId in favorites
    val lastStore = purchases.firstOrNull { it.itemId == itemId && it.storeId != null }
        ?.storeId?.let { id -> stores.firstOrNull { it.id == id }?.name }
    val scroll = rememberScrollState()
    val scrolled by remember { androidx.compose.runtime.derivedStateOf { scroll.value > 0 } }
    val productName = line?.name ?: stats?.identity?.name ?: itemName
    val productVariant = line?.variant ?: stats?.identity?.variant

    // a full page (not a sheet): it closes only with the back arrow or the system back, never by scrolling
    SlidingPageDialog(pageWindow) {
      ItemPageWindow()
      Column(
          Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
              .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
      ) {
        // pinned while the page scrolls: back, the name with its actions, and the quantity values
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = pageWindow::close) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                }
                // "נקנה" is a labelled button in the row below, away from the back arrow (no marking by mistake)
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
                    // what has a button of its own on the page (נקנה, חסר, הסרה, צ'אט פוד, the pencil) is not repeated here
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (favorite) "הסרה מהמועדפים" else "הוספה למועדפים") },
                            leadingIcon = { Icon(if (favorite) Icons.Default.StarBorder else Icons.Default.Star, null) },
                            onClick = { vm.toggleFavorite(itemId); menu = false },
                        )
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
                    UnitChooser(line.quantity?.unit, app.necessityq.domain.QuantitySteps.choicesFor(line.name, line.category, line.quantity?.unit), Modifier.weight(1f)) { vm.setQuantityUnit(line, it) }
                }
            }
            if (scrolled) HorizontalDivider()
        }
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // first, in one row: bought, missing (a toggle — filled while marked), remove, and the product's צ'אט פוד
            val compact = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (line != null) {
                    Button(onClick = { vm.checkOff(line); pageWindow.close() }, contentPadding = compact) {
                        Icon(Icons.Default.Check, null, Modifier.padding(end = 4.dp).size(18.dp))
                        Text("נקנה", maxLines = 1, softWrap = false)
                    }
                    val missingContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                        Icon(Icons.Default.ReportProblem, null, Modifier.padding(end = 4.dp).size(18.dp))
                        Text("חסר", maxLines = 1, softWrap = false)
                    }
                    if (line.missing) {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { vm.setMissing(line, false) },
                            contentPadding = compact,
                            modifier = Modifier.semantics { contentDescription = "מסומן כחסר — ביטול" },
                            content = missingContent,
                        )
                    } else {
                        OutlinedButton(onClick = { vm.setMissing(line, true) }, contentPadding = compact, content = missingContent)
                    }
                    OutlinedButton(onClick = { vm.delete(line); pageWindow.close() }, contentPadding = compact) {
                        Icon(Icons.Default.Delete, null, Modifier.padding(end = 4.dp).size(18.dp))
                        Text("הסרה", maxLines = 1, softWrap = false)
                    }
                } else {
                    Button(onClick = { vm.addByName(itemName) }, contentPadding = compact) { Text("הוספה לרשימה", maxLines = 1, softWrap = false) }
                }
                // צ'אט פוד can be turned off (הגדרות ← פיצ'רים)
                val features by vm.features.collectAsStateWithLifecycle()
                if (app.necessityq.domain.Feature.CHAT in features) {
                    OutlinedButton(
                        onClick = { chat = itemId to (line?.displayName ?: itemName) },
                        contentPadding = compact,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "צ'אט פוד על המוצר" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.padding(end = 4.dp).size(18.dp))
                        ChatFoodButtonLabel(messages = thread.size, unread = unread)
                    }
                }
            }
            // then: the products that can be bought for it and their prices, with the brand chooser
            val priceInfo = rememberItemPriceInfo(vm, itemId, productName, productVariant)
            BranchPriceSection(vm, itemId = itemId, info = priceInfo)
            // the chosen product at every followed branch, like a stock ticker
            BranchQuotesStrip(vm, chosen = priceInfo?.preferred)

            HorizontalDivider()
            if (line != null) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.necessityq.domain.ItemTags.of(line, stats, favorite).forEach { TagBadge(it) }
                }
                app.necessityq.domain.QuantityNorms.warningFor(line, stats?.typicalQuantity)?.let { warning ->
                    LargeAmountNotice(warning.message(line.displayName)) { vm.changeQuantity(line, line.quantity, approved = true) }
                }
            }
            if (line?.missing == true) {
                Text(
                    "מסומן כחסר — בני הבית שנבחרו לקבל התראות יקבלו הודעה (בלשונית הבית ← הגדרות ← התראות).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            line?.note?.let { InfoLine("הערה", it) }

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
                    pastThreads.forEach { past ->
                        Column(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, NqShapes.Panel).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "קנייה שהסתיימה ${fmt(past.last().archivedAtMillis ?: 0L)} · ${past.size} הודעות",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { confirmDeletePast = past }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "מחיקת השיחה", Modifier.size(18.dp))
                                }
                            }
                            val last = past.last()
                            Text(
                                "${if (last.authorId == vm.me) "אני" else last.authorName}: ${last.preview}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            androidx.compose.material3.TextButton(onClick = { pastChat = past }) { Text("פתיחת השיחה ›") }
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp))
        }
      }
    }

    chat?.let { (id, name) ->
        ChatFoodPage(
            vm, itemId = id, itemName = name,
            onDismiss = { chat = null },
            onOpenItem = { otherId, otherName -> chat = otherId to otherName },
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
    pastChat?.let { past ->
        ArchivedChatScreen(vm, past, onDelete = { confirmDeletePast = past; pastChat = null }, onDismiss = { pastChat = null })
    }

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
            onDelete = { vm.delete(line); editing = false; pageWindow.close() },
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

/** Who wrote the message, when it was sent / edited, and who received and read it. */
@Composable
internal fun MessageDetailsDialog(vm: AppViewModel, m: ItemMessage, onDismiss: () -> Unit) {
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
internal fun ImageAttachment(vm: AppViewModel, mediaId: String) {
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

// ---------- messages hub ----------

private val dayFormat = DateTimeFormatter.ofPattern("d.M")
private fun fmtDay(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dayFormat)

private fun retentionText(days: Int?): String = when {
    days == null -> ""
    days < 0 -> "שיחות שהסתיימו נשמרות ללא הגבלה."
    days == 0 -> "שיחות שהסתיימו נמחקות מיד; נשארות רק סטטיסטיקות."
    else -> "שיחות שהסתיימו נשמרות $days ימים ואז נמחקות; נשארות רק סטטיסטיקות."
}

/** Past conversations shown at a time; "load more" adds as many again. */
private const val PAST_PAGE = 50

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
    // past conversations stay folded under their title, and open fifty at a time
    var pastOpen by rememberSaveable { mutableStateOf(false) }
    var pastShown by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(PAST_PAGE) }
    val names by vm.memberNames.collectAsStateWithLifecycle()
    fun who(m: ItemMessage) = if (m.authorId == vm.me) "אני" else names[m.authorId] ?: m.authorName
    var confirmDelete by remember { mutableStateOf<List<ItemMessage>?>(null) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = { BackNavButton() },
            title = { Text("צ'אט פוד", fontWeight = FontWeight.ExtraBold) },
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
                            "${who(last)}: ${last.preview}",
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
                    val turn by androidx.compose.animation.core.animateFloatAsState(if (pastOpen) 180f else 0f, label = "pastArrow")
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(onClickLabel = if (pastOpen) "סגירה" else "פתיחה") { pastOpen = !pastOpen; if (!pastOpen) pastShown = PAST_PAGE }
                            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("שיחות מקניות קודמות (${archivedThreads.size})", style = MaterialTheme.typography.titleSmall)
                            Text(retentionText(retention), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer { rotationZ = turn },
                        )
                    }
                    HorizontalDivider()
                }
            }
            if (archivedThreads.isNotEmpty() && pastOpen) {
                items(archivedThreads.take(pastShown), key = { "arch_${it.first().itemId}_${it.last().archivedAtMillis}" }) { thread ->
                    val last = thread.last()
                    Row(
                        Modifier.fillMaxWidth().clickable { openArchived = thread }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(last.itemName, style = MaterialTheme.typography.titleMedium)
                            Text("${who(last)}: ${last.preview}", maxLines = 1, style = MaterialTheme.typography.bodyMedium)
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
                val more = archivedThreads.size - pastShown
                if (more > 0) {
                    item(key = "archived_more") {
                        androidx.compose.material3.TextButton(
                            onClick = { pastShown += PAST_PAGE },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) { Text("טעינת עוד ${minOf(more, PAST_PAGE)} שיחות (נשארו $more)") }
                    }
                }
            }
        }
    }

    // a conversation of a past shopping opens in full (pictures, recordings, quotes), even when the item
    // was bought or removed from the list
    openArchived?.let { thread ->
        ArchivedChatScreen(vm, thread, onDelete = { confirmDelete = thread; openArchived = null }, onDismiss = { openArchived = null })
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


/** The item's price picture at the household's primary branch: from the list, else matched in the background. */
@Composable
private fun rememberItemPriceInfo(vm: AppViewModel, itemId: String, name: String, variant: String?): app.necessityq.domain.ItemPriceInfo? {
    val primary by vm.primaryBranch.collectAsStateWithLifecycle()
    val infos by vm.itemPriceInfo.collectAsStateWithLifecycle()
    val loaded by vm.branchPrices.collectAsStateWithLifecycle()
    val branchId = primary?.id
    // an item not on the list is matched in the background (searching a branch's products takes a moment)
    val fallback by produceState<app.necessityq.domain.ItemPriceInfo?>(null, loaded, infos, branchId, itemId) {
        value = if (branchId == null || infos[itemId] != null) null else vm.priceInfoFor(itemId, name, variant)
    }
    return infos[itemId] ?: fallback
}

/**
 * What the item costs at the household's primary branch, first on the product page: the basic price (per unit, or per
 * kilo / 100 g by weight), the brand chooser — one line that scrolls sideways, the brands most of the products carry
 * first — and the matching products with their prices in a block of their own that scrolls up and down, rather than a
 * long list down the page. Choosing an exact product is optional.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BranchPriceSection(vm: AppViewModel, itemId: String, info: app.necessityq.domain.ItemPriceInfo?) {
    val primary by vm.primaryBranch.collectAsStateWithLifecycle()
    val loaded by vm.branchPrices.collectAsStateWithLifecycle()
    val branch = primary ?: return
    val unit = LocalWeightPriceUnit.current
    var brand by remember(itemId) { mutableStateOf<String?>(null) }
    var pct by remember(itemId) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("מחיר ב${branch.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val basic = info?.let { app.necessityq.domain.UnitPrices.of(it, unit) }
        Text(
            when {
                !loaded.containsKey(branch.id) -> "המחירים של הסניף נטענים…"
                info == null -> "לא נמצאו מוצרים מתאימים בסניף"
                basic == null -> "במשקל · ${info.candidates.size} מוצרים"
                info.preferred != null -> "${app.necessityq.domain.PriceData.formatPrice(basic.low)} ${basic.per} · ${info.preferred!!.name}"
                else -> "${app.necessityq.domain.PriceData.formatRange(basic.low, basic.high)} ${basic.per} · ${info.candidates.size} מוצרים"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (info == null || info.candidates.isEmpty()) return

    val brands = remember(info) { app.necessityq.domain.ItemPrices.brands(info.candidates) }
    val percents = remember(info) { app.necessityq.domain.ItemPrices.percents(info.candidates) }
    if (percents.size > 1) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            percents.forEach { p -> FilterChip(selected = pct == p, onClick = { pct = if (pct == p) null else p }, label = { Text(p) }) }
        }
    }
    if (brands.size > 1) {
        // the companies: the common ones first, then the less known; one line that scrolls sideways
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            item { FilterChip(selected = brand == null, onClick = { brand = null }, label = { Text("כל החברות") }) }
            items(brands, key = { it }) { b ->
                FilterChip(selected = brand == b, onClick = { brand = if (brand == b) null else b }, label = { Text(b) })
            }
        }
    }
    val filtered = info.candidates.filter { c ->
        (brand == null || c.brand.trim() == brand) && (pct == null || app.necessityq.domain.ItemPrices.hasPercent(c, pct!!))
    }
    // the products scroll up and down in a block of their own, with no visible frame — only the edges fade out
    // where there is more to scroll to
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    Box(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalFadingEdges(listState)) {
        LazyColumn(Modifier.fillMaxWidth(), state = listState) {
            items(filtered, key = { it.code }) { c ->
                val chosen = info.preferred?.code == c.code
                BranchProductRow(c, chosen, onChoose = { vm.setPreferredProduct(itemId, if (chosen) null else c.code) })
            }
            if (filtered.isEmpty()) item {
                Text("אין מוצרים לבחירה הזו", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
            }
        }
    }
    Text(
        if (info.preferred != null) "המוצר שבחרתם מוצג ברשימה במקום הטווח." else "בחירת מוצר מדויק היא לא חובה — המוצר שתבחרו יוצג ברשימה במקום הטווח.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (info.preferred != null) {
        androidx.compose.material3.TextButton(onClick = { vm.setPreferredProduct(itemId, null) }) { Text("ביטול הבחירה — הצגת טווח בלבד") }
    }
}

/** The item page's own window: drawn edge to edge with no dimming, status-bar icons readable on the page. */
@Composable
internal fun ItemPageWindow() {
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
private fun BranchQuotesStrip(vm: AppViewModel, chosen: app.necessityq.domain.BranchProduct?) {
    val primary by vm.primaryBranch.collectAsStateWithLifecycle()
    val loaded by vm.branchPrices.collectAsStateWithLifecycle()
    val followed by vm.selectedPriceBranches.collectAsStateWithLifecycle()
    val all by vm.priceBranches.collectAsStateWithLifecycle()
    val branch = primary ?: return
    val unit = LocalWeightPriceUnit.current
    chosen ?: return
    // the other followed branches are downloaded only once there is a chosen product to compare
    LaunchedEffect(followed) { followed.forEach { vm.loadPrices(it) } }
    val quotes = remember(chosen, loaded, followed, all, branch.id) {
        // the branch the list shows first, then the others in the order they were followed
        val branches = (listOf(branch.id) + followed).distinct().mapNotNull { id -> all.firstOrNull { it.id == id } ?: branch.takeIf { it.id == id } }
        app.necessityq.domain.PriceData.quotes(chosen.code, chosen.internalCode, branch.chainKey, branches, loaded)
    }
    if (quotes.isEmpty()) return
    // prices are in the unit of the first branch; one that can't be matched shows its own and is not compared
    val compared = quotes.filter { it.comparable }
    val cheapest = compared.minOfOrNull { it.price }

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
                    // in the first branch's unit, which is the chosen product's: by weight per kilo / 100 g as chosen
                    Text(shownProductPrice(chosen, q.price, unit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    q.change?.let { c ->
                        val up = c > 0
                        val tint = if (up) NqPalette.PriceUp else NqPalette.PriceDown
                        Icon(
                            if (up) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = if (up) "עלה" else "ירד",
                            tint = tint,
                        )
                        val k = chosen.priceUnitKilos?.takeIf { chosen.soldByWeight }
                        Text(app.necessityq.domain.PriceData.formatPrice(kotlin.math.abs(if (k != null) unit.convert(c, k) else c)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tint)
                    }
                }
                if (!q.comparable) {
                    Text("ביחידה אחרת", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (compared.size > 1 && q.price == cheapest) {
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
            val unit = LocalWeightPriceUnit.current
            val range = if (c.high > c.low) " · היה ${shownProductPrice(c, c.low, unit)}–${shownProductPrice(c, c.high, unit)}" else ""
            Text(listOf(c.brand, range.removePrefix(" · ")).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        Text(shownProductPrice(c, c.price, LocalWeightPriceUnit.current), fontWeight = FontWeight.Bold)
    }
}

/**
 * Kind of quantity: none and the units that fit the item ([shown], see QuantitySteps.choicesFor) — an avocado by the
 * piece or by weight, never in litres.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun UnitChooser(
    current: app.necessityq.domain.QuantityUnit?,
    shown: List<app.necessityq.domain.QuantityUnit>,
    modifier: Modifier = Modifier,
    onChoose: (app.necessityq.domain.QuantityUnit?) -> Unit,
) {
    val label = { u: app.necessityq.domain.QuantityUnit -> if (u == app.necessityq.domain.QuantityUnit.PACKAGE) "מארז" else u.label }
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
    Row(Modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
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
            // a wave: each dot rises and falls a little after the previous one (as in WhatsApp)
            androidx.compose.foundation.layout.Box(
                Modifier
                    .graphicsLayer { translationY = -phase * 4.dp.toPx() }
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

/** Fades the top / bottom edge of a scrolling list out to nothing, each only while there is more to scroll that way. */
private fun Modifier.verticalFadingEdges(state: androidx.compose.foundation.lazy.LazyListState, length: androidx.compose.ui.unit.Dp = 28.dp): Modifier =
    this
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = length.toPx().coerceAtMost(size.height / 3)
            if (state.canScrollBackward) {
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = fade),
                    size = androidx.compose.ui.geometry.Size(size.width, fade),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                )
            }
            if (state.canScrollForward) {
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fade, endY = size.height),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fade),
                    size = androidx.compose.ui.geometry.Size(size.width, fade),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                )
            }
        }
