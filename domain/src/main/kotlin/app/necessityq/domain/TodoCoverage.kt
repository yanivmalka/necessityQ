package app.necessityq.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * How well the Microsoft To Do history documents the household's shopping.
 *
 * The household shops about once a week (rarely every week and a half to two weeks), but To Do was not
 * used on every trip, and its use stopped altogether a while before the app took over. So in the To Do
 * era a week with no record does not mean nothing was bought — it means the trip was not documented.
 * Also, the store had no reception, so check-offs reached the server only after leaving the store,
 * sometimes after midnight or the next day.
 *
 *  1. **Trips:** To Do check-offs are placed on a "shopping day" (before 05:00 counts as the evening
 *     before), and shopping days that follow each other are merged into one trip, dated by its first day.
 *  2. **Undocumented trips:** between two consecutive To Do trips that are `g` days apart we assume
 *     `round(g / 7) − 1` trips that were not documented — nothing for up to 10 days, one for 11–17, and so
 *     on. The same holds after the last To Do trip, up to the first purchase checked off in the app (or
 *     up to today when the app has not recorded one yet).
 *  3. The statistics engine uses this to skip intervals that span an undocumented trip (the item may have
 *     been bought there) and to avoid calling an item "overdue" just because its last purchase was before
 *     a stretch with no documentation.
 */
class TodoCoverage private constructor(
    /** Documented To Do trips, by first day, ascending. */
    val trips: List<LocalDate>,
    /** Stretches whose trips were (at least partly) not documented, ascending. */
    val gaps: List<Gap>,
    /** The household's real trip interval in the To Do era, counting undocumented trips too. */
    val tripIntervalDays: Double,
    private val tripByDay: Map<LocalDate, LocalDate>,
    private val zone: ZoneId,
) {
    /**
     * Between the documented trip [after] and [until] (the next documented trip, or the day documentation
     * resumed) about [missingTrips] trips took place without a record.
     */
    data class Gap(val after: LocalDate, val until: LocalDate, val missingTrips: Int)

    val missingTrips: Int get() = gaps.sumOf { it.missingTrips }

    /** The trip a To Do purchase belongs to; null for purchases recorded in the app. */
    fun tripOf(purchase: Purchase): LocalDate? =
        if (purchase.isFromTodo) tripByDay[shoppingDay(purchase.boughtAtMillis, zone)] else null

    /** True when an undocumented trip may have happened strictly between the trips on [from] and [to]. */
    fun hasUndocumentedTripBetween(from: LocalDate, to: LocalDate): Boolean =
        gaps.any { !it.after.isBefore(from) && !it.until.isAfter(to) }

    /**
     * The day from which the household's shopping is documented without gaps, looking forward from [day]:
     * the end of the last undocumented stretch that starts on or after [day], or null if there is none.
     */
    fun documentedFromAfter(day: LocalDate): LocalDate? =
        gaps.filter { !it.after.isBefore(day) }.maxOfOrNull { it.until }

    /** Documented To Do trips after [first] up to and including the last one. */
    fun tripsAfter(first: LocalDate): Int = trips.count { it.isAfter(first) }

    companion object {
        /** Check-offs before this hour belong to the previous evening's trip. */
        const val DAY_STARTS_AT_HOUR = 5L

        fun shoppingDay(millis: Long, zone: ZoneId): LocalDate =
            Instant.ofEpochMilli(millis).atZone(zone).minusHours(DAY_STARTS_AT_HOUR).toLocalDate()

        /** Null when there is no To Do history; then every purchase is treated as documented. */
        fun of(
            purchases: List<Purchase>,
            today: LocalDate,
            zone: ZoneId,
            typicalTripDays: Double = 7.0,
        ): TodoCoverage? {
            val todoDays = purchases.filter { it.isFromTodo }
                .map { shoppingDay(it.boughtAtMillis, zone) }
                .distinct()
                .sorted()
            if (todoDays.isEmpty()) return null

            // merge consecutive shopping days into one trip
            val clusters = mutableListOf(mutableListOf(todoDays.first()))
            for (day in todoDays.drop(1)) {
                val current = clusters.last()
                if (ChronoUnit.DAYS.between(current.last(), day) <= 1) current.add(day) else clusters.add(mutableListOf(day))
            }
            val trips = clusters.map { it.first() }
            val tripByDay = clusters.flatMap { c -> c.map { it to c.first() } }.toMap()
            val lastTodoDay = clusters.last().last()

            fun missingBetweenTrips(days: Long) = ((days / typicalTripDays).roundToInt() - 1).coerceAtLeast(0)

            val gaps = mutableListOf<Gap>()
            trips.zipWithNext { a, b ->
                val missing = missingBetweenTrips(ChronoUnit.DAYS.between(a, b))
                if (missing > 0) gaps.add(Gap(a, b, missing))
            }

            // after the last To Do trip: until the app's first own record, or until today
            val firstAppDay = purchases.filter { !it.isFromTodo }
                .map { shoppingDay(it.boughtAtMillis, zone) }
                .filter { it.isAfter(lastTodoDay) }
                .minOrNull()
            val lastTrip = trips.last()
            val tailMissing = if (firstAppDay != null) {
                missingBetweenTrips(ChronoUnit.DAYS.between(lastTrip, firstAppDay))
            } else {
                // trips that should already have happened by today
                (ChronoUnit.DAYS.between(lastTrip, today) / typicalTripDays).toInt().coerceAtLeast(0)
            }
            if (tailMissing > 0) gaps.add(Gap(lastTrip, firstAppDay ?: today, tailMissing))

            val span = ChronoUnit.DAYS.between(trips.first(), trips.last()).toDouble()
            val intervalsInSpan = (trips.size - 1) + gaps.filter { it.until <= lastTrip }.sumOf { it.missingTrips }
            val tripInterval = if (intervalsInSpan > 0) span / intervalsInSpan else typicalTripDays

            return TodoCoverage(trips, gaps, tripInterval, tripByDay, zone)
        }
    }
}
