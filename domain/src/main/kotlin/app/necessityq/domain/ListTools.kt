package app.necessityq.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ---------- cart loading order ----------

/**
 * Recommended order for filling the cart: heavy and bulky first (they go at the bottom), then dry
 * goods and cans, fruit/vegetables and bread (fragile, on top), and the chilled and frozen products
 * last so they stay cold on the way home.
 */
object CartLoad {
    enum class Stage(val title: String) {
        HEAVY("1 · כבדים וגדולים — לתחתית העגלה"),
        DRY("2 · יבשים ושימורים"),
        FRESH("3 · פירות, ירקות ולחם — למעלה, שלא יימעכו"),
        CHILLED("4 · מקרר — מוצרי חלב, בשר ודגים"),
        FROZEN("5 · קפואים — אחרונים"),
    }

    /** Heavy or bulky generic items that go first even though their category is "dry". */
    private val heavyNames: Set<String> = setOf(
        "מים מינרליים", "סודה", "משקה קל", "מיץ", "מיץ ענבים",
        "אבקת כביסה", "מרכך כביסה", "נייר טואלט", "מגבות נייר", "חומר ניקוי", "סבון כלים", "שקיות אשפה",
        "חיתולים", "תמ\"ל",
        "שמן", "שמן זית", "קמח", "סוכר", "אורז", "מלח",
        "תפוחי אדמה", "בטטה", "בצל",
    ).map { HebrewText.matchKey(it) }.toSet()

    fun isHeavy(line: ListLine): Boolean = HebrewText.matchKey(line.name) in heavyNames

    fun stageOf(line: ListLine): Stage = when {
        line.category == Category.FROZEN -> Stage.FROZEN
        line.category.isChilled -> Stage.CHILLED
        isHeavy(line) || line.category in setOf(Category.DRINKS, Category.CLEANING, Category.BABY) -> Stage.HEAVY
        line.category == Category.PRODUCE || line.category == Category.BAKERY -> Stage.FRESH
        else -> Stage.DRY
    }

    /** Inside a stage: heavy items first, then by store-walk category, then the list's own order. */
    val withinStage: Comparator<ListLine> = compareBy({ !isHeavy(it) }, { it.category.order }, { it.orderKey })
}

// ---------- list from past purchases ----------

enum class GenerateMode(val label: String, val hint: String) {
    DUE("מה שעומד להיגמר", "לפי קצב הקנייה: מה שהגיע זמנו או ייגמר עד הקנייה הבאה"),
    ROUTINE("כל הקבועים", "כל מה שנקנה באופן קבוע, בכמות הרגילה"),
    LAST_TRIP("כמו הקנייה הקודמת", "כל מה שנקנה בפעם האחרונה"),
}

/** One proposed line; the user confirms (or unticks) before anything is added. */
data class GeneratedItem(
    val identity: ItemIdentity,
    val quantity: Quantity?,
    val reason: String,
    val preselected: Boolean = true,
)

/**
 * Builds a list from the household's history. Nothing is added by itself — the result is a proposal;
 * accepted lines are marked [LineSource.AUTO] so they stay distinguishable from what people added.
 */
