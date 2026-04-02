/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.policy.ContentCapturePolicy
import org.jetbrains.ai.tracy.openai.adapters.BaseOpenAITracingTest
import org.jetbrains.ai.tracy.openai.adapters.containsToolCall
import org.jetbrains.ai.tracy.openai.adapters.name
import org.jetbrains.ai.tracy.openai.clients.instrument
import org.jetbrains.ai.tracy.test.utils.MediaSource
import org.jetbrains.ai.tracy.test.utils.loadFileAsBase64Encoded
import org.jetbrains.ai.tracy.test.utils.toDataUrl
import org.jetbrains.ai.tracy.test.utils.toMediaContentAttributeValues
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.chat.completions.*
import com.openai.models.embeddings.EmbeddingCreateParams
import com.openai.models.embeddings.EmbeddingModel
import com.openai.models.responses.ResponseCreateParams
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.minutes

@Tag("openai")
class ChatCompletionsOpenAIApiEndpointHandlerTest : BaseOpenAITracingTest() {
    @Test
    fun `test OpenAI chat completions auto tracing`() = runTest {
        val client = createOpenAIClient().apply { instrument(this) }
        val model = ChatModel.GPT_4O_MINI

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(model).temperature(1.1).build()
        client.chat().completions().create(params)

        validateBasicTracing(model)
    }

    @Test
    fun `test nested instrumentation calls don't cause duplicative tracing`() = runTest {
        val client = createOpenAIClient(llmProviderUrl, llmProviderApiKey)
            .apply { instrument(this) }
            .apply { instrument(this) }
            .apply { instrument(this) }

        val model = ChatModel.GPT_4O_MINI

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(model).temperature(1.1).build()
        client.chat().completions().create(params)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        validateBasicTracing(model)
    }

    @Test
    fun `test OpenAI chat completions span error status when request fails`() = runTest {
        val client = createOpenAIClient().apply { instrument(this) }

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI)
            // setting invalid temperature
            .temperature(-1000.0)
            .build()

        try {
            client.chat().completions().create(params)
        } catch (_: Exception) {
            // suppress
        }

