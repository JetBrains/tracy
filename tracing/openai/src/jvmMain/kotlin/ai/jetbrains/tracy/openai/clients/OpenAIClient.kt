/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.openai.clients

import ai.jetbrains.tracy.core.OpenTelemetryOkHttpInterceptor
import ai.jetbrains.tracy.core.patchOpenAICompatibleClient
import ai.jetbrains.tracy.openai.adapters.OpenAILLMTracingAdapter
import com.openai.client.OpenAIClient

/**
 * Instruments an OpenAI client with OpenTelemetry tracing capabilities **inplace**.
 *
 * This function enables automatic tracing for all OpenAI API calls made through the provided client,
 * including chat completions, responses API, tool calling, streaming, and multimodal operations.
 * Trace data is captured according to OpenTelemetry semantic conventions and can be exported to
 * configured backends (e.g., Langfuse, Jaeger, console).
 *
 * ## Use Cases
 *
 * ### Basic Chat Completions
 * ```kotlin
 * val client = instrument(createOpenAIClient())
 * val params = ResponseCreateParams.builder()
 *     .input("Generate polite greeting and introduce yourself")
 *     .model(ChatModel.GPT_4O_MINI)
 *     .temperature(1.1)
 *     .build()
 * client.responses().create(params)
 * ```
 *
 * ### Tool Calling
 * ```kotlin
 * val client = instrument(createOpenAIClient())
 * val greetTool = createFunctionTool("hi")
 * val params = ResponseCreateParams.builder()
 *     .input("Call the `hi` tool with the argument `name` set to 'USER'")
 *     .addTool(greetTool)
 *     .model(ChatModel.GPT_4O_MINI)
 *     .temperature(0.0)
 *     .build()
 * val response = client.responses().create(params)
 * // Tool calls are automatically traced
 * ```
 *
 * ### Streaming Responses
 * ```kotlin
 * val client = instrument(createOpenAIClient())
 * val params = ResponseCreateParams.builder()
 *     .input("Generate polite greeting and introduce yourself")
 *     .model(ChatModel.GPT_4O_MINI)
 *     .temperature(0.0)
 *
 * client.responses().createStreaming(params.build()).use { stream ->
 *     stream.stream().forEach { event ->
 *         event.outputTextDelta().ifPresent { delta ->
 *             print(delta.delta())
 *         }
 *     }
 * }
 * // Streaming data is automatically captured and traced
 * ```
 *
 * ### Multimodal Input with Images
 * ```kotlin
 * val client = instrument(createOpenAIClient())
 * val params = ResponseCreateParams.builder()
 *     .input(inputWith(
 *         inputImage(MediaSource.File("image.jpg", "image/jpeg")),
 *         inputText("Describe what you see in the image.")
 *     ))
 *     .model(ChatModel.GPT_4O_MINI)
 *     .temperature(0.0)
 *     .build()
 * client.responses().create(params)
 * // Images and prompts are traced with media content upload attributes
 * ```
 *
 * ## Notes
 * - This function is **idempotent**: calling `instrument()` multiple times on the same client
 *   will not result in duplicate tracing.
 * - Tracing can be controlled globally via `TracingManager.isTracingEnabled`.
 * - Content capture policies can be configured via `TracingManager.withCapturingPolicy(policy)`
 *   to redact sensitive input/output data.
 *
 * @param client The OpenAI client to instrument
 *
 * @see OpenAILLMTracingAdapter
 */
fun instrument(client: OpenAIClient) {
    patchOpenAICompatibleClient(
        client = client,
        interceptor = OpenTelemetryOkHttpInterceptor(adapter = OpenAILLMTracingAdapter())
    )
}
