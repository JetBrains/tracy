package ai.features.haiku

import kotlinx.coroutines.runBlocking
import org.example.ai.AIModel
import org.example.ai.createAIClient
import org.example.ai.features.haiku.HaikuGenerator
import org.example.ai.mlflow.BaseEvaluationTest
import org.example.ai.mlflow.dataclasses.EvaluationCriteria
import org.example.ai.mlflow.dataclasses.RunTag
import org.example.ai.mlflow.dataclasses.TestCase
import org.example.ai.mlflow.fluent.KotlinFlowTrace


class HaikuGeneratorTest :
    BaseEvaluationTest<String, String, Double>(
        "HaikuGeneratorTest",
        numberOfRuns = 1,
        tags = listOf(RunTag(color = "#FF0000")),
    ) {
    override fun testCases(): List<TestCase<String>> {
        return listOf(
            TestCase("table"),
            TestCase("computer"),
            TestCase("flower"),
            TestCase("horse")
        )
    }

    override fun model(): HaikuGenerator {
        return HaikuGenerator(AIModel.GPT_4O_MINI)
    }

    override fun testFunctions(): List<EvaluationCriteria<String, Double>> {
        return listOf(
            Structure,
            Quality,
            Creativity
        )
    }

    override fun getMLflowRunsDir() = "/Users/Anton.Bragin/PycharmProjects/litellm-test/mlruns"
}

/**
 * Test to evaluate haiku line structure.
 *
 * In English tradition haiku should consist of three lines.
 */
object ConsistsOfThreeLines : EvaluationCriteria<String, Double>("consists of three lines", 1.0) {
    @KotlinFlowTrace(name = "Three Lines")
    override fun evaluate(output: String): Double {
        return if (output.split("\n").size == 3) {
            1.0
        } else {
            0.0
        }
    }
}

object Quality : EvaluationCriteria<String, Double>("quality") {
    @KotlinFlowTrace(name = "Quality")
    override fun evaluate(output: String): Double {
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
""" + output

        val client = createAIClient()
        val result = runBlocking { client.chatRequest(AIModel.GPT_4O_MINI, prompt, temperature = 1.0).toDouble() }
        return result
    }
}

object Creativity : EvaluationCriteria<String, Double>("creativity") {
    @KotlinFlowTrace(name = "Creativity")
    override fun evaluate(output: String): Double {
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
""" + output

        val client = createAIClient()
        val result = runBlocking {
            client.chatRequest(AIModel.GPT_4O_MINI, prompt, temperature = 1.0).toDouble()
        }
        return result
    }
}

object Structure : EvaluationCriteria<String, Double>("structure") {
    @KotlinFlowTrace(name = "Structure")
    override fun evaluate(output: String): Double {
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
""" + output

        val client = createAIClient()
        val result = runBlocking {
            client.chatRequest(AIModel.GPT_4O_MINI, prompt, temperature = 1.0).toDouble()
        }
        return result
    }
}