        validateErrorStatus()
    }

    @ParameterizedTest
    @MethodSource("provideContentCapturePolicies")
    fun `test capture policy hides sensitive data`(policy: ContentCapturePolicy) = runTest {
        TracingManager.withCapturingPolicy(policy)

        val client = createOpenAIClient().apply { instrument(this) }

        val greetTool = createChatCompletionTool("hi")
        val model = ChatModel.GPT_4O_MINI

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Use a given `hi` tool to greet a person named Alex. You MUST use the given tool!")
            .addTool(greetTool)
            .model(model)
            .temperature(0.0)
            .build()

        client.chat().completions().create(params)

        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        // user prompt
        val prompt = trace.attributes[AttributeKey.stringKey("gen_ai.prompt.0.content")]
        // tool definition
        val name = trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.name")]
        val description = trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.description")]
        val parameters = trace.attributes[AttributeKey.stringKey("gen_ai.tool.0.parameters")]

        if (!policy.captureInputs) {
            assertEquals("REDACTED", prompt, "User prompt should be redacted")
            assertEquals("REDACTED", name, "Tool name should be redacted")
            assertEquals("REDACTED", description, "Tool description should be redacted")
            assertEquals("REDACTED", parameters, "Tool parameters should be redacted")
        } else {
            assertNotEquals("REDACTED", prompt, "User prompt should NOT be redacted")
            assertNotEquals("REDACTED", name, "Tool name should NOT be redacted")
            assertNotEquals("REDACTED", description, "Tool description should NOT be redacted")
            assertNotEquals("REDACTED", parameters, "Tool parameters should NOT be redacted")
        }

        // assume that AI called the given tool
        Assumptions.assumeTrue(
            trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.finish_reason")] == "tool_calls"
        )

        val calledToolName = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.name")]
        val calledToolArgs = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.tool.0.arguments")]

        if (!policy.captureOutputs) {
            assertEquals("REDACTED", calledToolName, "Name of the called tool should be redacted")
            assertEquals("REDACTED", calledToolArgs, "Arguments of the called tool should be redacted")
        } else {
            assertNotEquals("REDACTED", calledToolName, "Name of the called tool should NOT be redacted")
            assertNotEquals("REDACTED", calledToolArgs, "Arguments of the called tool should NOT be redacted")
        }
    }

    @Test
    fun `test OpenAI chat completions tool calls auto tracing`() = runTest {
        val client = createOpenAIClient().apply { instrument(this) }

        val toolName = "hi"
        val greetTool = createChatCompletionTool(toolName)

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Call the `hi` tool with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool call.")
            .addTool(greetTool)
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)
            .build()

        val response = client.chat().completions().create(params)

        flushTracesAndAssumeToolCalled(response, toolName, ChatCompletion::containsToolCall)

        validateToolCall()
    }

    @Test
    fun `test OpenAI chat completions response to a tool call auto tracing`() = runTest {
        val client = createOpenAIClient().apply { instrument(this) }

        val toolName = "hi"
        val greetTool = createChatCompletionTool(toolName)

        // See example at:
        // https://github.com/openai/openai-java/blob/main/openai-java-example/src/main/java/com/openai/example/FunctionCallingRawExample.java
        val paramsBuilder = ChatCompletionCreateParams.builder()
            .addUserMessage("Call the `hi` tool with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool call.")
            .addTool(greetTool)
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)

        // expect AI to request a tool call
        val response = client.chat().completions().create(paramsBuilder.build())

        flushTracesAndAssumeToolCalled(response, toolName, ChatCompletion::containsToolCall)

        response.choices().stream()
            .map(ChatCompletion.Choice::message)
            .peek(paramsBuilder::addMessage)
            .flatMap { message -> message.toolCalls().stream().flatMap { it.stream() } }
            .forEach { toolCall ->
                // add an answer to a tool call
                paramsBuilder.addMessage(
                    ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolCall.id)
                        .content("Hello! I'm greeting you!")
                        .build()
                )
            }

        // give an answer to a tool call
        client.chat().completions().create(paramsBuilder.build())

        validateToolCallResponse()
    }

    @Test
    fun `test OpenAI chat completions multiple tools response to tool calls auto tracing`() = runTest {
        val client = createOpenAIClient().apply { instrument(this) }

        val greetToolName = "hi"
        val greetTool = createChatCompletionTool(greetToolName)
        val goodbyeToolName = "goodbye"
        val goodbyeTool = createChatCompletionTool(goodbyeToolName)

        val paramsBuilder = ChatCompletionCreateParams.builder()
            .addUserMessage("Call the `hi` tool with the argument `name` set to 'USER' and `goodbye` with the argument `name` set to 'USER'. Do not output any conversational text; only execute the tool calls.")
            .addTool(greetTool)
            .addTool(goodbyeTool)
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.0)

        val response = client.chat().completions().create(paramsBuilder.build())

        flushTracesAndAssumeToolCalled(response, greetToolName, ChatCompletion::containsToolCall)
        flushTracesAndAssumeToolCalled(response, goodbyeToolName, ChatCompletion::containsToolCall)

        response.choices().stream()
            .map(ChatCompletion.Choice::message)
            .peek(paramsBuilder::addMessage)
            .flatMap { msg -> msg.toolCalls().stream().flatMap { it.stream() } }
            .forEach { toolCall ->
                paramsBuilder.addMessage(
                    ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolCall.id)
                        .content(toolCall.name)
                        .build()
                )
            }

        client.chat().completions().create(paramsBuilder.build())

        validateMultipleToolCallResponseWithInput()
    }

    @Test
    fun `test OpenAI auto tracing when instrumentation is off`() = runTest {
        val client = createOpenAIClient()

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI).temperature(1.1).build()
        val result = client.chat().completions().create(params)

        val traces = analyzeSpans()
        assertTracesCount(0, traces)

        assertTrue(result.model().startsWith(ChatModel.GPT_4O_MINI.asString()))
        val content = result.choices().first().message().content().getOrNull()
        assertNotNull(content)
        assertTrue(content!!.isNotEmpty())
    }

    @Test
    fun `test OpenAI chat completions streaming`(): Unit = runTest {
        val client = createOpenAIClient().apply { instrument(this) }

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(ChatModel.GPT_4O_MINI)
            .temperature(0.7)
            .build()

        val sb = StringBuilder()
        client.chat().completions().createStreaming(params).use { stream ->
            stream.stream().forEach { chunk ->
                chunk.choices().forEach { choice ->
                    val delta = choice.delta()
                    delta.content().ifPresent { parts ->
                        parts.forEach { part -> sb.append(part.toString()) }
                    }
                }
            }
        }

        validateStreaming(sb.toString())
    }

    @Test
    fun `test OpenAI embeddings`() = runTest {
        // handler defaults to chat/completions, but the specific embedding parameters are still propagated to the span
        val client = createOpenAIClient(llmProviderUrl, llmProviderApiKey).apply { instrument(this) }

        val params = EmbeddingCreateParams.builder()
            .model(EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
            .input("The quick brown fox jumps over the lazy dog.")
            .input("Sphinx of black quartz, judge my vow.")
            .build()

        client.embeddings().create(params)
        val traces = analyzeSpans()
        assertTracesCount(1, traces)
        val trace = traces.first()

        val responseData = trace.attributes?.get(AttributeKey.stringKey("tracy.response.data"))
        assertFalse(responseData.isNullOrEmpty())

        val responseObject = trace.attributes?.get(AttributeKey.stringKey("tracy.response.object"))
        assertFalse(responseObject.isNullOrEmpty())

        val requestEncodingFormat = trace.attributes?.get(AttributeKey.stringKey("tracy.request.encoding_format"))
        assertFalse(requestEncodingFormat.isNullOrEmpty())
    }

    @ParameterizedTest
    @MethodSource("provideImagesForUpload")
    fun `test image is extracted and uploaded on Langfuse`(image: MediaSource) = runTest(timeout = 3.minutes) {
        val model = ChatModel.GPT_4O
        val prompt = "Please describe what you see in this image."

        val client = createOpenAIClient(timeout = Duration.ofMinutes(3)).apply { instrument(this) }

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(
                listOf(
                    partImage(image),
                    partText(prompt),
                )
            )
            .build()

        // send request
        client.chat().completions().create(params)

        // expect the content of a request to be captures successfully
        validateBasicTracing(model)
        val trace = analyzeSpans().first()
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                image.toMediaContentAttributeValues(field = "input")
            )
        )
    }

    @Test
    fun `test audio file is extracted and uploaded on Langfuse`() = runTest(timeout = 3.minutes) {
        val model = ChatModel.GPT_4O_AUDIO_PREVIEW
        val prompt = "Tell me what is in the audio file"
        val filepath = "lofi.wav"

        val client = createOpenAIClient(timeout = Duration.ofMinutes(3)).apply { instrument(this) }

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(
                listOf(
                    partAudio(filepath),
                    partText(prompt),
                )
            )
            .build()

        client.chat().completions().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()

        val expectedMedia = MediaSource.File(filepath, "audio/wav")
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                expectedMedia.toMediaContentAttributeValues(field = "input")
            )
        )
    }

    @Test
    fun `test PDF file is extracted and uploaded on Langfuse`() = runTest(timeout = 3.minutes) {
        val model = ChatModel.GPT_4O
        val prompt = "Please describe what you see in the PDF file."
        val media = MediaSource.File(
            filepath = "sample.pdf",
            contentType = "application/pdf",
        )

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(
                listOf(
                    partFile(media),
                    partText(prompt),
                )
            )
            .build()

        client.chat().completions().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                media.toMediaContentAttributeValues(field = "input")
            )
        )
    }

    @Test
    fun `test two images sent simultaneously are both uploaded on Langfuse`() = runTest(timeout = 3.minutes) {
        val model = ChatModel.GPT_4O
        val prompt = "Please describe what you see in both images."

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val images = listOf(
            MediaSource.File(filepath = "image.jpg", contentType = "image/jpeg"),
            MediaSource.Link(CAT_IMAGE_URL),
        )

        // insert both images and the prompt
        val parts: List<ChatCompletionContentPart> = images.map { partImage(it) } + partText(prompt)

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(parts)
            .build()

        // send request
        client.chat().completions().create(params)

        val trace = analyzeSpans().first()
        verifyMediaContentUploadAttributes(trace, expected = images.map {
            it.toMediaContentAttributeValues(field = "input")
        })
    }

    @Test
    fun `test OpenAI chat completions auto tracing disabled`() = runTest {
        TracingManager.isTracingEnabled = false

        val model = ChatModel.GPT_4O_MINI
        val client = createOpenAIClient().apply { instrument(this) }

        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Generate polite greeting and introduce yourself")
            .model(model).temperature(1.1).build()

        client.chat().completions().create(params)

        val traces = analyzeSpans()
        assert(traces.isEmpty())
    }

    @Test
    fun `test several media types sent simultaneously are uploaded on Langfuse`() = runTest(timeout = 3.minutes) {
        val model = ChatModel.GPT_4O
        val prompt = "Please describe every media item attached"

        val client = createOpenAIClient(
            url = patchedProviderUrl,
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        val image = MediaSource.File("image.jpg", "image/jpeg")
        val file = MediaSource.File("sample.pdf", "application/pdf")

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addUserMessageOfArrayOfContentParts(
                listOf(
                    partImage(image),
                    partFile(file),
                    partText(prompt),
                )
            )
            .build()

        // send request
        client.chat().completions().create(params)

        validateBasicTracing(model)
        val trace = analyzeSpans().first()
        verifyMediaContentUploadAttributes(
            trace, expected = listOf(
                image.toMediaContentAttributeValues(field = "input"),
                file.toMediaContentAttributeValues(field = "input"),
            )
        )
    }

    @Test
    fun `test single instrumented client is used for multiple endpoints`() = runTest(timeout = 3.minutes) {
        val client = createOpenAIClient(
            timeout = Duration.ofMinutes(3)
        ).apply { instrument(this) }

        // I. chat completions
        val model1 = ChatModel.GPT_4O
        client.chat().completions().create(
            ChatCompletionCreateParams.builder()
                .model(model1)
                .addUserMessage("Tell me about yourself")
                .build()
        )
        validateBasicTracing(model1)
        resetExporter()

        // II. responses
        val model2 = ChatModel.GPT_4O_MINI
        client.responses().create(
            ResponseCreateParams.builder()
                .input("Tell me about yourself")
                .model(model2)
                .build()
        )
        validateBasicTracing(model2)
        resetExporter()

        // III. chat completions
        val model3 = ChatModel.GPT_4
        client.chat().completions().create(
            ChatCompletionCreateParams.builder()
                .model(model3)
                .addUserMessage("Tell me about yourself")
                .build()
        )
        validateBasicTracing(model3)
        resetExporter()

        // IV. responses
        val model4 = ChatModel.GPT_3_5_TURBO
        client.responses().create(
            ResponseCreateParams.builder()
                .input("Tell me about yourself")
                .model(model4)
                .build()
        )
        validateBasicTracing(model4)
    }

    private fun partText(prompt: String) = ChatCompletionContentPart.ofText(
        ChatCompletionContentPartText.builder()
            .text(prompt)
            .build()
    )

    private fun partImage(media: MediaSource): ChatCompletionContentPart {
        val url = when (media) {
            is MediaSource.File -> media.toDataUrl()
            is MediaSource.Link -> media.url
        }
        return ChatCompletionContentPart.ofImageUrl(
            ChatCompletionContentPartImage.builder()
                .imageUrl(
                    ChatCompletionContentPartImage.ImageUrl.builder()
                        .url(url)
                        .build()
                )
                .build()
        )
    }

    private fun partFile(media: MediaSource.File) = ChatCompletionContentPart.ofFile(
        ChatCompletionContentPart.File.builder()
            .file(
                ChatCompletionContentPart.File.FileObject.builder()
                    .filename(media.filepath)
                    .fileData(media.toDataUrl())
                    .build()
            )
            .build()
    )

    private fun partAudio(filepath: String): ChatCompletionContentPart {
        val audioData = loadFileAsBase64Encoded(filepath)
        val format = when (val ext = filepath.substringAfterLast(".")) {
            "wav" -> ChatCompletionContentPartInputAudio.InputAudio.Format.WAV
            "mp3" -> ChatCompletionContentPartInputAudio.InputAudio.Format.MP3
            else -> error("Unsupported file format $ext at $filepath")
        }

        return ChatCompletionContentPart.ofInputAudio(
            ChatCompletionContentPartInputAudio.builder()
                .inputAudio(
                    ChatCompletionContentPartInputAudio.InputAudio.builder()
                        .format(format)
                        .data(audioData)
                        .build()
                )
                .build(),
        )
    }
}
