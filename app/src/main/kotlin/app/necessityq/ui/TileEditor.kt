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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * Popup to choose what the product tiles of one stage of use show. A sample tile carries every part;
 * tapping a part fades it (hidden) or brings it back to full colour (shown). Nothing changes until "שמירה",
 * which updates every tile of [mode].
 */
@Composable
fun TileEditorDialog(
    mode: ListMode,
    layout: TileLayout,
    onSave: (TileLayout) -> Unit,
    onDismiss: () -> Unit,
    onMoreSettings: (() -> Unit)? = null,
) {
    var draft by remember(mode, layout) { mutableStateOf(layout) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("תצוגת האריח · ${mode.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "נגיעה ברכיב מסתירה אותו (דהוי) או מחזירה אותו (צבע מלא).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SampleTile(draft, shopping = mode == ListMode.SHOP) { draft = draft.toggle(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "מוצגים ${draft.shown.size} מתוך ${TileElement.entries.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { draft = TileLayout.defaultFor(mode) }) { Text("ברירת מחדל") }
                }
                onMoreSettings?.let { TextButton(onClick = it) { Text("הגדרות תצוגה נוספות") } }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("שמירה") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** One part of the sample tile: full colour when shown, faded when hidden; a tap toggles it. */
@Composable
private fun Part(element: TileElement, layout: TileLayout, onToggle: (TileElement) -> Unit, content: @Composable () -> Unit) {
    val shown = element in layout
    Box(Modifier.alpha(if (shown) 1f else HIDDEN_ALPHA)) {
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

/** A made-up product with every part a tile can have, laid out like the real tile on the list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SampleTile(layout: TileLayout, shopping: Boolean, onToggle: (TileElement) -> Unit) {
    val quantity = Quantity(6.0, QuantityUnit.PIECE)
    val weight = PieceWeights.estimate("עגבניות", quantity)?.let { PieceWeights.label(it) } ?: "≈0.9 ק\"ג"
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        shape = NqShapes.Tile,
        border = BorderStroke(1.dp, NqTheme.colors.hairline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(start = 6.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Part(TileElement.DRAG, layout, onToggle) {
                    Icon(Icons.Default.DragIndicator, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(2.dp))
                }
                Part(TileElement.CHECK, layout, onToggle) {
                    val size = if (shopping) 48.dp else 40.dp
                    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(size - 18.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    }
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("עגבניות", style = ProductNameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Part(TileElement.QUANTITY, layout, onToggle) {
                            Text(
                                quantity.format(),
                                fontWeight = FontWeight.SemiBold,
                                color = muted,
                                modifier = Modifier.padding(top = 2.dp).clip(NqShapes.Pill)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 1.dp),
                            )
                        }
                    }
                    FlowRow(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Part(TileElement.ALERTS, layout, onToggle) { TagBadge(ItemTag(TagKind.LARGE_AMOUNT, "כמות חריגה")) }
                        Part(TileElement.SOURCE, layout, onToggle) { TagBadge(ItemTag(TagKind.RECIPE, "מתכון")) }
                        Part(TileElement.KIND, layout, onToggle) { TagBadge(ItemTag(TagKind.ROUTINE, "קבוע")) }
                        Part(TileElement.FAVORITE, layout, onToggle) { TagBadge(ItemTag(TagKind.FAVORITE, "מועדף")) }
                        Part(TileElement.CHAT, layout, onToggle) { ChatIndicator(ChatState(unread = 2, lastIsMine = false, status = null)) }
                    }
                }
                Spacer(Modifier.width(4.dp))
                Part(TileElement.PRICE, layout, onToggle) { PriceSlot(price = 8.9, average = 9.5) }
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    TileElement.NOTE to "שרי אם יש",
                    TileElement.LAST_BOUGHT to "נקנה לפני 5 ימים",
                    TileElement.FREQUENCY to "בערך כל 7 ימים",
                    TileElement.ADDED_BY to "הוסיף/ה: אבא",
                ).forEach { (element, text) ->
                    Part(element, layout, onToggle) {
                        Text(text, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Part(TileElement.WEIGHT, layout, onToggle) {
                    Text(weight, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(4.dp))
                }
                Part(TileElement.STEPPER, layout, onToggle) { QuantityStepper(quantity, onMinus = {}, onPlus = {}) }
            }
        }
    }
}
