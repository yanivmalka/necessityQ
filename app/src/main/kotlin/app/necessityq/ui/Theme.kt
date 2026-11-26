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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.necessityq.R
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
    val SwipeBought = Color(0xFF0B6E4F)

    /** Reveal behind a tile swiped right while shopping: missing. */
    val SwipeMissing = Color(0xFFB45309)

    /** Reveal behind a tile swiped right while preparing: delete. */
    val SwipeDelete = Color(0xFFD92D20)

    /** Reveal behind a tile swiped to open its צ'אט פוד. */
    val SwipeChat = Color(0xFF1D5FA8)

    /** Icon, ring and label on the swipe reveals, and the unread count on the chat pill. */
    val OnAction = Color.White

    /** Star beside a favourite item's name. */
    val FavoriteStar = Color(0xFFC77700)

    /** A price that went up (▲), as on a stock ticker. */
    val PriceUp = Color(0xFFD92D20)

    /** A price that went down (▼). */
    val PriceDown = Color(0xFF16A34A)
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
    TagKind.LARGE_AMOUNT -> Color(0xFFFFE8CC) to Color(0xFF9C3D00)
}

/**
 * Colours that depend on whether the screen is dark. On a dark screen the tag and promotion pairs
 * swap roles (dark fill, light text), which keeps exactly the same two colours and the same contrast.
 */
@Immutable
class NqColors(private val dark: Boolean, private val palette: AppPalette = AppPalette.MARKET) {
    /** Background and text of a tag pill. */
    fun tag(kind: TagKind): Pair<Color, Color> = tagPair(kind).let { (tint, ink) -> if (dark) ink to tint else tint to ink }

    /** Promotion strip under a tile. */
    val promoBackground: Color = if (dark) Color(0xFF5C4400) else Color(0xFFFFF1B8)
    val promoInk: Color = if (dark) Color(0xFFFFF1B8) else Color(0xFF5C4400)

    /** Double tick of a message that was read; lighter on dark so it shows on the dark bubbles. */
    val readBlue: Color = if (dark) Color(0xFF4DABF7) else Color(0xFF1570EF)

    /** צ'אט פוד: my bubbles (soft green, as in WhatsApp) and everyone else's (plain card). */
    val myBubble: Color = if (dark) Color(0xFF1E4A38) else Color(0xFFDCF5E3)
    val theirBubble: Color = if (dark) Color(0xFF262B28) else Color.White

    /** Behind a "@name" tag inside a bubble or the message box. */
    val mentionBackground: Color = if (dark) Color(0xFF2F4A6B) else Color(0xFFDCE9FF)

    /** Day line between messages ("היום", "אתמול"). */
    val dayChip: Color = if (dark) Color(0xFF2E3430) else Color(0xFFEDEAE2)

    /**
     * Name colour per writer in a conversation, so it is clear at a glance who wrote what. Saturated on
     * light, lighter on dark, each readable on its bubble.
     */
    val senderColors: List<Color> = if (dark) {
        listOf(Color(0xFF74C0FC), Color(0xFFFFA94D), Color(0xFFB197FC), Color(0xFF63E6BE), Color(0xFFFF8787), Color(0xFFFFD43B))
    } else {
        listOf(Color(0xFF1C64C9), Color(0xFFC2410C), Color(0xFF7048E8), Color(0xFF0F7B6C), Color(0xFFC92A2A), Color(0xFF946200))
    }

    /** Hairline border around cards and fields. */
    val hairline: Color = if (dark) Color(0xFF2E3430) else Color(0xFFECE9E1)

    /** Background of the segmented mode switch. */
    val segmentTrack: Color = if (dark) Color(0xFF262B28) else Color(0xFFE9E6DD)

    /** Header of the shopping screen and the text on it. */
    val shopHeader: Color = if (dark) palette.darkContainer else palette.primary
    val onShopHeader: Color = Color.White
    val onShopHeaderMuted: Color = if (dark) Color(0xFFB7DCC8) else Color(0xFFCFE7DB)
    val shopProgress: Color = Color(0xFFFFD8A8)
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

/** Rubik: a rounded sans with full Hebrew support (SIL OFL, see third_party/rubik). */
val Rubik = FontFamily(
    Font(R.font.rubik_400, FontWeight.Normal),
    Font(R.font.rubik_500, FontWeight.Medium),
    Font(R.font.rubik_600, FontWeight.SemiBold),
    Font(R.font.rubik_700, FontWeight.Bold),
)

/** Product names on tiles: clear and a little larger than body text. */
val ProductNameStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)

