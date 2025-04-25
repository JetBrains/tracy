package ai.dev.kit.eval.utils

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
): EvalResult {
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

fun averageSingleScoreEvalResults(results: List<SingleScoreEvalResult>): Double {
    if (results.isEmpty()) return 0.0

    val scoreNames = results.map { it.scoreName }.distinct()
    if (scoreNames.size != 1) {
        println("WARNING: Score names are inconsistent, the score is probably meaningless: $scoreNames")
    }
    return results.map { it.score }.average()
}

fun averageMultiScoreEvalResults(results: List<MultiScoreEvalResult>): Map<String, Double> {
    if (results.isEmpty()) return emptyMap()

    val allScoreNames = results.flatMap { it.scores }.map { it.scoreName }.distinct()
    val listOfNameToScoreMaps = results.map {
        it.scores.associate { it.scoreName to it.score }
    }
    return allScoreNames.associateWith { name ->
        listOfNameToScoreMaps.map { it[name] }.requireNoNulls().average()
    }
}

fun List<EvalResult>.allSingleScore() = all { it is SingleScoreEvalResult }
fun List<EvalResult>.allMultiScore() = all { it is MultiScoreEvalResult }

fun List<EvalResult>.toTable(): Table? {
    if (isEmpty()) return null

    if (allSingleScore()) {
        val results = map { it as SingleScoreEvalResult }
        val scoreNames = results.map { it.scoreName }.distinct()
        if (scoreNames.size != 1) {
            println("WARNING: Score names are inconsistent, could not convert List<SingleScoreEvalResult> to table: $scoreNames")
            return null
        }
        return Column(
            name = scoreNames.first(),
            data = results.map { it.score }
        ).toTable()
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
            .reduceOrNull { acc, table -> acc.join(table) }
    }

    return null
}
