package ai.dev.kit.tracing.fluent.providers

import ai.dev.kit.exporters.SupportedMediaContentTypes
import ai.dev.kit.exporters.UploadableMediaContentAttributeKeys
import ai.dev.kit.tracing.BaseOpenTelemetryTracingTest
import ai.dev.kit.tracing.autologging.createOpenAIClient
import com.openai.client.OpenAIClient
import com.openai.core.ClientOptions.Companion.PRODUCTION_URL
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
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.provider.Arguments
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseOpenAITracingTest : BaseOpenTelemetryTracingTest() {
    /**
     * When no value is provided, defaults to [PRODUCTION_URL].
     */
    protected val llmProviderUrl: String? = System.getenv("LLM_PROVIDER_URL")

    protected val llmProviderApiKey =
        System.getenv("OPENAI_API_KEY") ?: System.getenv("LLM_PROVIDER_API_KEY")
        ?: error("LLM_PROVIDER_API_KEY environment variable is not set")

    protected fun createOpenAIClient(): OpenAIClient {
        return createOpenAIClient(llmProviderUrl, llmProviderApiKey)
    }

    protected fun validateBasicTracing(model: ChatModel) {
        validateBasicTracing(model.asString())
    }

    protected fun validateBasicTracing(model: String) {
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()

        assertNotNull(trace)
        assertTrue(
            (llmProviderUrl ?: PRODUCTION_URL)
                .startsWith(trace.attributes[AttributeKey.stringKey("gen_ai.api_base")].toString())
        )

        val responseModel = trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]
        assertNotNull(responseModel)
        assertTrue(responseModel.startsWith(model))

        val content = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertFalse(content.isNullOrEmpty())
    }

    protected fun validateErrorStatus() {
        val traces = analyzeSpans()
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals(StatusCode.ERROR, trace.status.statusCode)
        assertTrue(
            (llmProviderUrl
                ?: PRODUCTION_URL).startsWith(trace.attributes[AttributeKey.stringKey("gen_ai.api_base")].toString())
        )

        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.error.message")].isNullOrEmpty())
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.error.code")].isNullOrEmpty())
    }

    protected fun validateToolCall() {
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()
        assertNotNull(trace)

        assertEquals("hi", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")])
        assertEquals("function", trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.type")])
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.description")].isNullOrEmpty())
        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.parameters")].isNullOrEmpty())

        // if AI called the tool when check its props
        if (trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls") {
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")].isNullOrEmpty())
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.call.id")].isNullOrEmpty())
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.call.type")].isNullOrEmpty())
            assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.arguments")].isNullOrEmpty())
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
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.content")].isNullOrEmpty())
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.tool_call_id")].isNullOrEmpty())
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
            assertFalse(toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")].isNullOrEmpty())
            assertFalse(toolCallRequestTrace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.1.name")].isNullOrEmpty())

            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.role")])
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.content")].isNullOrEmpty())
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.2.tool_call_id")].isNullOrEmpty())

            assertEquals("tool", toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.role")])
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.content")].isNullOrEmpty())
            assertFalse(toolCallResponseTrace.attributes[AttributeKey.stringKey("gen_ai.prompt.3.tool_call_id")].isNullOrEmpty())
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

        val contentType = trace.attributes[AttributeKey.stringKey("gen_ai.completion.content.type")]
        assertNotNull(contentType, "Missing gen_ai.completion.content.type attribute")
        assertTrue(contentType.startsWith("text/event-stream"))

        assertFalse(trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")].isNullOrEmpty())
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

    protected fun verifyMediaContentUploadAttributes(
        span: SpanData,
        expected: List<MediaContentAttributeValues>
    ) {
        for ((index, values) in expected.withIndex()) {
            val keys = UploadableMediaContentAttributeKeys.forIndex(index)
            val failMessage = "Media content attribute values do not match for index $index"

            when (values) {
                is MediaContentAttributeValues.Data -> {
                    assertEquals(values.type, span.attributes[keys.type], failMessage)
                    assertEquals(values.field, span.attributes[keys.field], failMessage)
                    assertEquals(values.contentType, span.attributes[keys.contentType], failMessage)
                    if (values.data != null) {
                        assertEquals(values.data, span.attributes[keys.data], failMessage)
                    }
                }
                is MediaContentAttributeValues.Url -> {
                    assertEquals(values.type, span.attributes[keys.type], failMessage)
                    assertEquals(values.field, span.attributes[keys.field], failMessage)
                    if (values.url != null) {
                        assertEquals(values.url, span.attributes[keys.url], failMessage)
                    }
                }
            }
        }
    }

    protected fun MediaSource.toMediaContentAttributeValues(
        field: String
    ): MediaContentAttributeValues {
        val media = this
        return when (media) {
            is MediaSource.File -> MediaContentAttributeValues.Data(
                type = SupportedMediaContentTypes.BASE64.type,
                field = field,
                contentType = media.contentType,
                data = loadFileAsBase64Encoded(media.filepath)
            )
            is MediaSource.Link -> MediaContentAttributeValues.Url(
                type = SupportedMediaContentTypes.URL.type,
                field = field,
                url = media.url,
            )
        }
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

        sealed class MediaContentAttributeValues {
            data class Url(
                val type: String,
                val field: String,
                val url: String?
            ) : MediaContentAttributeValues()

            data class Data(
                val type: String,
                val field: String,
                val contentType: String,
                val data: String?,
            ) : MediaContentAttributeValues()
        }
    }

    protected fun readResource(filepath: String): InputStream {
        return javaClass.classLoader.getResourceAsStream(filepath)
            ?: error("File not found")
    }
}