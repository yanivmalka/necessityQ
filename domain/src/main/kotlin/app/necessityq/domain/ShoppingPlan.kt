package app.necessityq.domain

// ---------- before a shopping trip: where it pays to buy the list, in one store or split over several ----------

/** What one open line costs at one branch: as usual, and with the best usable promotion there. */
data class LineQuote(val regular: Double, val withPromo: Double, val promotion: Promotion? = null) {
    val cost: Double get() = minOf(regular, withPromo)
}

/** One stop of a trip: a branch and the items bought there. */
data class TripStop(val branchId: String, val itemIds: List<String>, val total: Double)

/** A way to buy the list: one or more stops; [total] over the items compared at every branch. */
data class TripOption(val stops: List<TripStop>) {
    val total: Double get() = stops.sumOf { it.total }
    val branchIds: List<String> get() = stops.map { it.branchId }
}

/** Why a stop pays: an item cheaper there than at the regular store. */
data class TripReason(val itemId: String, val itemName: String, val branchId: String, val saving: Double)

enum class TripAdvice {
    /** the regular store: no other choice saves at least the threshold */
    STAY,
    /** one other store saves at least the threshold */
    SWITCH,
    /** splitting the list over several stores saves at least the threshold for every store other than the regular one */
    SPLIT,
}

data class TripPlan(
    /** the household's regular store */
    val homeBranchId: String,
    val threshold: Double,
    val advice: TripAdvice,
    /** the recommended way to shop */
    val chosen: TripOption,
    /** the regular store alone */
    val home: TripOption,
    /** each branch alone, cheapest first */
    val singles: List<TripOption>,
    /** the cheapest split for 2, 3… stores (up to the chosen number), when it differs from one store */
    val splits: List<TripOption>,
    /** the largest saving any option gives against the regular store, and that option */
    val biggest: TripOption,
    /** items cheaper at the chosen stops than at the regular store, biggest saving first */
    val reasons: List<TripReason>,
    /** item id → branch to buy it at (every open line, also those not compared) */
    val assignment: Map<String, String>,
    /** compared at every branch / open lines in all */
    val compared: Int,
    val open: Int,
    /** open lines with no price at some branch: not in the totals */
    val notCompared: List<String>,
) {
    val saving: Double get() = home.total - chosen.total
    val biggestSaving: Double get() = home.total - biggest.total
}

object ShoppingPlanner {
    /**
     * What [line] costs with [info] (a branch's price picture of the item): the chosen product's price, else the middle
     * of the typical range, times the packages or the weight (as in [ListCost]). The best usable promotion there
     * ([PromoOffers.best]) lowers it when the quantity reaches its minimum ("2 ב-10": three cost two at 5 and one at
     * the regular price). Null when the line can't be priced there.
     */
    fun quote(line: ListLine, info: ItemPriceInfo, promos: BranchPromotions?, nowLocal: String, includeMembers: Boolean): LineQuote? {
        val grams = PieceWeights.gramsPerPiece(line.name)
        val quantity = line.quantity
        val preferred = info.preferred
        val regular: Double = if (preferred != null) {
            val n = ListCost.packages(quantity, preferred.soldByWeight, grams) ?: return null
            val each = if (preferred.soldByWeight && quantity != null) preferred.pricePerKilo ?: return null else preferred.price
            each * n
        } else {
            val byWeight = LineCosts.weighted(line, info)
            val firstByWeight = info.candidates.firstOrNull()?.soldByWeight == true
            when {
                byWeight != null -> (byWeight.low + byWeight.high) / 2
                firstByWeight && quantity != null -> return null
                ItemPrices.byWeight(info.candidates) && !info.perKilo -> return null
                else -> {
                    val n = ListCost.packages(quantity, firstByWeight, grams) ?: return null
                    (info.low + info.high) / 2 * n
                }
            }
        }
        val best = PromoOffers.best(info, promos, nowLocal, includeMembers)
        if (best != null) {
            val (product, promo, unit) = best
            val withPromo = if (product.soldByWeight) {
                // a rate on a weighed product: the same share off the weight's price
                regular * (unit / product.price)
            } else {
                val n = ListCost.packages(quantity, false) ?: 1.0
                if (n + 1e-9 < promo.minQty) null
                else {
                    val sets = kotlin.math.floor(n / maxOf(promo.minQty, 1.0) + 1e-9) * maxOf(promo.minQty, 1.0)
                    sets * unit + (n - sets) * product.price
                }
            }
            if (withPromo != null && withPromo < regular - 0.005) return LineQuote(regular, withPromo, promo)
        }
        return LineQuote(regular, regular)
    }

