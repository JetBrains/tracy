package ai.dev.kit.examples.eval

import ai.dev.kit.eval.utils.AIInput
import ai.dev.kit.eval.utils.AIOutput
import ai.dev.kit.eval.utils.Generator
import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlin.jvm.optionals.getOrElse

data class HaikuTopic(val topic: String) : AIInput
data class HaikuText(val text: String) : AIOutput

class HaikuGenerator : Generator<HaikuTopic, HaikuText> {
    val prompt = """
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

    val temperature: Double = 1.0

    /**
    Generate a haiku using the [input] provided.
     */
    @KotlinFlowTrace(name = "GenerateHaiku")
    override suspend fun generate(input: HaikuTopic): HaikuText {
        val apiToken = System.getenv("OPENAI_API_KEY")
            ?: error("Environment variable 'OPENAI_API_KEY' is not set")
        val client = OpenAIOkHttpClient.builder().apiKey(apiToken).build()

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage(prompt.format(input.topic))
            .model(ChatModel.GPT_4O_MINI)
            .temperature(temperature)
            .build()

        val text = client.chat().completions().create(params).choices().first().message().content().getOrElse { "" }
        return HaikuText(text)
    }
}
