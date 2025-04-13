package ai.dev.kit.example.haiku

import ai.dev.kit.core.eval.AIModel
import ai.dev.kit.core.eval.createOpenAIClient
import ai.dev.kit.eval.mlflow.dataclasses.Generator
import ai.dev.kit.eval.mlflow.fluent.MlflowTracingMetadataConfigurator
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlin.jvm.optionals.getOrElse

class HaikuGenerator(override val model: AIModel) : Generator<String, String> {
    override val prompt = """
    You are a creative and talented poet proficient in Japanese versification.
    
    You goal is to create a haiku about the given word. Haiku should follow the typical haiku structure in English adaptation.
    
    The most important aspects are:
    * Must consists of three lines
    * Must be composed of 17 morae (called "on" in Japanese)   
    * Each morae must be represented by a single English syllable
    * The number of morae must be: 5 in the first line, 7 in the second line, and 5 in the third line.
    * The haiku should include a kireji, or "cutting word", and a kigo, or seasonal reference.    
    * The haiku must contain the word provided.
    
    Adhere to all the rules listed above.
    Generate a haiku about "%s".
    """.trimIndent()

    override val temperature = 1.0

    /**
    Generate a haiku using the [input] provided.
     */
    override suspend fun generate(input: String): String {
        val client = createOpenAIClient(tracingMetadataConfigurator = MlflowTracingMetadataConfigurator)

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage(prompt.format(input))
            .model(ChatModel.Companion.GPT_4O_MINI)
            .temperature(temperature)
            .build()

        return client.chat().completions().create(params).choices().first().message().content().getOrElse { "" }
    }
}
