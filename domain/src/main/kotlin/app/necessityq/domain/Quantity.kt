package app.necessityq.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/** Measurement dimension. Quantities can only be added within the same dimension. */
enum class Dimension { COUNT, WEIGHT, VOLUME }

enum class QuantityUnit(val dimension: Dimension, val toBase: Double, val label: String) {
    PIECE(Dimension.COUNT, 1.0, "יח'"),
    PACKAGE(Dimension.COUNT, 1.0, "חב'"),
    GRAM(Dimension.WEIGHT, 1.0, "גרם"),
    KILOGRAM(Dimension.WEIGHT, 1000.0, "ק\"ג"),
    MILLILITER(Dimension.VOLUME, 1.0, "מ\"ל"),
    LITER(Dimension.VOLUME, 1000.0, "ליטר"),
}

/**
 * A flexible shopping quantity: "3 יח'", "500 גרם", "1.5 ליטר".
 * [amount] is always positive.
 */
data class Quantity(val amount: Double, val unit: QuantityUnit) {
    init {
        require(amount > 0.0) { "Quantity must be positive" }
    }

    val dimension: Dimension get() = unit.dimension

    /** Amount in the dimension's base unit (piece / gram / milliliter). */
    val baseAmount: Double get() = amount * unit.toBase

    /** Adds two quantities of the same dimension; returns null when dimensions differ. */
    operator fun plus(other: Quantity): Quantity? {
        if (dimension != other.dimension) return null
        if (unit == other.unit) return Quantity(amount + other.amount, unit)
        return fromBase(baseAmount + other.baseAmount, dimension)
    }

    fun format(): String = "${formatNumber(amount)} ${unit.label}"

    companion object {
        fun pieces(n: Number) = Quantity(n.toDouble(), QuantityUnit.PIECE)

        /** Builds a readable quantity from a base amount (switches to kg / liter at 1000). */
        fun fromBase(base: Double, dimension: Dimension): Quantity = when (dimension) {
            Dimension.COUNT -> Quantity(base, QuantityUnit.PIECE)
            Dimension.WEIGHT -> if (base >= 1000.0) Quantity(base / 1000.0, QuantityUnit.KILOGRAM) else Quantity(base, QuantityUnit.GRAM)
            Dimension.VOLUME -> if (base >= 1000.0) Quantity(base / 1000.0, QuantityUnit.LITER) else Quantity(base, QuantityUnit.MILLILITER)
        }

        fun formatNumber(value: Double): String {
            val rounded = (value * 100).roundToLong() / 100.0
            return if (abs(rounded - rounded.roundToLong()) < 1e-9) rounded.roundToLong().toString()
            else rounded.toString().trimEnd('0').trimEnd('.')
        }
    }
}
