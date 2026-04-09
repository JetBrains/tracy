/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.clients

import org.jetbrains.ai.tracy.anthropic.adapters.AnthropicLLMTracingAdapter
import org.jetbrains.ai.tracy.core.interceptors.OpenTelemetryOkHttpInterceptor
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.interceptors.patchOpenAICompatibleClient
import com.anthropic.client.AnthropicClient

/**
 * Instruments an Anthropic Claude client with OpenTelemetry tracing capabilities **inplace**.
 *
 * This function enables automatic tracing for all Anthropic API calls made through the provided client,
 * including message creation, streaming, tool calling, and multi-turn conversations. Trace data is
 * captured according to OpenTelemetry semantic conventions and can be exported to configured backends
 * (e.g., Langfuse, Jaeger, console).
 *
 * ## Use Cases
 *
 * ### Basic Message Creation
 * ```kotlin
 * val client = instrument(createAnthropicClient())
 * val params = MessageCreateParams.builder()
 *     .addUserMessage("Generate polite greeting and introduce yourself")
 *     .maxTokens(1000L)
 *     .temperature(0.8)
 *     .model(Model.CLAUDE_3_5_HAIKU_LATEST)
 *     .build()
 * client.messages().create(params)
 * // Request and response are automatically traced
 * ```
 *
 * ### Tool Calling
 * ```kotlin
 * val client = instrument(createAnthropicClient())
 * val toolName = "hi"
 * val greetTool = createTool(toolName)
 *
 * val params = MessageCreateParams.builder()
 *     .addUserMessage("Call the `hi` tool with the argument `name` set to 'USER'")
 *     .addTool(greetTool)
 *     .maxTokens(1000L)
 *     .temperature(0.0)
 *     .model(Model.CLAUDE_3_5_HAIKU_LATEST)
 *     .build()
 *
 * val response = client.messages().create(params)
 * // Tool definitions and tool call requests are automatically traced
 * ```
 *
 * ### Responding to Tool Calls
 * ```kotlin
 * val client = instrument(createAnthropicClient())
 * val paramsBuilder = MessageCreateParams.builder()
 *     .addUserMessage("Call the `hi` tool with the argument `name` set to 'USER'")
 *     .addTool(greetTool)
 *     .maxTokens(1000L)
 *     .model(Model.CLAUDE_3_5_HAIKU_LATEST)
 *
 * // First request - AI requests tool execution
 * val assistantMessage = client.messages().create(paramsBuilder.build())
 * paramsBuilder.addMessage(assistantMessage)
 *
 * // Add tool results
 * assistantMessage.content().forEach { block ->
 *     if (block.isToolUse()) {
 *         val toolUse = block.toolUse().get()
 *         paramsBuilder.addMessage(
 *             MessageParam.builder()
 *                 .contentOfBlockParams(listOf(
 *                     ContentBlockParam.ofToolResult(
 *                         ToolResultBlockParam.builder()
 *                             .toolUseId(toolUse.id())
 *                             .content("Hello, my dear friend!")
 *                             .build()
 *                     )
 *                 ))
 *                 .role(MessageParam.Role.USER)
 *                 .build()
 *         )
 *     }
 * }
 *
 * // Second request - AI processes tool results
 * client.messages().create(paramsBuilder.build())
 * // Tool results are traced in the conversation history
 * ```
 *
 * ### Streaming Responses
 * ```kotlin
 * val client = instrument(createAnthropicClient())
 * val params = MessageCreateParams.builder()
 *     .addUserMessage("Call the `hi` tool with the argument `name` set to 'USER'")
 *     .addTool(greetTool)
 *     .maxTokens(1000L)
 *     .model(Model.CLAUDE_3_5_HAIKU_LATEST)
 *     .build()
 *
 * val messageAccumulator = MessageAccumulator.create()
 * client.messages().createStreaming(params).use {
 *     it.stream().forEach(messageAccumulator::accumulate)
 * }
 * val message = messageAccumulator.message()
 * // Streaming events are automatically captured and traced
 * ```
 *
 * ## Notes
 * - This function is **idempotent**: calling `instrument()` multiple times on the same client
 *   will not result in duplicate tracing.
 * - Tracing can be controlled globally via `TracingManager.isTracingEnabled`.
 * - Content capture policies can be configured via `TracingManager.withCapturingPolicy(policy)`
 *   to redact sensitive input/output data.
 * - Error responses (e.g., 529 overload errors) are automatically captured with error status and messages.
 * - Multi-turn conversations with tool results are fully traced, showing the complete dialogue history.
 *
 * @param client The Anthropic client to instrument
 *
 * @see AnthropicLLMTracingAdapter
 * @see TracingManager
 * @see TracingManager.traceSensitiveContent
 */
fun instrument(client: AnthropicClient) {
    patchOpenAICompatibleClient(
        client = client,
        interceptor = OpenTelemetryOkHttpInterceptor(adapter = AnthropicLLMTracingAdapter())
    )
}
