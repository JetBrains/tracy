/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.http.parsers.FormPart
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.asFormData
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.policy.ContentKind
import org.jetbrains.ai.tracy.core.policy.orRedacted
import org.jetbrains.ai.tracy.openai.adapters.handlers.OpenAIApiUtils.setJsonAttribute
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Conservative fallback handler for OpenAI API families whose schemas are mostly resource CRUD/list shapes.
 */
internal open class OpenAIGenericApiEndpointHandler(
    private val apiType: String,
    private val outputType: String? = null,
    private val knownQueryParameters: List<String> = DEFAULT_QUERY_PARAMETERS,
) : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        span.setAttribute("openai.api.type", apiType)
        outputType?.let { span.setAttribute(GEN_AI_OUTPUT_TYPE, it) }

        knownQueryParameters.forEach { name ->
            request.url.parameters.queryParameter(name)?.let { span.setScalarAttribute("tracy.request.$name", it) }
        }

        request.body.asJson()?.jsonObject?.let { body ->
            OpenAIApiUtils.setCommonRequestAttributes(span, request)
            body.entries.forEach { (key, value) ->
                when (key) {
                    "model" -> span.setJsonAttribute("gen_ai.request.model", value)
                    "input" -> span.setJsonAttribute("gen_ai.prompt.0.content", value)
                    "prompt" -> span.setJsonAttribute("gen_ai.prompt.0.content", value)
                    "limit", "order", "after", "purpose", "store", "metadata", "include" ->
                        span.setJsonAttribute("tracy.request.$key", value)
                    else -> span.setJsonAttribute("tracy.request.$key", value)
                }
            }
        }

        request.body.asFormData()?.parts?.forEach { part ->
            val name = part.name ?: return@forEach
            handleFormPart(span, name, part)
        }
    }

    protected open fun handleFormPart(span: Span, name: String, part: FormPart) {
        val content = part.asTextOrNull()
        when (name) {
            "model" -> content?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it) }
            "prompt" -> content?.let { span.setAttribute("gen_ai.prompt.0.content", it.orRedacted(ContentKind.INPUT)) }
            "file", "image" -> {
                span.setAttribute("tracy.request.$name.size_bytes", part.content.size.toLong())
                part.filename?.let { span.setAttribute("tracy.request.$name.filename", it) }
                part.contentType?.let { span.setAttribute("tracy.request.$name.format", it.subtype) }
            }
            else -> content?.let { span.setScalarAttribute("tracy.request.$name", it) }
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        span.setAttribute("openai.api.type", apiType)
        outputType?.let { span.setAttribute(GEN_AI_OUTPUT_TYPE, it) }
        OpenAIApiUtils.setCommonResponseAttributes(span, response)

        val body = response.body.asJson()?.jsonObject ?: return
        body.entries.forEach { (key, value) ->
            when (key) {
                "object" -> span.setJsonAttribute("tracy.response.object", value)
                "status" -> span.setJsonAttribute("tracy.response.status", value)
                "created", "created_at" -> span.setJsonAttribute("tracy.response.created_at", value)
                "deleted" -> span.setJsonAttribute("tracy.response.deleted", value)
                "has_more" -> span.setJsonAttribute("tracy.response.has_more", value)
                "first_id", "last_id" -> span.setJsonAttribute("tracy.response.$key", value)
                "usage" -> traceUsage(span, value as? JsonObject)
                "data" -> traceData(span, value)
                "results" -> traceResults(span, value)
                "error" -> traceResponseError(span, value as? JsonObject)
                else -> span.setJsonAttribute("tracy.response.$key", value)
            }
        }
        if (apiType == "conversations") {
            traceConversationAliases(span, body)
        }
        if (apiType == "files") {
            traceFileAliases(span, body)
        }
        if (apiType == "batches") {
            traceBatchAliases(span, body)
        }
    }

    override fun handleStreaming(span: Span, events: String) {
        val count = events.lineSequence().count { it.startsWith("data:") && it.removePrefix("data:").trim() != "[DONE]" }
        span.setAttribute("tracy.response.stream.events.count", count.toLong())
    }

    private fun traceUsage(span: Span, usage: JsonObject?) {
        usage ?: return
        usage["input_tokens"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it) }
        usage["prompt_tokens"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it) }
        usage["output_tokens"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it) }
        usage["completion_tokens"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it) }
    }

    private fun traceData(span: Span, value: kotlinx.serialization.json.JsonElement) {
        if (value is JsonArray) {
            span.setAttribute("tracy.response.list.count", value.size.toLong())
            value.firstOrNull()?.jsonObject?.let { first ->
                first["id"]?.let { span.setJsonAttribute("tracy.response.data.id", it) }
                first["object"]?.let { span.setJsonAttribute("tracy.response.data.type", it) }
                (first["embedding"] as? JsonArray)?.let { embedding ->
                    span.setAttribute("gen_ai.embeddings.dimension.count", embedding.size.toLong())
                }
            }
        } else {
            span.setJsonAttribute("tracy.response.data", value)
        }
    }

    private fun traceResults(span: Span, value: kotlinx.serialization.json.JsonElement) {
        if (value !is JsonArray) {
            span.setJsonAttribute("tracy.response.results", value)
            return
        }
        span.setAttribute("tracy.response.results.count", value.size.toLong())
        val first = value.firstOrNull()?.jsonObject ?: return
        first["flagged"]?.let { span.setJsonAttribute("tracy.response.results.flagged", it) }
        first["categories"]?.let { span.setJsonAttribute("tracy.response.results.categories", it) }
        first["category_scores"]?.let { span.setJsonAttribute("tracy.response.results.category_scores", it) }
        first["category_applied_input_types"]?.let {
            span.setJsonAttribute("tracy.response.results.category_applied_input_types", it)
        }
    }

    private fun traceResponseError(span: Span, error: JsonObject?) {
        error ?: return
        error["message"]?.let { span.setJsonAttribute("tracy.response.error.message", it) }
        error["type"]?.let { span.setJsonAttribute("tracy.response.error.type", it) }
        error["code"]?.let { span.setJsonAttribute("tracy.response.error.code", it) }
    }

    private fun traceConversationAliases(span: Span, body: JsonObject) {
        body["id"]?.let { span.setJsonAttribute("gen_ai.conversation.id", it) }
        body["created_at"]?.let { span.setJsonAttribute("tracy.conversation.created_at", it) }
        body["deleted"]?.let { span.setJsonAttribute("tracy.conversation.deleted", it) }
        body["first_id"]?.let { span.setJsonAttribute("tracy.conversation.items.first_id", it) }
        body["last_id"]?.let { span.setJsonAttribute("tracy.conversation.items.last_id", it) }
        body["has_more"]?.let { span.setJsonAttribute("tracy.conversation.items.has_more", it) }
        (body["data"] as? JsonArray)?.let { span.setAttribute("tracy.conversation.items.count", it.size.toLong()) }
    }

    private fun traceFileAliases(span: Span, body: JsonObject) {
        body["id"]?.let { span.setJsonAttribute("tracy.response.file.id", it) }
        body["filename"]?.let { span.setJsonAttribute("tracy.response.file.filename", it) }
        body["purpose"]?.let { span.setJsonAttribute("tracy.response.file.purpose", it) }
        body["bytes"]?.let { span.setJsonAttribute("tracy.response.file.size_bytes", it) }
        body["created_at"]?.let { span.setJsonAttribute("tracy.response.file.created_at", it) }
        body["expires_at"]?.let { span.setJsonAttribute("tracy.response.file.expires_at", it) }
        body["status"]?.let { span.setJsonAttribute("tracy.response.file.status", it) }
    }

    private fun traceBatchAliases(span: Span, body: JsonObject) {
        body["id"]?.let { span.setJsonAttribute("tracy.batch.id", it) }
        body["status"]?.let { span.setJsonAttribute("tracy.batch.status", it) }
        body["created_at"]?.let { span.setJsonAttribute("tracy.batch.created_at", it) }
        body["request_counts"]?.jsonObject?.let { counts ->
            counts["total"]?.let { span.setJsonAttribute("tracy.batch.request_counts.total", it) }
            counts["completed"]?.let { span.setJsonAttribute("tracy.batch.request_counts.completed", it) }
            counts["failed"]?.let { span.setJsonAttribute("tracy.batch.request_counts.failed", it) }
        }
    }

    private fun FormPart.asTextOrNull(): String? {
        val contentType = contentType
        val charset = contentType?.charset() ?: if (contentType == null || contentType.type == "text") Charsets.UTF_8 else null
        return charset?.let { content.toString(it) }
    }

    private fun Span.setScalarAttribute(key: String, value: String) {
        when {
            value.toLongOrNull() != null -> setAttribute(key, value.toLong())
            value.toDoubleOrNull() != null -> setAttribute(key, value.toDouble())
            value.toBooleanStrictOrNull() != null -> setAttribute(key, value.toBooleanStrict())
            else -> setAttribute(key, value)
        }
    }

    companion object {
        private val DEFAULT_QUERY_PARAMETERS = listOf("after", "before", "limit", "order", "include")
    }
}
