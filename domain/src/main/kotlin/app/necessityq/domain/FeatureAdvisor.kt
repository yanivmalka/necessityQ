package app.necessityq.domain

/** What accepting a suggestion does. */
enum class AdviceKind {
    /** Turn [FeatureSuggestion.feature] on. */
    TURN_ON,
    /** Turn [FeatureSuggestion.feature] off (a feature nobody uses, to make the screen simpler). */
    TURN_OFF,
    /** Open an item's page with a double tap instead of a single one. */
    DOUBLE_TAP,
    /** Open the "מחירים" tab to pick (another) branch. */
    PICK_BRANCH,
}

/**
 * A suggestion shown as a card above the list: what to change, and why it would help this household — told from
 * its own data ("חלב כל 5 ימים"), never in general terms.
 */
data class FeatureSuggestion(
    /** Stable id: a declined suggestion is never offered again. */
    val id: String,
    val title: String,
    val why: String,
    /** The label of the accepting button. */
    val action: String,
    val kind: AdviceKind,
    val feature: Feature? = null,
)

/** An item bought at a steady pace, with its usual interval. */
data class RoutineItem(val name: String, val everyDays: Int)

/** What the app knows about how it is used: the inputs of [FeatureAdvisor]. All counts are recent ones. */
data class UsageSignals(
    /** Items bought at a steady pace (the "קבוע" ones), most regular first. */
    val routine: List<RoutineItem> = emptyList(),
    /** Items bought in the last 30 days and how many times, most first. */
    val repeated: List<Pair<String, Int>> = emptyList(),
    /** A shop was chosen at the start of a trip. */
    val choseStore: Boolean = false,
    /** Barcodes scanned. */
    val scans: Int = 0,
    val followedBranches: Int = 0,
    val members: Int = 1,
    /** Items marked "חסר" in the shop. */
    val missingMarks: Int = 0,
    /** Times an item's page was opened and closed again at once (opened by mistake). */
    val quickCloses: Int = 0,
    val doubleTap: Boolean = false,
    /** When each feature was last used (opened), in millis; missing = never since [AdvisorState.since]. */
    val lastUsed: Map<Feature, Long> = emptyMap(),
    /** When each feature was last turned on, in millis. */
    val enabledAt: Map<Feature, Long> = emptyMap(),
)

/**
 * The advisor's memory: suggestions declined for good, suggestions put off until a date, and when the user last
 * answered one (a new card waits [FeatureAdvisor.GAP_DAYS] after that).
 */
data class AdvisorState(
    val declined: Set<String> = emptySet(),
    val snoozedUntil: Map<String, Long> = emptyMap(),
    val lastAnsweredAt: Long = 0L,
    /** When the advisor started watching (millis); "not used for a month" counts from here at the earliest. */
    val since: Long = 0L,
) {
    fun decline(id: String, now: Long) = copy(declined = declined + id, lastAnsweredAt = now)
    fun later(id: String, now: Long) = copy(snoozedUntil = snoozedUntil + (id to now + FeatureAdvisor.SNOOZE_DAYS * DAY), lastAnsweredAt = now)
    fun accepted(now: Long) = copy(lastAnsweredAt = now)
    /** A declined suggestion may be offered again. */
    fun restore(id: String) = copy(declined = declined - id)

    fun encode(): String = listOf(
        declined.joinToString(","),
        snoozedUntil.entries.joinToString(",") { "${it.key}=${it.value}" },
        lastAnsweredAt.toString(),
        since.toString(),
    ).joinToString("|")

    companion object {
        const val DAY = 24 * 60 * 60 * 1000L

        fun decode(text: String?): AdvisorState {
            val parts = text?.split('|') ?: return AdvisorState()
            return AdvisorState(
                declined = parts.getOrNull(0).orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
                snoozedUntil = parts.getOrNull(1).orEmpty().split(',').mapNotNull { e ->
                    val i = e.lastIndexOf('=')
                    if (i <= 0) null else e.substring(i + 1).toLongOrNull()?.let { e.substring(0, i) to it }
                }.toMap(),
                lastAnsweredAt = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
                since = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
            )
        }
    }
}

