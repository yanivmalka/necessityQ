package app.necessityq.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.necessityq.ui.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.necessityq.domain.ItemTag
import app.necessityq.domain.ListMode
import app.necessityq.domain.PieceWeights
import app.necessityq.domain.Quantity
import app.necessityq.domain.QuantityUnit
import app.necessityq.domain.TagKind
import app.necessityq.domain.TileElement
import app.necessityq.domain.TileLayout

/** How a hidden part looks on the sample tile: pale and faded, still there to tap back on. */
private const val HIDDEN_ALPHA = 0.22f

/**
 * Sheet from the bottom to choose what the product tiles of one stage of use show. The sample tile is laid
 * out exactly like a tile on the list (same width, same rows, same truncation), so it is as tall as a real
 * one; tapping a part fades it (hidden) or brings it back to full colour (shown). Parts that a real tile
 * would cut off are listed under the sample so they can still be toggled. Nothing changes until "שמירה",
 * which updates every tile of [mode].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TileEditorSheet(
    mode: ListMode,
    layout: TileLayout,
    onSave: (TileLayout) -> Unit,
    onDismiss: () -> Unit,
    onMoreSettings: (() -> Unit)? = null,
) {
    var draft by remember(mode, layout) { mutableStateOf(layout) }
    var cutOff by remember { mutableStateOf(emptyList<TileElement>()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("מה מוצג על כל פריט · ${mode.label}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    // a real (outlined) button, so it does not read as a caption beside the title; greyed out
                    // while the draft already is the default
                    OutlinedButton(
                        onClick = { draft = TileLayout.defaultFor(mode) },
                        enabled = draft != TileLayout.defaultFor(mode),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ברירת מחדל")
                    }
                }
                Text(
                    "נגיעה ברכיב מסתירה אותו (דהוי) או מחזירה אותו (צבע מלא).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // same side margins as the list, so the sample is exactly as wide as a real tile
            Box(Modifier.padding(horizontal = 12.dp)) {
                SampleTile(
                    layout = draft,
                    shopping = mode == ListMode.SHOP,
                    onToggle = { draft = draft.toggle(it) },
                    onCutOff = { if (it != cutOff) cutOff = it },
                )
            }
            if (cutOff.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "לא נכנסו באריח (כמו ברשימה):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        cutOff.forEach { element ->
                            FilterChip(
                                selected = element in draft,
                                onClick = { draft = draft.toggle(element) },
                                label = { Text(element.label) },
                            )
                        }
                    }
                }
            }
            Text(
                "מוצגים ${draft.shown.count { it.availableIn(mode) }} מתוך ${TileElement.of(mode).size} רכיבים",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                onMoreSettings?.let { TextButton(onClick = it) { Text("הגדרות נוספות") } }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text("ביטול") }
                Button(onClick = { onSave(draft) }) { Text("שמירה") }
            }
        }
    }
}

/** One part of the sample tile: full colour when shown, faded when hidden; a tap toggles it. */
@Composable
private fun Part(
    element: TileElement,
    layout: TileLayout,
    onToggle: (TileElement) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shown = element in layout
    Box(modifier.alpha(if (shown) 1f else HIDDEN_ALPHA)) {
        content()
        // on top of the part, so its own buttons (the stepper) don't take the tap
        Box(
            Modifier.matchParentSize().clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Checkbox, onClickLabel = if (shown) "הסתרה" else "הצגה") { onToggle(element) }
                .semantics {
                    contentDescription = element.label
                    stateDescription = if (shown) "מוצג" else "מוסתר"
                },
        )
    }
}

/** The texts under the product name, joined with " · " like the real tile's line. */
private val SUBLINE = listOf(
    TileElement.NOTE to "שרי אם יש",
    TileElement.LAST_BOUGHT to "נקנה לפני 5 ימים",
    TileElement.FREQUENCY to "בערך כל 7 ימים",
    TileElement.ADDED_BY to "הוסיף/ה: אבא",
)