/*
 * "Market" palette: a warm off-white ground, white cards with a hairline border, one deep green for
 * every main action and amber only for "running out", deals and missing items.
 */
private val MarketLight = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F1EA),
    onPrimaryContainer = Color(0xFF08513A),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDF0E1),
    onSecondaryContainer = Color(0xFF8A4108),
    tertiary = Color(0xFF3B5BDB),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEEF2FF),
    onTertiaryContainer = Color(0xFF3B4AA8),
    background = Color(0xFFF6F4EE),
    onBackground = Color(0xFF1B1F1C),
    surface = Color.White,
    onSurface = Color(0xFF1B1F1C),
    surfaceVariant = Color(0xFFF0EEE8),
    onSurfaceVariant = Color(0xFF545B56),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAF7),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color(0xFF7A817C),
    outlineVariant = Color(0xFFE4E1D8),
    error = Color(0xFFC92A2A),
)

private val MarketDark = darkColorScheme(
    primary = Color(0xFF7FD1A8),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0F5139),
    onPrimaryContainer = Color(0xFFC7EBD8),
    secondary = Color(0xFFF5B26B),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF5A3209),
    onSecondaryContainer = Color(0xFFFDE3C6),
    tertiary = Color(0xFFA5B4FC),
    onTertiary = Color(0xFF1E2A78),
    tertiaryContainer = Color(0xFF2B3480),
    onTertiaryContainer = Color(0xFFE0E6FF),
    background = Color(0xFF121513),
    onBackground = Color(0xFFE6E9E7),
    surface = Color(0xFF1B1F1C),
    onSurface = Color(0xFFE6E9E7),
    surfaceVariant = Color(0xFF262B28),
    onSurfaceVariant = Color(0xFFB9C0BB),
    surfaceContainerLowest = Color(0xFF0E110F),
    surfaceContainerLow = Color(0xFF171A18),
    surfaceContainer = Color(0xFF1B1F1C),
    surfaceContainerHigh = Color(0xFF222724),
    surfaceContainerHighest = Color(0xFF1B1F1C),
    outline = Color(0xFF8A928C),
    outlineVariant = Color(0xFF343A36),
    error = Color(0xFFFF8A80),
)

