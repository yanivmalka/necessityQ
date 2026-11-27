package app.necessityq.data.firebase

import app.necessityq.domain.BaselineEstimator
import app.necessityq.domain.QuantityUnit
import app.necessityq.domain.SurveyItem
import app.necessityq.domain.SurveyPeriod
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The amounts the first-list survey proposes, estimated by Gemini from the ages of the people at home through the
 * `estimateBaseline` cloud function (firebase/functions/src/baseline.ts). Only ages are sent, no names. Null when it
 * could not ask: offline, too slow, or the daily limit; the survey then proposes the local table's amounts.
 */
class FirebaseBaselineEstimator(private val functions: FirebaseFunctions) : BaselineEstimator {
    override suspend fun estimate(householdId: String, ages: List<Int>, items: List<SurveyItem>): Map<String, Double>? {
        if (ages.isEmpty() || items.isEmpty()) return null
        val args = mapOf(
            "householdId" to householdId,
            "ages" to ages,
            "items" to items.map { mapOf("name" to it.name, "unit" to unitName(it.unit), "period" to periodName(it.period)) },
        )
        val result = withTimeoutOrNull(TIMEOUT_MS) { functions.getHttpsCallable("estimateBaseline").call(args).await() } ?: return null
        val amounts = (result.data as? Map<*, *>)?.get("amounts") as? Map<*, *> ?: return null
        return amounts.mapNotNull { (k, v) ->
            val name = k as? String ?: return@mapNotNull null
            val amount = (v as? Number)?.toDouble() ?: return@mapNotNull null
            name to amount
        }.toMap()
    }

    private fun unitName(unit: QuantityUnit) = when (unit) {
        QuantityUnit.PIECE -> "piece"
        QuantityUnit.PACKAGE -> "package"
        QuantityUnit.KILOGRAM -> "kilogram"
        QuantityUnit.GRAM -> "gram"
        QuantityUnit.LITER -> "liter"
        QuantityUnit.MILLILITER -> "milliliter"
    }

    private fun periodName(period: SurveyPeriod) = when (period) {
        SurveyPeriod.WEEK -> "week"
        SurveyPeriod.MONTH -> "month"
    }

    private companion object {
        /** Longer than this, the survey shows the table's amounts rather than wait. */
        const val TIMEOUT_MS = 20_000L
    }
}
