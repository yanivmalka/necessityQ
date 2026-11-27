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
    DUE(
        "מה שעומד להיגמר",
        "מה שהגיע זמנו או ייגמר עד הקנייה הבאה. מה שנקנה 3 פעמים בהפרשים דומים מסומן מראש; מה שנקנה רק פעמיים מוצע עם שאלה.",
    ),
    ROUTINE("כל מה שיש לו קצב", "כל מה שנקנה לפחות 3 פעמים בהפרשים דומים (כל שבוע, כל חודש, כל חצי שנה), בכמות הרגילה"),
    LAST_TRIP("כמו הקנייה הקודמת", "כל מה שנקנה בפעם האחרונה"),
}

/** One proposed line; the user confirms (or unticks) before anything is added. */
data class GeneratedItem(
    val identity: ItemIdentity,
    val quantity: Quantity?,
    val reason: String,
    val preselected: Boolean = true,
    /** Where [quantity] came from when it is not just the usual purchase (see [TripSizer]). */
    val quantityNote: String? = null,
)

/**
 * Builds a list from the household's history. Nothing is added by itself — the result is a proposal;
 * accepted lines are marked [LineSource.AUTO] so they stay distinguishable from what people added.
 *
 * [GenerateMode.DUE] is the same list as the "בניית רשימה עד הקנייה הבאה" card ([SuggestionEngine.forNextTrip]), preselected;
 * items whose rhythm is still a guess are shown below it, not preselected. A household with no history yet gets a
 * first list from the survey's answers or its size ([TripSizer.starter]). Amounts come from [TripSizer], as everywhere else.
 */
object ListGenerator {
    fun propose(
        mode: GenerateMode,
        stats: Collection<ItemStats>,
        purchases: List<Purchase>,
        current: List<ListLine>,
        today: LocalDate,
        /** How often the household shops ([TripCadence.days]): the list is for until the trip after this one. */
        horizonDays: Long = TripCadence.DEFAULT.days.toLong(),
        zone: ZoneId = ZoneId.systemDefault(),
        /** Items the household recently turned down: still shown (they asked for the whole list), but not preselected. */
        suppressed: Set<String> = emptySet(),
        /** Of [suppressed], the ones hidden because the household has them at home ("יש לי בבית"). */
        atHome: Set<String> = emptySet(),
        /** Per item, how often its suggestion was turned down lately (see [SuggestionEngine.rejectionWeight]). */
        rejections: Map<String, Int> = emptyMap(),
        /** The size of the household ([HouseholdSize.people]); 0 when unknown. */
        people: Double = 0.0,
        /** The first-list survey's answers ([FirstListSurvey]), when it was filled in. */
        baseline: HouseholdBaseline? = null,
        /** The packs the household buys ([HouseholdPacks]): computed amounts are whole packs of them, as on the list. */
        chosen: PackChoices = PackChoices(),
    ): List<GeneratedItem> {
        val onList = current.filter { it.status == LineStatus.NEEDED }.map { it.itemId }.toSet()
        val sizer = TripSizer(stats, people, baseline, chosen, horizonDays.toInt())
        val horizon = horizonDays.toInt()
        val proposed = when (mode) {
            GenerateMode.DUE -> {
                // what runs out before the trip after this one: by its steady rhythm, preselected; by two purchases
                // (or uneven gaps), offered with a question
                val week = SuggestionEngine().forNextTrip(stats, onList, emptyList(), today, horizon, rejections = rejections)
                val byId = stats.associateBy { it.itemId }
                val weekIds = week.map { it.itemId }.toSet()
                val due = week.map { w ->
                    val s = byId[w.itemId]
                    if (s == null || s.hasRhythm) GeneratedItem(w.identity, w.quantity, w.reason)
                    else GeneratedItem(w.identity, w.quantity, askReason(s), preselected = false)
                }
                val unsure = stats.mapNotNull { s ->
                    if (s.itemId in weekIds) return@mapNotNull null
                    val next = s.nextDue ?: return@mapNotNull null
                    if (s.cadenceDays == null || s.confidence <= 0.0 || next.isAfter(today.plusDays(horizonDays))) return@mapNotNull null
                    GeneratedItem(s.identity, s.typicalQuantity, askReason(s), preselected = false)
                }
                val starter = if (stats.isEmpty()) sizer.starter(onList).map { GeneratedItem(it.identity, it.quantity, it.reason) } else emptyList()
                due + unsure + starter
            }
            GenerateMode.ROUTINE -> stats.filter { it.hasRhythm }.map { s ->
                GeneratedItem(s.identity, s.typicalQuantity, "נקנה " + Rhythm.text(s.cadenceDays!!) + " · ${s.purchaseDays} פעמים")
            }
            GenerateMode.LAST_TRIP -> {
                val day: (Purchase) -> LocalDate = { Instant.ofEpochMilli(it.boughtAtMillis).atZone(zone).toLocalDate() }
                val last = purchases.maxByOrNull { it.boughtAtMillis }?.let(day)
                if (last == null) emptyList()
                else purchases.filter { day(it) == last }
                    .sortedBy { it.boughtAtMillis }
                    .distinctBy { it.itemId }
                    .map { p -> GeneratedItem(p.identity, p.quantity?.let(TripSizer::wholeCount), "נקנה בקנייה הקודמת") }
            }
        }
        return proposed
            .filter { it.identity.id !in onList }
            .distinctBy { it.identity.id }
            .map { item ->
                when (mode) {
                    GenerateMode.DUE -> sizer.size(item, horizon)
                    GenerateMode.ROUTINE -> sizer.size(item)
                    GenerateMode.LAST_TRIP -> item // what was really bought last time
                }
            }
            .map {
                when (it.identity.id) {
                    in atHome -> it.copy(preselected = false, reason = it.reason + " · יש בבית")
                    in suppressed -> it.copy(preselected = false, reason = it.reason + " · דחיתם לאחרונה")
                    else -> it
                }
            }
            .sortedWith(compareBy({ it.identity.category.order }, { it.identity.displayName }))
    }
}