object ListGenerator {
    fun propose(
        mode: GenerateMode,
        stats: Collection<ItemStats>,
        purchases: List<Purchase>,
        current: List<ListLine>,
        today: LocalDate,
        horizonDays: Long = 7,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<GeneratedItem> {
        val onList = current.filter { it.status == LineStatus.NEEDED }.map { it.itemId }.toSet()
        val proposed = when (mode) {
            GenerateMode.DUE -> stats.mapNotNull { s ->
                val due = s.nextDue ?: return@mapNotNull null
                val cadence = s.cadenceDays ?: return@mapNotNull null
                if (s.confidence <= 0.0 || due.isAfter(today.plusDays(horizonDays))) return@mapNotNull null
                val ratio = s.dueRatio(today) ?: 0.0
                GeneratedItem(
                    s.identity, s.typicalQuantity,
                    SuggestionEngine.runningLowReason(cadence, s.daysSinceLast(today)),
                    preselected = ratio >= 0.6 || s.classification == Classification.ROUTINE,
                )
            }
            GenerateMode.ROUTINE -> stats.filter { it.classification == Classification.ROUTINE }.map { s ->
                GeneratedItem(s.identity, s.typicalQuantity, "קבוע · נקנה ${s.purchaseDays} פעמים")
            }
            GenerateMode.LAST_TRIP -> {
                val day: (Purchase) -> LocalDate = { Instant.ofEpochMilli(it.boughtAtMillis).atZone(zone).toLocalDate() }
                val last = purchases.maxByOrNull { it.boughtAtMillis }?.let(day)
                if (last == null) emptyList()
                else purchases.filter { day(it) == last }
                    .sortedBy { it.boughtAtMillis }
                    .distinctBy { it.itemId }
                    .map { p -> GeneratedItem(p.identity, p.quantity, "נקנה בקנייה הקודמת") }
            }
        }
        return proposed
            .filter { it.identity.id !in onList }
            .distinctBy { it.identity.id }
            .sortedWith(compareBy({ it.identity.category.order }, { it.identity.displayName }))
    }
}

// ---------- reset ----------

enum class ResetScope(val label: String) {
    ALL("כל הרשימה"),
    AUTO_ONLY("רק מה שנוצר אוטומטית"),
    MANUAL_ONLY("רק מה שנוסף ידנית"),
}

object ListReset {
    /** The lines a reset moves to the recycle bin: open lines in scope, and with [ResetScope.ALL] also the bought ones. */
    fun linesFor(scope: ResetScope, lines: List<ListLine>): List<ListLine> = lines.filter { l ->
        when (scope) {
            ResetScope.ALL -> l.status == LineStatus.NEEDED || l.status == LineStatus.BOUGHT
            ResetScope.AUTO_ONLY -> l.status == LineStatus.NEEDED && l.isAuto
            ResetScope.MANUAL_ONLY -> l.status == LineStatus.NEEDED && !l.isAuto
        }
    }
}

// ---------- filter ----------

enum class SourceFilter(val label: String) { ALL("הכל"), MANUAL("ידני"), AUTO("אוטומטי") }

data class ListFilter(
    val text: String = "",
    val source: SourceFilter = SourceFilter.ALL,
    val categories: Set<Category> = emptySet(),
    val missingOnly: Boolean = false,
) {
    val isActive: Boolean get() = text.isNotBlank() || source != SourceFilter.ALL || categories.isNotEmpty() || missingOnly

    fun matches(line: ListLine): Boolean {
        if (text.isNotBlank() && !HebrewText.matchKey(line.displayName).contains(HebrewText.matchKey(text))) return false
        when (source) {
            SourceFilter.ALL -> Unit
            SourceFilter.MANUAL -> if (line.isAuto) return false
            SourceFilter.AUTO -> if (!line.isAuto) return false
        }
        if (categories.isNotEmpty() && line.category !in categories) return false
        if (missingOnly && !line.missing) return false
        return true
    }

    /** Applies to open lines only; bought and removed lines pass through untouched. */
    fun apply(lines: List<ListLine>): List<ListLine> =
        if (!isActive) lines else lines.filter { it.status != LineStatus.NEEDED || matches(it) }
}

// ---------- estimated total ----------

/** Estimated cost of the open lines from known unit prices. */
data class PriceEstimate(val total: Double, val pricedLines: Int, val openLines: Int) {
    val complete: Boolean get() = openLines > 0 && pricedLines == openLines

    companion object {
        /** How many price units a quantity is: pieces/packages as counted, kg/litre as is, grams/ml in thousands. No quantity = 1. */
        fun units(quantity: Quantity?): Double = when (quantity?.unit) {
            null -> 1.0
            QuantityUnit.PIECE, QuantityUnit.PACKAGE, QuantityUnit.KILOGRAM, QuantityUnit.LITER -> quantity.amount
            QuantityUnit.GRAM, QuantityUnit.MILLILITER -> quantity.amount / 1000.0
        }

        fun of(lines: List<ListLine>, prices: Map<String, Double>): PriceEstimate {
            val open = lines.filter { it.status == LineStatus.NEEDED }
            val priced = open.filter { prices[it.itemId] != null }
            val total = priced.sumOf { prices.getValue(it.itemId) * units(it.quantity) }
            return PriceEstimate(total, priced.size, open.size)
        }
    }
}