    /**
     * Plans the trip for the open lines of [lines] over the branches of [quotes] (branch id → line id → quote).
     *
     * Totals are over the lines priced at every branch, so they compare. Every store other than the regular one
     * ([homeBranchId]) has to earn [threshold]: going elsewhere needs a saving of at least [threshold], a split over
     * two other stores at least twice it, a split between the regular store and one more at least once. With
     * [maxStores] 1 the list is not split. Null when nothing can be compared.
     */
    fun plan(
        lines: List<ListLine>,
        quotes: Map<String, Map<String, LineQuote>>,
        homeBranchId: String,
        threshold: Double,
        maxStores: Int,
    ): TripPlan? {
        val open = lines.filter { it.status == LineStatus.NEEDED }.distinctBy { it.itemId }
        val branches = quotes.keys.toList()
        if (branches.isEmpty() || open.isEmpty()) return null
        val home = homeBranchId.takeIf { it in quotes } ?: branches.first()
        val common = open.filter { l -> branches.all { quotes[it]?.get(l.id) != null } }
        if (common.isEmpty()) return null
        fun cost(branch: String, line: ListLine) = quotes.getValue(branch).getValue(line.id).cost

        fun option(set: List<String>): TripOption {
            val byStop = linkedMapOf<String, MutableList<ListLine>>()
            set.forEach { byStop[it] = mutableListOf() }
            for (line in common) {
                // the cheapest stop; a tie stays at the regular store, then at the earlier stop
                val best = set.minWith(compareBy<String>({ cost(it, line) }, { if (it == home) 0 else 1 }))
                byStop.getValue(best) += line
            }
            return TripOption(byStop.map { (b, ls) -> TripStop(b, ls.map { it.itemId }, ls.sumOf { cost(b, it) }) })
        }

        val singles = branches.map { option(listOf(it)) }.sortedBy { it.total }
        val homeOption = singles.first { it.branchIds == listOf(home) }
        // every way to split the list over 2…maxStores of the branches; a stop with nothing to buy is one store fewer
        val combos = (2..maxOf(1, minOf(maxStores, branches.size))).flatMap { k ->
            combinations(branches, k).map { option(it) }.filter { o -> o.stops.all { it.itemIds.isNotEmpty() } }
        }
        val splits = combos.groupBy { it.stops.size }.toSortedMap().values.map { group -> group.minBy { it.total } }

        // each store other than the regular one has to earn the threshold: the best option has the highest saving
        // beyond that, and is taken only when it is not negative (a tie goes to the larger saving)
        fun effort(o: TripOption) = o.branchIds.count { it != home }
        fun net(o: TripOption) = homeOption.total - o.total - threshold * effort(o)
        val chosen = (listOf(homeOption) + singles + combos)
            .filter { net(it) >= -0.005 }
            .maxWith(compareBy<TripOption>({ net(it) }, { homeOption.total - it.total }, { -it.stops.size }))
        val advice = when {
            chosen === homeOption -> TripAdvice.STAY
            chosen.stops.size == 1 -> TripAdvice.SWITCH
            else -> TripAdvice.SPLIT
        }
        val biggest = (singles + combos).minBy { it.total }

        val reasons = if (advice == TripAdvice.STAY) emptyList() else {
            chosen.stops.flatMap { stop ->
                stop.itemIds.mapNotNull { id ->
                    val line = common.first { it.itemId == id }
                    val saving = cost(home, line) - cost(stop.branchId, line)
                    if (stop.branchId == home || saving < 0.005) null else TripReason(id, line.displayName, stop.branchId, saving)
                }
            }.sortedByDescending { it.saving }
        }

        // every open line gets a stop: compared ones as planned; the rest at the regular store when priced there,
        // else at a chosen stop that has it, else the regular store (or the first stop)
        val assignment = LinkedHashMap<String, String>()
        chosen.stops.forEach { s -> s.itemIds.forEach { assignment[it] = s.branchId } }
        val stops = chosen.branchIds
        for (line in open) {
            if (line.itemId in assignment) continue
            assignment[line.itemId] = when {
                home in stops && quotes[home]?.get(line.id) != null -> home
                else -> stops.firstOrNull { quotes[it]?.get(line.id) != null } ?: (home.takeIf { it in stops } ?: stops.first())
            }
        }
        return TripPlan(
            homeBranchId = home,
            threshold = threshold,
            advice = advice,
            chosen = chosen,
            home = homeOption,
            singles = singles,
            splits = splits,
            biggest = biggest,
            reasons = reasons,
            assignment = assignment,
            compared = common.size,
            open = open.size,
            notCompared = open.filter { l -> common.none { it.id == l.id } }.map { it.displayName },
        )
    }

    /** Every [k]-element subset of [items], in order. */
    internal fun <T> combinations(items: List<T>, k: Int): List<List<T>> {
        if (k == 0) return listOf(emptyList())
        if (items.size < k) return emptyList()
        val head = items.first()
        val rest = items.drop(1)
        return combinations(rest, k - 1).map { listOf(head) + it } + combinations(rest, k)
    }
}