/**
 * Why an item without a steady rhythm yet is offered, as a question: "נקנה פעמיים, בערך כל חודש · נראה שנגמר. לקנות?".
 */
fun askReason(s: ItemStats): String {
    val times = if (s.purchaseDays == 2) "פעמיים" else "${s.purchaseDays} פעמים"
    val every = s.cadenceDays?.let { ", בערך " + Rhythm.text(it) } ?: ""
    return "נקנה $times$every · נראה שנגמר. לקנות?"
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

enum class SourceFilter(val label: String) { ALL("הכל"), MANUAL("ידני"), AUTO("אוטומטי"), RECIPE("ממתכון"), MENU("מתפריט") }

data class ListFilter(
    val text: String = "",
    val source: SourceFilter = SourceFilter.ALL,
    val categories: Set<Category> = emptySet(),
    val missingOnly: Boolean = false,
    /** Only products that have a conversation in the current shopping. */
    val withChatOnly: Boolean = false,
    /**
     * [text] is what was typed in the quick-add box: compared word by word, like the quick-add suggestions
     * ([ItemSearch]), so "לחם" shows the breads on the list and not "לחמית". The filter panel's own search keeps
     * matching any part of a name ("עגב").
     */
    val wholeWords: Boolean = false,
) {
    val isActive: Boolean
        get() = text.isNotBlank() || source != SourceFilter.ALL || categories.isNotEmpty() || missingOnly || withChatOnly

    /** How many criteria are on (each category counts). */
    val activeCount: Int
        get() = (if (text.isNotBlank()) 1 else 0) + (if (source != SourceFilter.ALL) 1 else 0) +
            categories.size + (if (missingOnly) 1 else 0) + (if (withChatOnly) 1 else 0)

    /** What is being filtered, in words (null when nothing is). Shown while the filter controls are hidden. */
    fun summary(): String? {
        if (!isActive) return null
        val parts = buildList {
            if (text.isNotBlank()) add("חיפוש \"${text.trim()}\"")
            if (source != SourceFilter.ALL) add(source.label)
            if (missingOnly) add("חסרים")
            if (withChatOnly) add("עם שיחה")
            if (categories.isNotEmpty()) add(categories.sortedBy { it.order }.joinToString(", ") { it.label })
        }
        return parts.joinToString(" · ")
    }

    fun matches(
        line: ListLine,
        chatItemIds: Set<String> = emptySet(),
        vocabulary: ItemSearch.Vocabulary? = null,
    ): Boolean {
        if (withChatOnly && line.itemId !in chatItemIds) return false
        if (text.isNotBlank() && !matchesText(line.displayName, vocabulary)) return false
        when (source) {
            SourceFilter.ALL -> Unit
            SourceFilter.MANUAL -> if (!line.isManual) return false
            SourceFilter.AUTO -> if (!line.isAuto) return false
            SourceFilter.RECIPE -> if (!line.isFromRecipe) return false
            SourceFilter.MENU -> if (!line.isFromMenu) return false
        }
        if (categories.isNotEmpty() && line.category !in categories) return false
        if (missingOnly && !line.missing) return false
        return true
    }

    /** Applies to open lines only; bought and removed lines pass through untouched. */
    fun apply(lines: List<ListLine>, chatItemIds: Set<String> = emptySet()): List<ListLine> {
        if (!isActive) return lines
        val vocabulary = if (wholeWords && text.isNotBlank()) ItemSearch.vocabulary(lines.map { it.displayName }) else null
        return lines.filter { it.status != LineStatus.NEEDED || matches(it, chatItemIds, vocabulary) }
    }

    private fun matchesText(name: String, vocabulary: ItemSearch.Vocabulary?): Boolean =
        if (wholeWords) ItemSearch.nameMatches(text, name, vocabulary ?: ItemSearch.vocabulary(emptyList()))
        else HebrewText.matchKey(name).contains(HebrewText.matchKey(text))
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
