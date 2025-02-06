package ai.tracing

import com.openai.models.ChatCompletionCreateParams
import com.openai.models.ChatModel
import org.example.ai.createOpenAIClient
import org.junit.jupiter.api.Test


class TestTracing {
    @Test
    fun testOpenAIAutoTracing() {
        val client = createOpenAIClient()

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.Companion.GPT_4O_MINI)
            .temperature(1.1)
            .build()
        val completions = client.chat().completions().create(params)

        println(completions.choices().first().message().content().get())
    }
}