/**
 * Watches how the app is used and suggests the feature that would probably help — or hiding one nobody uses.
 * At most one card at a time, and a new one only a week after the last was answered. Pure rules, so each can be
 * explained and tested.
 */
object FeatureAdvisor {
    const val GAP_DAYS = 7L
    const val SNOOZE_DAYS = 14L
    const val UNUSED_DAYS = 30L
    const val MIN_ROUTINE = 5
    const val MIN_REPEATS = 4
    const val MIN_MISSING = 3
    const val MIN_QUICK_CLOSES = 3

    /** Features that can be hidden when they go unused: each is a whole tab or page of its own. */
    private val HIDEABLE = listOf(Feature.MENU_TAB, Feature.PRICES_TAB, Feature.CHAT)

    /** Every suggestion that applies now, the most helpful first (declined or put off ones included). */
    fun all(features: Features, s: UsageSignals, since: Long, now: Long): List<FeatureSuggestion> = buildList {
        if (Feature.RUNNING_LOW !in features && s.routine.size >= MIN_ROUTINE) {
            val first = s.routine.first()
            val names = s.routine.take(2).joinToString(", ") { it.name }
            add(
                FeatureSuggestion(
                    "on:RUNNING_LOW",
                    "להזכיר לכם לפני שמשהו נגמר?",
                    "$names ועוד ${s.routine.size - 2} מוצרים אתם קונים בקצב קבוע (${first.name}: ${every(first.everyDays)}). " +
                        "עם \"כנראה נגמר בקרוב\" הם יופיעו בראש הרשימה לפני שייגמרו.",
                    "להדליק", AdviceKind.TURN_ON, Feature.RUNNING_LOW,
                ),
            )
        }
        if (Feature.PRICES in features && s.followedBranches == 0) {
            add(
                FeatureSuggestion(
                    "pick-branch",
                    "לבחור את הסניף שלכם?",
                    "המחירים דלוקים, אבל עוד לא נבחר סניף, ולכן אין מחירים ליד הפריטים. בחירת סניף אחד מספיקה.",
                    "בחירת סניף", AdviceKind.PICK_BRANCH,
                ),
            )
        }
        if (Feature.PRICES !in features && (s.choseStore || s.scans > 0)) {
            add(
                FeatureSuggestion(
                    "on:PRICES",
                    "להציג כמה הסל עולה?",
                    (if (s.scans > 0) "סרקתם ${times(s.scans, "מוצר אחד", "מוצרים")} כדי לבדוק מחיר. " else "בחרתם חנות לקנייה. ") +
                        "אפשר להציג את המחיר של כל פריט בסניף שלכם ואת הסכום המשוער של הרשימה.",
                    "להדליק", AdviceKind.TURN_ON, Feature.PRICES,
                ),
            )
        }
        val top = s.repeated.firstOrNull()
        if (Feature.FREQUENT !in features && top != null && top.second >= MIN_REPEATS) {
            add(
                FeatureSuggestion(
                    "on:FREQUENT",
                    "להוסיף בלי להקליד?",
                    "קניתם \"${top.first}\" ${top.second} פעמים בחודש האחרון. עם \"קונים בדרך כלל\" המוצרים הנפוצים שלכם " +
                        "מחכים בכפתור הוספה מהירה.",
                    "להדליק", AdviceKind.TURN_ON, Feature.FREQUENT,
                ),
            )
        }
        if (Feature.CHAT !in features && s.members >= 2 && s.missingMarks >= MIN_MISSING) {
            add(
                FeatureSuggestion(
                    "on:CHAT",
                    "להתייעץ על תחליף מתוך החנות?",
                    "סימנתם ${s.missingMarks} מוצרים כחסרים לאחרונה. בצ'אט פוד אפשר לשאול את בני הבית על המוצר עצמו " +
                        "(\"אין 5%, לקחת 3%?\") ולקבל תשובה עוד בחנות.",
                    "להדליק", AdviceKind.TURN_ON, Feature.CHAT,
                ),
            )
        }
        if (Feature.TRIP_PLAN in features && Feature.PRICES_TAB in features && s.followedBranches == 1) {
            add(
                FeatureSuggestion(
                    "second-branch",
                    "להשוות לסניף נוסף?",
                    "אתם עוקבים אחרי סניף אחד. עם סניף נוסף האפליקציה תראה לפני כל קנייה איפה הרשימה כולה זולה יותר, ובכמה.",
                    "בחירת סניף", AdviceKind.PICK_BRANCH,
                ),
            )
        }
        if (!s.doubleTap && s.quickCloses >= MIN_QUICK_CLOSES) {
            add(
                FeatureSuggestion(
                    "double-tap",
                    "דף הפריט נפתח בטעות?",
                    "דף הפריט נפתח ונסגר מיד ${s.quickCloses} פעמים. בנגיעה כפולה נגיעה בטעות לא תפתח אותו.",
                    "לעבור לנגיעה כפולה", AdviceKind.DOUBLE_TAP,
                ),
            )
        }
        HIDEABLE.filter { it in features }.forEach { f ->
            val from = maxOf(since, s.lastUsed[f] ?: 0L, s.enabledAt[f] ?: 0L)
            if (since > 0 && now - from >= UNUSED_DAYS * AdvisorState.DAY) {
                add(
                    FeatureSuggestion(
                        "off:${f.name}",
                        "להסתיר את ${f.label}?",
                        "לא השתמשתם ב${f.label} כבר חודש. אפשר להסתיר אותו כדי שהמסך יהיה פשוט יותר, ולהחזיר בכל זמן בהגדרות.",
                        "להסתיר", AdviceKind.TURN_OFF, f,
                    ),
                )
            }
        }
    }