private fun Typography.withFont(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/**
 * The colour palettes the household can choose ("מראה" ← "ערכת צבעים"). Each has its own main colour (actions, the
 * shopping header, chosen chips) and a matching ground, in light and in dark. Amber stays for "running out", deals and
 * missing items, and the red/green of prices stay, in every palette, so their meaning does not change.
 */
enum class AppPalette(
    val label: String,
    val primary: Color,
    val container: Color,
    val onContainer: Color,
    val background: Color,
    val surfaceVariant: Color,
    val outlineVariant: Color,
    val darkPrimary: Color,
    val onDarkPrimary: Color,
    val darkContainer: Color,
    val onDarkContainer: Color,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceVariant: Color,
) {
    MARKET(
        "שוק (ירוק)",
        Color(0xFF0B6E4F), Color(0xFFE3F1EA), Color(0xFF08513A), Color(0xFFF6F4EE), Color(0xFFF0EEE8), Color(0xFFE4E1D8),
        Color(0xFF7FD1A8), Color(0xFF00391F), Color(0xFF0F5139), Color(0xFFC7EBD8), Color(0xFF121513), Color(0xFF1B1F1C), Color(0xFF262B28),
    ),
    SEA(
        "ים (כחול)",
        Color(0xFF1E5AA8), Color(0xFFE1ECFA), Color(0xFF113E78), Color(0xFFF2F5F9), Color(0xFFE9EEF4), Color(0xFFDCE3EC),
        Color(0xFF9CC3F5), Color(0xFF0B2A52), Color(0xFF163D6B), Color(0xFFD6E6FB), Color(0xFF111418), Color(0xFF1A1E23), Color(0xFF252B32),
    ),
    GRAPE(
        "ענבים (סגול)",
        Color(0xFF5B3A9E), Color(0xFFECE5F8), Color(0xFF41287A), Color(0xFFF6F3F9), Color(0xFFEEE9F3), Color(0xFFE2DBEB),
        Color(0xFFC6B1F2), Color(0xFF2A1659), Color(0xFF3E2A6E), Color(0xFFE9DFFC), Color(0xFF141217), Color(0xFF1D1A21), Color(0xFF28242D),
    ),
    CITRUS(
        "הדר (כתום)",
        Color(0xFFC2410C), Color(0xFFFDE9DC), Color(0xFF8A2E08), Color(0xFFFAF5EF), Color(0xFFF3ECE3), Color(0xFFE9DFD2),
        Color(0xFFFFB38A), Color(0xFF4F1A02), Color(0xFF6B2A0A), Color(0xFFFFE1CF), Color(0xFF161311), Color(0xFF201C19), Color(0xFF2C2622),
    ),
    OLIVE(
        "זית",
        Color(0xFF5F6B2E), Color(0xFFEEF0DD), Color(0xFF454E1F), Color(0xFFF6F5EC), Color(0xFFEEECDF), Color(0xFFE2E0CF),
        Color(0xFFC8D48E), Color(0xFF2B3210), Color(0xFF3E4720), Color(0xFFE9EFC9), Color(0xFF131410), Color(0xFF1C1E18), Color(0xFF272A22),
    ),
    GRAPHITE(
        "גרפיט (אפור)",
        Color(0xFF37474F), Color(0xFFE6EBEE), Color(0xFF263238), Color(0xFFF4F5F6), Color(0xFFEBEDEF), Color(0xFFDEE2E5),
        Color(0xFFB0C4CE), Color(0xFF1A262C), Color(0xFF2F3D44), Color(0xFFDDE7EC), Color(0xFF121314), Color(0xFF1B1D1F), Color(0xFF26292B),
    ),
    ;

    /** The Market scheme with this palette's main colour and ground. */
    fun scheme(dark: Boolean) = if (dark) {
        MarketDark.copy(
            primary = darkPrimary,
            onPrimary = onDarkPrimary,
            primaryContainer = darkContainer,
            onPrimaryContainer = onDarkContainer,
            background = darkBackground,
            surface = darkSurface,
            surfaceVariant = darkSurfaceVariant,
            surfaceContainer = darkSurface,
            surfaceContainerHighest = darkSurface,
        )
    } else {
        MarketLight.copy(
            primary = primary,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            background = background,
            surfaceVariant = surfaceVariant,
            outlineVariant = outlineVariant,
        )
    }

    companion object {
        fun of(name: String?): AppPalette = entries.firstOrNull { it.name == name } ?: MARKET
    }
}

/**
 * Two looks, both in the chosen palette ([AppPalette]) and the Rubik typeface:
 *  - system (default): regular text sizes, light or dark following the phone setting.
 *  - bright ("תצוגה בהירה במיוחד", chosen in the settings): for supermarkets and sunlight. Always light, with larger text.
 */
@Composable
fun NecessityTheme(bright: Boolean, palette: AppPalette = AppPalette.MARKET, content: @Composable () -> Unit) {
    val dark = !bright && isSystemInDarkTheme()
    val colors = palette.scheme(dark)
    // the clock, battery and signal follow the app's own look, not the phone's: dark icons on a light app
    // even when the phone is in dark mode (edge-to-edge picked them by the phone's setting alone)
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val base = Typography()
    val sized = base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = if (bright) 26.sp else 24.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = if (bright) 18.sp else 16.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = if (bright) 15.sp else 14.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = if (bright) 17.sp else 16.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = if (bright) 15.sp else 14.sp),
        bodySmall = base.bodySmall.copy(fontSize = if (bright) 13.sp else 12.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
    // The app is Hebrew-first: force RTL regardless of the device language.
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalNqColors provides NqColors(dark, palette),
    ) {
        MaterialTheme(colorScheme = colors, typography = sized.withFont(Rubik), content = content)
    }
}
