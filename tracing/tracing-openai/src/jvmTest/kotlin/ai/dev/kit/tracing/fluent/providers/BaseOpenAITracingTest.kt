package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.LITELLM_URL
import com.openai.core.JsonArray
import com.openai.core.JsonString
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionMessageToolCall
import com.openai.models.chat.completions.ChatCompletionTool
import com.openai.models.responses.FunctionTool
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.provider.Arguments
import java.io.File
import java.util.Base64
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseOpenAITracingTest : BaseOpenTelemetryTracingTest() {
    protected fun validateBasicTracing() {
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )
        assertEquals(
            trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]?.startsWith(ChatModel.GPT_4O_MINI.asString()),
            true
        )
        val content = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
    }

    protected fun validateErrorStatus() {
        val traces = analyzeSpans()
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertEquals(
            LITELLM_URL,
            trace.attributes[AttributeKey.stringKey("gen_ai.api_base")]
        )

        assertEquals(trace.attributes[AttributeKey.stringKey("gen_ai.error.message")]?.isNotEmpty(), true)
        assertEquals(trace.attributes[AttributeKey.stringKey("gen_ai.error.code")]?.isNotEmpty(), true)
    }

    protected fun validateToolCall() {
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("function", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.type")])
        assertEquals(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.description")]?.isNotEmpty(), true)
        assertEquals(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.parameters")]?.isNotEmpty(), true)

        // if AI called the tool when check its props
        if (trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertEquals(
                trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")]?.isNotEmpty(),
                true
            )
            assertEquals(
                trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.call.id")]?.isNotEmpty(),
                true
            )
            assertEquals(
                trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.call.type")]?.isNotEmpty(),
                true
            )
            assertEquals(
                trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.arguments")]?.isNotEmpty(),
                true
            )
        }
    }

    protected fun validateToolCallResponse() {
        val traces = analyzeSpans()

        assertEquals(2, traces.size)
        // contains AI's request for a tool call
        val toolCallRequestTrace = traces.firstOrNull()
        // contains an answer to a tool call
        val toolCallResponseTrace = traces.lastOrNull()
        assertNotNull(toolCallRequestTrace)
        assertNotNull(toolCallResponseTrace)

        assertEquals("hi", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("function", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.0.type")])

        // if AI called the tool when check its props
        if (toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.role")])
            assertEquals(
                toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.content")]?.isNotEmpty(),
                true
            )
            assertEquals(
                toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.tool_call_id")]?.isNotEmpty(),
                true
            )
        }
    }

    fun validateMultipleToolCallResponseWithInput() {
        val traces = analyzeSpans()
        assertEquals(2, traces.size)

        val toolCallRequestTrace = traces.first()
        val toolCallResponseTrace = traces.last()

        assertEquals("hi", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("goodbye", toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.tool.1.name")])

        if (toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertEquals(
                toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")]?.isNotEmpty(),
                true
            )
            assertEquals(
                toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.1.name")]?.isNotEmpty(),
                true
            )

            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.role")])
            assertEquals(
                toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.content")]?.isNotEmpty(),
                true
            )
            assertEquals(
                toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.tool_call_id")]?.isNotEmpty(),
                true
            )

            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.role")])
            assertEquals(
                toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.content")]?.isNotEmpty(),
                true
            )
            assertEquals(
                toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.tool_call_id")]?.isNotEmpty(),
                true
            )
        }
    }

    protected fun createTool(word: String): ChatCompletionTool {
        val functionTool = ChatCompletionFunctionTool.builder()
            .type(JsonString.of("function"))
            .function(
                FunctionDefinition.builder()
                    .description("Say $word to the user")
                    .name(word)
                    .parameters(
                        FunctionParameters.builder()
                            .putAdditionalProperty("type", JsonValue.from("object"))
                            .putAdditionalProperty(
                                "properties",
                                JsonValue.from(mapOf("name" to mapOf("type" to "string")))
                            )
                            .putAdditionalProperty("required", JsonArray.of(listOf(JsonString.of("name"))))
                            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                            .build()
                    )
                    .build()
            )
            .build()

        return ChatCompletionTool.ofFunction(functionTool)
    }

    protected fun createFunctionTool(word: String): FunctionTool {
        val schema = JsonValue.from(
            mapOf(
                "type" to "object",
                "properties" to mapOf("name" to mapOf("type" to "string")),
                "required" to listOf("name"),
                "additionalProperties" to false
            )
        )
        return FunctionTool.builder()
            .name(word)
            .description("Say $word to the user")
            .parameters(schema)
            .strict(false)
            .build()
    }

    protected fun validateStreaming(output: String) {
        val traces = analyzeSpans()
        assertEquals(1, traces.size)
        val trace = traces.first()
        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.completion.content.type")]?.startsWith("text/event-stream") == true)
        assertTrue(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]?.isNotEmpty() == true)
        assertEquals(output, trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")])
    }

    protected fun provideImagesForUpload(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(MediaSource.File(
                filepath = "./image.jpg",
                contentType = "image/jpeg",
            )),
            Arguments.of(MediaSource.Link(CAT_IMAGE_URL))
        )
    }

    protected fun provideFilesForUpload(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(MediaSource.File(
                filepath = "./sample.pdf",
                contentType = "application/pdf",
            )),
            Arguments.of(MediaSource.Link(SAMPLE_PDF_FILE_URL))
        )
    }

    protected fun MediaSource.File.toDataUrl(): String {
        val encodedData = loadFileAsBase64Encoded(this.filepath)
        return "data:$contentType;base64,${encodedData}"
    }

    protected fun loadFileAsBase64Encoded(filepath: String): String {
        val file = loadFile(filepath)
        return Base64.getEncoder().encodeToString(file.readBytes())
    }

    protected fun loadFile(filepath: String): File {
        val classLoader = Thread.currentThread().contextClassLoader
        val file = classLoader.getResource(filepath)?.file?.let { File(it) }
            ?: error("Could not find file at $filepath")
        return file
    }

    protected val ChatCompletionMessageToolCall.id: String
        get() {
            val toolCall = this
            val id = if (toolCall.isFunction()) {
                toolCall.function().get().id()
            } else if (toolCall.isCustom()) {
                toolCall.custom().get().id()
            } else {
                throw IllegalStateException("Cannot extract ID of the tool call $toolCall")
            }
            return id
        }

    protected val ChatCompletionMessageToolCall.name: String
        get() {
            val toolCall = this
            val name = if (toolCall.isFunction()) {
                toolCall.function().get().function().name()
            }
            else if (toolCall.isCustom()) {
                toolCall.custom().get().custom().name()
            }
            else {
                throw IllegalStateException("Cannot extract name of the tool call $toolCall")
            }
            return name
        }

    companion object {
        protected const val CAT_IMAGE_URL = "https://images.pexels.com/photos/104827/cat-pet-animal-domestic-104827.jpeg"
        protected const val SAMPLE_PDF_FILE_URL = "https://pdfobject.com/pdf/sample.pdf"

        sealed class MediaSource {
            data class File(
                val filepath: String,
                val contentType: String,
            ) : MediaSource()
            data class Link(val url: String) : MediaSource()
        }
    }
}