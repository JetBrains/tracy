package ai.dev.kit.eval.utils

import mu.KotlinLogging
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf

private val logger = KotlinLogging.logger {}

/**
 * The score or several scores assigned by [Evaluator] to
 * a particular [AIOutput].
 */
interface EvalResult {
    val hasJunitTestSucceeded: Boolean
        get() = true
}

open class SingleScoreEvalResult(
    val scoreName: String,
    val score: Float,
    junitThreshold: Float = 0.0f
) : EvalResult {
    override val hasJunitTestSucceeded: Boolean = score >= junitThreshold

    override fun toString(): String = "SingleScoreEvalResult[$scoreName=$score]"
}

class MultiScoreEvalResult(val scores: List<SingleScoreEvalResult>) : EvalResult {
    override val hasJunitTestSucceeded: Boolean = scores.all { it.hasJunitTestSucceeded }

    override fun toString(): String =
        scores
            .joinToString(", ") { "${it.scoreName}=${it.score}" }
            .let { "MultiScoreEvalResult[$it]" }
}

fun averageSingleScoreEvalResults(results: List<SingleScoreEvalResult>): Double? {
    if (results.isEmpty()) return 0.0

    val scoreNames = results.map { it.scoreName }.distinct()
    if (scoreNames.size != 1) {
        logger.warn{"Score names are inconsistent, cannot compute score: $scoreNames"}
        return null
    }
    return results.map { it.score }.average()
}

fun averageMultiScoreEvalResults(results: List<MultiScoreEvalResult>): Map<String, Double> {
    if (results.isEmpty()) return emptyMap()

    val allScoreNames = results.flatMap { it.scores }.map { it.scoreName }.distinct()
    val listOfNameToScoreMaps = results.map {
        it.scores.associate { it.scoreName to it.score }
    }
    val nameToAvg = mutableMapOf<String, Double>()
    allScoreNames.forEach { name ->
        val scores = listOfNameToScoreMaps.map { it[name] ?: return@forEach }
        nameToAvg[name] = scores.average()
    }
    return nameToAvg
}

fun List<EvalResult>.allSingleScore() = all { it is SingleScoreEvalResult }
fun List<EvalResult>.allMultiScore() = all { it is MultiScoreEvalResult }

fun List<EvalResult>.toTable(): DataFrame<Float>? {
    if (isEmpty()) return null

    if (allSingleScore()) {
        val results = map { it as SingleScoreEvalResult }
        val scoreNames = results.map { it.scoreName }.distinct()
        if (scoreNames.size != 1) {
            logger.warn{"Score names are inconsistent, could not convert List<SingleScoreEvalResult> to table: $scoreNames"}
            return null
        }
        return dataFrameOf(scoreNames.first() to results.map { it.score }).cast<Float>()
    } else if (allMultiScore()) {
        val results = map { it as MultiScoreEvalResult }
        val allScoreNames = results.flatMap { it.scores }.map { it.scoreName }.distinct()
        val listOfNameToScoreMaps = results.map {
            it.scores.associate { it.scoreName to it }
        }
        return allScoreNames
            .map { name ->
                listOfNameToScoreMaps.map { it[name] ?: return null }
            }
            .mapNotNull { it.toTable() }
            .reduceOrNull { acc, table -> acc.add(table) }
    }

    return null
}

