package ai.dev.kit.examples.haiku

import ai.dev.kit.clients.instrument
import ai.dev.kit.eval.utils.AIInput
import ai.dev.kit.eval.utils.AIOutput
import ai.dev.kit.eval.utils.Generator
import ai.dev.kit.tracing.fluent.KotlinFlowTrace
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import java.time.Duration
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
        val client = instrument(createLiteLLMClient())

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage(prompt.format(input.topic))
            .model(ChatModel.GPT_4O_MINI)
            .temperature(temperature)
            .build()

        val text = client.chat().completions().create(params).choices().first().message().content().getOrElse { "" }
        return HaikuText(text)
    }
}

fun createLiteLLMClient(): OpenAIClient {
    return OpenAIOkHttpClient.builder()
        .baseUrl("https://litellm.labs.jb.gg")
        .apiKey(System.getenv("LITELLM_API_KEY") ?: error("LITELLM_API_KEY environment variable is not set"))
        .timeout(Duration.ofSeconds(60))
        .build()
}
