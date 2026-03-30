/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.model

import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractor
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.contentTracingAllowed
import org.jetbrains.ai.tracy.core.policy.orRedacted
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import org.jetbrains.ai.tracy.core.policy.orRedactedOutput
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*

/**
 * Serializes unified [TracedRequest] and [TracedResponse] models into flat OTel span attributes.
 *
 * This is **Step 3** of the tracing pipeline, written once and shared by all providers.
 * Handles:
 * - Generation config attributes (model, temperature, etc.)
 * - System prompt (text or structured blocks)
 * - Indexed messages with redaction by [ContentKind]
 * - Indexed tool definitions
 * - Indexed completions with tool calls
 * - Usage statistics
 * - Media content extraction
 * - Provider-specific extra attributes
 */
object SpanSerializer {

    /**
     * Writes all request attributes to the span.
     */
    fun writeRequest(span: Span, request: TracedRequest, extractor: MediaContentExtractor) {
        // Generation config
        request.model?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it) }
        request.temperature?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it) }
        request.topP?.let { span.setAttribute(GEN_AI_REQUEST_TOP_P, it) }
        request.topK?.let { span.setAttribute(GEN_AI_REQUEST_TOP_K, it) }
        request.maxTokens?.let { span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, it) }
        request.candidateCount?.let { span.setAttribute(GEN_AI_REQUEST_CHOICE_COUNT, it) }
        request.operationName?.let { span.setAttribute(GEN_AI_OPERATION_NAME, it) }

        // System prompt
        writeSystemPrompt(span, request.systemPrompt)

        // Messages
        request.messages.forEachIndexed { index, msg ->
            msg.role?.let { span.setAttribute("gen_ai.prompt.$index.role", it) }
            msg.content?.let { span.setAttribute("gen_ai.prompt.$index.content", it.orRedacted(msg.contentKind)) }
            msg.toolCallId?.let { span.setAttribute("gen_ai.prompt.$index.tool_call_id", it) }
            msg.extraAttributes.forEach { (key, value) ->
                span.setAttribute("gen_ai.prompt.$index.$key", value)
            }
        }

        // Tools
        request.tools.forEachIndexed { index, tool ->
            tool.type?.let { span.setAttribute("gen_ai.tool.$index.type", it) }
            tool.name?.let { span.setAttribute("gen_ai.tool.$index.name", it.orRedactedInput()) }
            tool.description?.let { span.setAttribute("gen_ai.tool.$index.description", it.orRedactedInput()) }
            tool.parameters?.let { span.setAttribute("gen_ai.tool.$index.parameters", it.orRedactedInput()) }
            tool.strict?.let { span.setAttribute("gen_ai.tool.$index.strict", it) }
        }

        // Media content
        if (contentTracingAllowed(ContentKind.INPUT)) {
            request.mediaContent?.let { extractor.setUploadableContentAttributes(span, "input", it) }
        }

        // Provider-specific extras
        writeExtraAttributes(span, request.extraAttributes)

        span.setAttribute("gen_ai.prompt", buildCompositeInputJson(request))
    }

    /**
     * Writes all response attributes to the span.
     */
    fun writeResponse(span: Span, response: TracedResponse, extractor: MediaContentExtractor) {
        response.id?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it) }
        response.model?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it) }
        response.operationName?.let { span.setAttribute(GEN_AI_OPERATION_NAME, it) }

        if (response.finishReasons.isNotEmpty()) {
            span.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS, response.finishReasons)
        }

        // Completions
        response.completions.forEachIndexed { index, completion ->
            completion.role?.let { span.setAttribute("gen_ai.completion.$index.role", it) }
            completion.content?.let { span.setAttribute("gen_ai.completion.$index.content", it.orRedactedOutput()) }
            completion.finishReason?.let { span.setAttribute("gen_ai.completion.$index.finish_reason", it) }
            completion.type?.let { span.setAttribute("gen_ai.completion.$index.type", it) }
            completion.annotations?.let { span.setAttribute("gen_ai.completion.$index.annotations", it) }

            // Tool calls
            completion.toolCalls.forEachIndexed { toolIdx, tc ->
                tc.id?.let { span.setAttribute("gen_ai.completion.$index.tool.$toolIdx.call.id", it) }
                tc.type?.let { span.setAttribute("gen_ai.completion.$index.tool.$toolIdx.call.type", it) }
                tc.name?.let {
                    span.setAttribute("gen_ai.completion.$index.tool.$toolIdx.name", it.orRedactedOutput())
                }
                tc.arguments?.let {
                    span.setAttribute("gen_ai.completion.$index.tool.$toolIdx.arguments", it.orRedactedOutput())
                }
            }

            completion.extraAttributes.forEach { (key, value) ->
                span.setAttribute("gen_ai.completion.$index.$key", value)
            }
        }

        // Usage
        response.usage?.let { usage ->
            usage.inputTokens?.let { span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it) }
            usage.outputTokens?.let { span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it) }
            writeExtraAttributes(span, usage.extraAttributes)
        }

        // Media content
        if (contentTracingAllowed(ContentKind.OUTPUT)) {
            response.mediaContent?.let { extractor.setUploadableContentAttributes(span, "output", it) }
        }

        // Provider-specific extras
        writeExtraAttributes(span, response.extraAttributes)

        span.setAttribute("gen_ai.completion", buildCompositeOutputJson(response))
    }

    private fun buildCompositeInputJson(request: TracedRequest): String {
        return buildJsonObject {
            // Messages (system prompt + conversation messages)
            putJsonArray("messages") {
                // System prompt as the first message
                when (val sys = request.systemPrompt) {
                    is TracedSystemPrompt.Text -> addJsonObject {
                        put("role", "system")
                        put("content", sys.text.orRedactedInput())
                    }
                    is TracedSystemPrompt.Blocks -> addJsonObject {
                        put("role", "system")
                        putJsonArray("content") {
                            for (block in sys.blocks) {
                                addJsonObject {
                                    block.type?.let { put("type", it) }
                                    block.content?.let { put("content", it.orRedactedInput()) }
                                }
                            }
                        }
                    }
                    null -> {}
                }
                // Conversation messages
                for (msg in request.messages) {
                    addJsonObject {
                        msg.role?.let { put("role", it) }
                        msg.content?.let { put("content", it.orRedacted(msg.contentKind)) }
                        msg.toolCallId?.let { put("tool_call_id", it) }
                        msg.extraAttributes.forEach { (key, value) -> put(key, value) }
                    }
                }
            }
            // Tools (only if present)
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    for (tool in request.tools) {
                        addJsonObject {
                            tool.type?.let { put("type", it) }
                            putJsonObject("function") {
                                putFunctionFields(tool.name, tool.description, tool.parameters)
                                tool.strict?.let { put("strict", it) }
                            }
                        }
                    }
                }
            }
        }.toString()
    }

    private fun buildCompositeOutputJson(response: TracedResponse): String {
        return buildJsonArray {
            for (completion in response.completions) {
                addJsonObject {
                    completion.role?.let { put("role", it) }
                    completion.content?.let { put("content", it.orRedactedOutput()) }
                    completion.finishReason?.let { put("finish_reason", it) }
                    completion.type?.let { put("type", it) }
                    if (completion.toolCalls.isNotEmpty()) {
                        putJsonArray("tool_calls") {
                            for (tc in completion.toolCalls) {
                                addJsonObject {
                                    tc.id?.let { put("id", it) }
                                    tc.type?.let { put("type", it) }
                                    putJsonObject("function") {
                                        tc.name?.let { put("name", it.orRedactedOutput()) }
                                        tc.arguments?.let {
                                            val redacted = it.orRedactedOutput()
                                            put("arguments", runCatching {
                                                Json.parseToJsonElement(redacted)
                                            }.getOrElse { JsonPrimitive(redacted) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.toString()
    }

    private fun JsonObjectBuilder.putFunctionFields(name: String?, description: String?, parameters: String?) {
        name?.let { put("name", it.orRedactedInput()) }
        description?.let { put("description", it.orRedactedInput()) }
        parameters?.let {
            val redacted = it.orRedactedInput()
            put("parameters", runCatching {
                Json.parseToJsonElement(redacted)
            }.getOrElse { JsonPrimitive(redacted) })
        }
    }

    private fun writeSystemPrompt(span: Span, systemPrompt: TracedSystemPrompt?) {
        when (systemPrompt) {
            is TracedSystemPrompt.Text -> {
                span.setAttribute("gen_ai.prompt.system.content", systemPrompt.text.orRedactedInput())
            }

            is TracedSystemPrompt.Blocks -> {
                systemPrompt.blocks.forEachIndexed { i, block ->
                    block.type?.let { span.setAttribute("gen_ai.prompt.system.$i.type", it) }
                    block.content?.let { span.setAttribute("gen_ai.prompt.system.$i.content", it.orRedactedInput()) }
                }
            }

            null -> {}
        }
    }

    private fun writeExtraAttributes(span: Span, extras: Map<String, Any?>) {
        extras.forEach { (key, value) ->
            when (value) {
                is String -> span.setAttribute(key, value)
                is Long -> span.setAttribute(key, value)
                is Int -> span.setAttribute(key, value.toLong())
                is Double -> span.setAttribute(key, value)
                is Boolean -> span.setAttribute(key, value)
                else -> value?.let { span.setAttribute(key, it.toString()) }
            }
        }
    }
}