/**
 * A made-up product with every part a tile can have, built with the same structure, paddings and limits as
 * ProductTile on the list: the name and the line under it are cut with "…" where a real tile would cut them.
 * [onCutOff] gets the parts of that line that did not fit at all.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SampleTile(
    layout: TileLayout,
    shopping: Boolean,
    onToggle: (TileElement) -> Unit,
    onCutOff: (List<TileElement>) -> Unit,
) {
    val quantity = Quantity(6.0, QuantityUnit.PIECE)
    val weight = PieceWeights.estimate("עגבניות", quantity)?.let { PieceWeights.label(it) } ?: "≈0.9 ק\"ג"
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = NqShapes.Tile,
        border = BorderStroke(1.dp, NqTheme.colors.hairline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Part(TileElement.DRAG, layout, onToggle) {
                    Icon(Icons.Default.DragIndicator, null, tint = MaterialTheme.colorScheme.outline)
                }
                // an item is marked bought only in the shop: no check circle while making the list
                if (shopping) {
                    Part(TileElement.CHECK, layout, onToggle) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(30.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "עגבניות",
                            style = ProductNameStyle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        Part(TileElement.QUANTITY, layout, onToggle) {
                            Text(
                                quantity.format(),
                                fontWeight = FontWeight.SemiBold,
                                color = muted,
                                modifier = Modifier.clip(NqShapes.Pill)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 1.dp),
                            )
                        }
                    }
                    FlowRow(
                        Modifier.padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Part(TileElement.ALERTS, layout, onToggle) { TagBadge(ItemTag(TagKind.LARGE_AMOUNT, "כמות חריגה")) }
                        Part(TileElement.SOURCE, layout, onToggle) { TagBadge(ItemTag(TagKind.RECIPE, "מתכון")) }
                        Part(TileElement.KIND, layout, onToggle) { TagBadge(ItemTag(TagKind.ROUTINE, "קבוע")) }
                        Part(TileElement.FAVORITE, layout, onToggle) { TagBadge(ItemTag(TagKind.FAVORITE, "מועדף")) }
                        Part(TileElement.CHAT, layout, onToggle) { ChatIndicator(ChatState(unread = 2, lastIsMine = false, status = null)) }
                    }
                }
                Part(TileElement.PRICE, layout, onToggle) { PriceSlot(price = 8.9, average = 9.5) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Subline(layout, onToggle, onCutOff, Modifier.weight(1f).padding(start = 8.dp))
                Part(TileElement.WEIGHT, layout, onToggle) {
                    Text(weight, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(end = 6.dp))
                }
                Part(TileElement.STEPPER, layout, onToggle) { QuantityStepper(quantity, onMinus = {}, onPlus = {}) }
            }
        }
    }
}

/**
 * The line under the name as one text, two lines at most and cut with "…", like on the list. Each part of
 * it is tappable on its own; the parts that fell entirely past the cut are reported to [onCutOff].
 */
@Composable
private fun Subline(
    layout: TileLayout,
    onToggle: (TileElement) -> Unit,
    onCutOff: (List<TileElement>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val starts = mutableMapOf<TileElement, Int>()
    val text = buildAnnotatedString {
        SUBLINE.forEachIndexed { i, (element, label) ->
            if (i > 0) withStyle(SpanStyle(color = muted.copy(alpha = 0.6f))) { append(" · ") }
            starts[element] = length
            withLink(LinkAnnotation.Clickable(element.name, linkInteractionListener = { onToggle(element) })) {
                withStyle(SpanStyle(color = if (element in layout) muted else muted.copy(alpha = HIDDEN_ALPHA))) { append(label) }
            }
        }
    }
    Text(
        text,
        style = style,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        onTextLayout = { result ->
            // where the two lines end without the "…": a part starting after that is not on the tile at all
            val clipped = measurer.measure(
                text,
                style = style,
                maxLines = 2,
                overflow = TextOverflow.Clip,
                constraints = result.layoutInput.constraints,
            )
            val end = if (clipped.hasVisualOverflow) clipped.getLineEnd(clipped.lineCount - 1) - 1 else Int.MAX_VALUE
            onCutOff(SUBLINE.map { it.first }.filter { (starts[it] ?: 0) >= end })
        },
    )
}
