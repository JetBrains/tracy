package ai.dev.kit.example.haiku

import ai.dev.kit.core.eval.createOpenAIClient
import ai.dev.kit.core.fluent.KotlinFlowTrace
import ai.dev.kit.eval.utils.*
import ai.dev.kit.providers.mlflow.BaseEvaluationTest
import ai.dev.kit.providers.mlflow.dataclasses.RunTag
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlin.jvm.optionals.getOrElse

fun haikuTestCase(topic: String) = TestCase<HaikuTopic, NoGroundTruth>(name = topic, HaikuTopic(topic), NoGroundTruth)

class HaikuGeneratorTest :
    BaseEvaluationTest<HaikuTopic, NoGroundTruth, HaikuText, MultiScoreEvalResult>(
        "HaikuGeneratorTest",
        numberOfRuns = 2,
        tags = listOf(RunTag(color = "#00FF00"), RunTag(color = "#FF0000")),
    ) {
    override val testCases: List<TestCase<HaikuTopic, NoGroundTruth>> =
         listOf("table", "computer", "flower", "horse").map { haikuTestCase(it) }

    override val generator: Generator<HaikuTopic, HaikuText> = HaikuGenerator()
    override val evaluator: Evaluator<NoGroundTruth, HaikuText, MultiScoreEvalResult> = HaikuEvaluator()
}

class HaikuEvaluator : Evaluator<NoGroundTruth, HaikuText, MultiScoreEvalResult> {
    override fun evaluate(groundTruth: NoGroundTruth, output: HaikuText): MultiScoreEvalResult {
        val scores = listOf(
            SingleScoreEvalResult(
                scoreName = "Quality",
                score = evaluateQuality(output),
                junitThreshold = 6f,
            ),
            SingleScoreEvalResult(
                scoreName = "Creativity",
                score = evaluateCreativity(output),
                junitThreshold = 6.5f,
            ),
            SingleScoreEvalResult(
                scoreName = "Structure",
                score = evaluateStructure(output),
                junitThreshold = 6f,
            ),
            SingleScoreEvalResult(
                scoreName = "ConsistsOfThreeLines",
                score = if (consistsOfThreeLines(output)) 1f else 0f,
                junitThreshold = 1f,
            )
        )
        return MultiScoreEvalResult(scores)
    }

    override fun aggregateResults(results: List<MultiScoreEvalResult>): List<AggregateScore> =
        averageMultiScoreEvalResults(results).map { (scoreName, score) -> AggregateScore(scoreName, score) }
}

/**
 * Test to evaluate haiku line structure.
 *
 * In English tradition haiku should consist of three lines.
 */
@KotlinFlowTrace(name = "ConsistsOfThreeLines")
fun consistsOfThreeLines(haikuText: HaikuText): Boolean =
    haikuText.text.trim().split("\n").size == 3

@KotlinFlowTrace(name = "Quality")
fun evaluateQuality(haikuText: HaikuText): Float {
    val prompt = """
You are an AI poetry critic. Your job is to evaluate the overall quality of a Haiku based on the following criteria:
1. Structure: It should follow the traditional 3-line Haiku format where the syllable structure is 5-7-5.
2. Imagery: The Haiku should evoke strong and vivid imagery, appealing to the reader's senses.
3. Emotion: It should convey a meaningful or emotional message effectively through simplicity.
4. Originality: The Haiku should feel unique and not appear as something clichéd or generic.
5. Flow: The lines should flow naturally and connect well thematically.

### Guidelines:
- Give a score for the overall quality of the Haiku from **0 to 10**, where 0 means "extremely poor" and 10 means "excellent."
- Return only the score as a single numerical value in your response (e.g., 7.5).
- Do NOT provide any additional context, text, or explanation beyond the score.

Evaluate this Haiku:
""" + haikuText.text

    val client = createOpenAIClient()

    val params = ChatCompletionCreateParams.Companion.builder()
        .addUserMessage(prompt)
        .model(ChatModel.Companion.GPT_4O_MINI)
        .temperature(1.0)
        .build()

    val result = client.chat().completions().create(params).choices().first()
    return result.message().content().getOrElse { "0" }.toFloat()
}

@KotlinFlowTrace(name = "Creativity")
fun evaluateCreativity(haikuText: HaikuText): Float {
    val prompt = """
You are an AI poetry critic highly focused on creativity in poetry. Your task is to evaluate the creativity of a Haiku based on the following guidelines:

1. Uniqueness: The Haiku should stand out as original and not resemble typical clichés or overused expressions.
2. Inventiveness: The imagery should be imaginative and reflect fresh, thought-provoking ideas.
3. Word Choice: The selection of words should be poetic, evocative, and unexpected while maintaining simplicity.
4. Depth: The Haiku should suggest profound meanings or emotions, going beyond surface-level expressions.
5. Artistic Merit: The combination of elements should reflect advanced craftsmanship and creativity.

### Guidelines:
- Provide a creativity score for the Haiku from **0 to 10**, where 0 means "completely uninspired/derivative" and 10 means "exceptionally creative and original."
- Return only the score as a **single numerical value** in your response (e.g., 8.7).
- Do NOT include any reasoning, analysis, or explanation—only provide the score.

Evaluate the creativity of this Haiku:
""" + haikuText.text

    val client = createOpenAIClient()

    val params = ChatCompletionCreateParams.Companion.builder()
        .addUserMessage(prompt)
        .model(ChatModel.Companion.GPT_4O_MINI)
        .temperature(1.0)
        .build()

    val result = client.chat().completions().create(params).choices().first()
    return result.message().content().getOrElse { "0" }.toFloat()
}


@KotlinFlowTrace(name = "Structure")
fun evaluateStructure(haikuText: HaikuText): Float {
    val prompt = """
You are a Haiku poetry expert and your task is to evaluate the structural correctness of a Haiku. Haiku should adhere to the following strict rules:

1. Line Count: The Haiku must consist of 3 lines.
2. Morae Count: The Haiku should consist of exactly 17 morae (syllables):
    - The first line must have 5 syllables.
    - The second line must have 7 syllables.
    - The third line must have 5 syllables.
3. Kireji (Cutting Word): The Haiku must include a cutting word to provide a pause or emphasis.
4. Seasonal Reference (Kigo): The Haiku must incorporate a seasonal word or imagery.

### Guidelines:
- Provide a score based on structural adherence:
    - **0** means the Haiku violates all structural rules.
    - **10** means the Haiku perfectly adheres to all structural rules.
- Return only the score as a **single numerical value** (e.g., 8.5).
- Do NOT provide additional details or commentary—simply return the numerical score.

Evaluate the structure of this Haiku:
""" + haikuText.text
    val client = createOpenAIClient()

    val params = ChatCompletionCreateParams.Companion.builder()
        .addUserMessage(prompt)
        .model(ChatModel.Companion.GPT_4O_MINI)
        .temperature(1.0)
        .build()

    val result = client.chat().completions().create(params).choices().first()
    return result.message().content().getOrElse { "0" }.toFloat()
}
