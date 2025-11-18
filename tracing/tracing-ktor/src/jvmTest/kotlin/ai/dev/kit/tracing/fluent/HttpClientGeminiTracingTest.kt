package ai.dev.kit.tracing.fluent

import ai.dev.kit.adapters.GeminiLLMTracingAdapter
import ai.dev.kit.instrument
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assumptions.assumingThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("gemini")
class HttpClientGeminiTracingTest : BaseOpenTelemetryTracingTest() {
    val llmProviderUrl: String? = System.getenv("LLM_PROVIDER_URL")
    val llmProviderApiKey =
        System.getenv("GEMINI_API_KEY") ?: System.getenv("LLM_PROVIDER_API_KEY")
        ?: error("LLM_PROVIDER_API_KEY environment variable is not set")

    @Test
    fun `test Ktor HttpClient auto tracing for Gemini`() = runTest {
        // TODO: fix
        // Gemini tests for now is only supported with LiteLLM
        assumingThat(llmProviderUrl?.startsWith("https://litellm.labs.jb.gg") == true) {
            runBlocking {
                val client: HttpClient = instrument(HttpClient(), GeminiLLMTracingAdapter())

                val model = "gemini-2.5-flash"
                val promptMessage = "Explain how AI works in a few words"

                val projectId = "jetbrains-grazie"
                val location = "us-central1"
                val url =
                    "${llmProviderUrl}/vertex_ai/v1/projects/$projectId/locations/$location/publishers/google/models/$model:generateContent"

                val response = client.post(url) {
                    // TODO: fix
                    header("x-litellm-api-key", "Bearer $llmProviderApiKey")
                    header("Content-Type", "application/json")
                    setBody(
                        """
                {
                    "contents": [
                        {
                            "parts": [
                                { "text": "$promptMessage" }
                            ],
                            "role": "user"
                        }
                    ]
                }
            """.trimIndent()
                    )
                }

                val traces = analyzeSpans()

                assertEquals(1, traces.size)
                val trace = traces.firstOrNull()
                assertNotNull(trace)

                assertEquals(StatusCode.OK, trace.status.statusCode)
                assertEquals("gemini", trace.attributes[AttributeKey.stringKey("gen_ai.system")])
                assertEquals(
                    llmProviderUrl,
                    trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
                )

                val tracedModel = trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]
                assertNotNull(tracedModel)
                assertTrue(tracedModel.startsWith(model))

                val tracedPrompt = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
                assertEquals(promptMessage, tracedPrompt)

                val completionRole = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.role")]
                val completionContent = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]

                assertFalse(completionRole.isNullOrEmpty())

                assertFalse(completionContent.isNullOrEmpty())

                // assert that tracing doesn't consume the response body
                val responseBody = response.bodyAsText()
                assertTrue(responseBody.isNotEmpty())

                val responseJson = Json.parseToJsonElement(responseBody).jsonObject

                assertEquals(
                    responseJson["responseId"]!!.jsonPrimitive.content,
                    trace.attributes[AttributeKey.stringKey("gen_ai.response.id")]
                )
                assertEquals(responseJson["modelVersion"]!!.jsonPrimitive.content, tracedModel)

                val firstResponseContent = responseJson["candidates"]?.jsonArray[0]?.jsonObject["content"]?.jsonObject

                assertEquals(
                    firstResponseContent?.get("role")?.jsonPrimitive?.content,
                    completionRole
                )
                assertEquals(
                    firstResponseContent?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject["text"]?.jsonPrimitive?.content,
                    completionContent
                )
                assertEquals(
                    responseJson["usageMetadata"]!!.jsonObject["promptTokenCount"]!!.jsonPrimitive.int,
                    trace.attributes[AttributeKey.longKey("gen_ai.usage.input_tokens")]!!.toInt()
                )
                assertEquals(
                    responseJson["usageMetadata"]!!.jsonObject["candidatesTokenCount"]!!.jsonPrimitive.int,
                    trace.attributes[AttributeKey.longKey("gen_ai.usage.output_tokens")]!!.toInt()
                )
            }
        }
    }
}