    /** The one card to show now, or null: nothing applies, the rest were declined or put off, or a week has not passed. */
    fun next(features: Features, signals: UsageSignals, state: AdvisorState, now: Long): FeatureSuggestion? {
        if (state.lastAnsweredAt > 0 && now - state.lastAnsweredAt < GAP_DAYS * AdvisorState.DAY) return null
        return all(features, signals, state.since, now).firstOrNull { s ->
            s.id !in state.declined && (state.snoozedUntil[s.id] ?: 0L) <= now
        }
    }

    /** A short name of a (declined) suggestion, for the list of declined suggestions. */
    fun describe(id: String): String = when {
        id.startsWith("on:") -> "להדליק " + (featureOf(id)?.label ?: id)
        id.startsWith("off:") -> "להסתיר " + (featureOf(id)?.label ?: id)
        id == "pick-branch" -> "לבחור סניף כדי לראות מחירים"
        id == "second-branch" -> "לעקוב אחרי סניף נוסף ולהשוות"
        id == "double-tap" -> "לפתוח את דף הפריט בנגיעה כפולה"
        else -> id
    }

    /** What accepting the suggestion [id] does, when it is accepted later from the list of declined ones. */
    fun kindOf(id: String): AdviceKind = when {
        id.startsWith("on:") -> AdviceKind.TURN_ON
        id.startsWith("off:") -> AdviceKind.TURN_OFF
        id == "double-tap" -> AdviceKind.DOUBLE_TAP
        else -> AdviceKind.PICK_BRANCH
    }

    fun featureOf(id: String): Feature? = Feature.entries.firstOrNull { it.name == id.substringAfter(':') }

    private fun every(days: Int): String = if (days <= 1) "כל יום" else "כל $days ימים"

    private fun times(n: Int, one: String, many: String) = if (n == 1) one else "$n $many"
}
