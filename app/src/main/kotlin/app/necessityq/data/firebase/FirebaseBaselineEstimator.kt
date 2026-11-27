package app.necessityq.data.firebase

import app.necessityq.domain.BaselineEstimator
import app.necessityq.domain.EstimateException
import app.necessityq.domain.EstimateFailure
import app.necessityq.domain.QuantityUnit
import app.necessityq.domain.SurveyItem
import app.necessityq.domain.SurveyPeriod
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The amounts the first-list survey proposes, estimated by Gemini from the ages of the people at home through the
 * `estimateBaseline` cloud function (firebase/functions/src/baseline.ts). Only ages are sent, no names. Null when it
 * could not ask: it throws an [EstimateException] saying why (offline, too slow, the daily limit, signed out, an
 * unreadable answer); the survey then tells the user and proposes the local table's amounts.
 */
class FirebaseBaselineEstimator(private val functions: FirebaseFunctions) : BaselineEstimator {
    override suspend fun estimate(householdId: String, ages: List<Int>, items: List<SurveyItem>): Map<String, Double>? {
        if (ages.isEmpty() || items.isEmpty()) return null
        val args = mapOf(
            "householdId" to householdId,
            "ages" to ages,
            "items" to items.map { mapOf("name" to it.name, "unit" to unitName(it.unit), "period" to periodName(it.period)) },
        )
        val reply = try {
            withTimeoutOrNull(TIMEOUT_MS) { functions.getHttpsCallable("estimateBaseline").call(args).await() }
        } catch (e: FirebaseFunctionsException) {
            throw EstimateException(
                when (e.code) {
                    FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> EstimateFailure.TIMEOUT
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> EstimateFailure.LIMIT
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> EstimateFailure.SIGNED_OUT
                    FirebaseFunctionsException.Code.UNAVAILABLE -> EstimateFailure.OFFLINE
                    else -> EstimateFailure.ERROR
                },
                e,
            )
        } catch (e: java.io.IOException) {
            throw EstimateException(EstimateFailure.OFFLINE, e)
        }
        val result = reply ?: throw EstimateException(EstimateFailure.TIMEOUT)
        val amounts = (result.data as? Map<*, *>)?.get("amounts") as? Map<*, *> ?: throw EstimateException(EstimateFailure.BAD_REPLY)
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
