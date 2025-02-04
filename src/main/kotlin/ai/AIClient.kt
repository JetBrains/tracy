package org.example.ai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class AIModel(val value: String) {
    GPT_4O_MINI("gpt-4o-mini")
}

class AIClient(val apiKey: String) {
    suspend fun chatRequest(model: AIModel, prompt: String, temperature: Double = 1.0): String {
        val requestBody = OpenAiRequest(
            model = model.value,
            messages = listOf(
                Message(role = "user", content = prompt)
            ),
            temperature = temperature
        )

        val response = client.post("https://api.openai.com/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        val jsonObject = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        val messages = jsonObject["choices"]?.jsonArray
        assert(messages!!.size == 1) { "Exactly one completion choice is returned" }

        val result = Result(
            response.status.value,
            messages.first().jsonObject["message"]?.jsonObject["content"]?.jsonPrimitive?.content
        )

        if (result.status != 200 || result.payload == null) {
            throw IllegalStateException("Error during generation")
        }

        return result.payload
    }




    companion object {
        private val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }
    }
}

fun createAIClient(): AIClient {
    return AIClient(getOpenAIKey())
}

private fun getOpenAIKey(): String {
    return System.getenv("OPENAI_API_KEY")
}

@Serializable
data class OpenAiRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double,
    val n: Int = 1,
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

data class Result(
    val status: Int,
    val payload: String?
)
