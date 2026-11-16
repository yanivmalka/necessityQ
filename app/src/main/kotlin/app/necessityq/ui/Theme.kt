package app.necessityq.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.necessityq.domain.Category
import app.necessityq.domain.TagKind

/*
 * The app's design tokens in one place. Names follow the NecessityQ design system:
 * swipe-bought -> NqPalette.SwipeBought, tag-routine-bg/fg -> NqTheme.colors.tag(ROUTINE),
 * radius-20 -> NqShapes.Tile, and so on.
 */

/** Colours that are the same in every theme. */
object NqPalette {
    /** Reveal behind a tile swiped left: bought. */
    val SwipeBought = Color(0xFF00875A)

    /** Reveal behind a tile swiped right while shopping: missing. */
    val SwipeMissing = Color(0xFFE8590C)

    /** Reveal behind a tile swiped right while preparing: delete. */
    val SwipeDelete = Color(0xFFD92D20)

    /** Icon, ring and label on the swipe reveals, and the unread count on the chat pill. */
    val OnAction = Color.White

    /** Star beside a favourite item's name. */
    val FavoriteStar = Color(0xFFC77700)
}

/** Accent colour per category: a thin strip on each tile and a dot on the group header. */
fun categoryColor(category: Category): Color = when (category) {
    Category.PRODUCE -> Color(0xFF2F9E44)
    Category.DAIRY -> Color(0xFF1C7ED6)
    Category.BAKERY -> Color(0xFFE8890C)
    Category.MEAT_FISH -> Color(0xFFE03131)
    Category.PANTRY -> Color(0xFF9C6B30)
    Category.BREAKFAST_SNACKS -> Color(0xFFD6336C)
    Category.DRINKS -> Color(0xFF1098AD)
    Category.FROZEN -> Color(0xFF4DABF7)
    Category.CLEANING -> Color(0xFF7048E8)
    Category.TOILETRIES -> Color(0xFFAE3EC9)
    Category.BABY -> Color(0xFFF783AC)
    Category.OTHER -> Color(0xFF868E96)
}

/** Light tint and dark ink of each tag kind. */
private fun tagPair(kind: TagKind): Pair<Color, Color> = when (kind) {
    TagKind.ROUTINE -> Color(0xFFDDF4E8) to Color(0xFF05603A)
    TagKind.OCCASIONAL -> Color(0xFFE0EAFF) to Color(0xFF2D3FA8)
    TagKind.SPECIAL -> Color(0xFFF1E4FF) to Color(0xFF6B21A8)
    TagKind.FAVORITE -> Color(0xFFFFF0C2) to Color(0xFF7A4D00)
    TagKind.MISSING -> Color(0xFFFFE2D6) to Color(0xFF9A3412)
    TagKind.RECIPE -> Color(0xFFD5F5F0) to Color(0xFF0F5F55)
    TagKind.AUTO -> Color(0xFFE9ECEF) to Color(0xFF343A40)
}

/**
 * Colours that depend on whether the screen is dark. On a dark screen the tag and promotion pairs
 * swap roles (dark fill, light text), which keeps exactly the same two colours and the same contrast.
 */
@Immutable
class NqColors(private val dark: Boolean) {
    /** Background and text of a tag pill. */
    fun tag(kind: TagKind): Pair<Color, Color> = tagPair(kind).let { (tint, ink) -> if (dark) ink to tint else tint to ink }

    /** Promotion strip under a tile. */
    val promoBackground: Color = if (dark) Color(0xFF5C4400) else Color(0xFFFFF1B8)
    val promoInk: Color = if (dark) Color(0xFFFFF1B8) else Color(0xFF5C4400)

    /** Double tick of a message that was read; lighter on dark so it shows on the dark bubbles. */
    val readBlue: Color = if (dark) Color(0xFF4DABF7) else Color(0xFF1570EF)
}

private val LocalNqColors = staticCompositionLocalOf { NqColors(dark = false) }

object NqTheme {
    val colors: NqColors
        @Composable @ReadOnlyComposable get() = LocalNqColors.current
}

/** Corner shapes, named by what they are for. */
object NqShapes {
    /** Product tiles, suggestion and menu cards, the swipe reveal. */
    val Tile = RoundedCornerShape(20.dp)

    /** Quick-add and search fields, the recording bar. */
    val Field = RoundedCornerShape(16.dp)

    /** Chat bubbles, price slot, info panels. */
    val Panel = RoundedCornerShape(12.dp)

    /** Reply and edit strips above the composer. */
    val Strip = RoundedCornerShape(10.dp)

    /** Quoted reply and images inside a bubble. */
    val Inset = RoundedCornerShape(8.dp)

    /** Tags, quantity, stepper and chat pills. */
    val Pill = RoundedCornerShape(50)
}

/** Product names on tiles: bold and large in every theme. */
val ProductNameStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)

/**
 * Two looks:
 *  - bright (default): for supermarkets and sunlight — pure white, near-black text, saturated
 *    dark accents, bold large product names, strong outlines; never switches to dark.
 *  - system: the regular light/dark palette following the phone setting.
 */
@Composable
fun NecessityTheme(bright: Boolean, content: @Composable () -> Unit) {
    val dark = !bright && isSystemInDarkTheme()
    val colors = when {
        // modern, high-contrast: white tiles floating on a cool light-grey canvas, ink-dark text
        bright -> lightColorScheme(
            primary = Color(0xFF00875A),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD3F4E4),
            onPrimaryContainer = Color(0xFF00301F),
            secondary = Color(0xFFE8590C),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFE8D9),
            onSecondaryContainer = Color(0xFF3D1300),
            tertiary = Color(0xFF3B5BDB),
            onTertiary = Color.White,
            background = Color(0xFFF2F4F7),
            onBackground = Color(0xFF101828),
            surface = Color.White,
            onSurface = Color(0xFF101828),
            surfaceVariant = Color(0xFFEEF1F5),
            onSurfaceVariant = Color(0xFF344054),
            surfaceContainer = Color.White,
            surfaceContainerLow = Color(0xFFF9FAFB),
            surfaceContainerHigh = Color.White,
            outline = Color(0xFF667085),
            outlineVariant = Color(0xFF98A2B3),
            error = Color(0xFFD92D20),
        )
        dark -> darkColorScheme(primary = Color(0xFF81C784), secondary = Color(0xFFFFB74D))
        else -> lightColorScheme(primary = Color(0xFF2E7D32), secondary = Color(0xFFEF6C00), background = Color(0xFFF7FAF5))
    }
    val base = Typography()
    val typography = if (bright) {
        base.copy(
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
            bodyLarge = base.bodyLarge.copy(fontSize = 18.sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            bodySmall = base.bodySmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        )
    } else base
    // The app is Hebrew-first: force RTL regardless of the device language.
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalNqColors provides NqColors(dark),
    ) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}
