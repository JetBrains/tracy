/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.http.protocol.asFormData
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import kotlinx.serialization.json.*

/**
 * Common utilities for OpenAI API handling
 */
internal object OpenAIApiUtils {

    /**
     * Sets common request attributes (temperature, model)
     */
    fun setCommonRequestAttributes(span: Span, request: TracyHttpRequest) {
        setEndpointAttributes(span, request.url, request.method)

        request.body.asFormData()?.parts?.forEach { part ->
            val value = part.content.toString(part.contentType?.charset() ?: Charsets.UTF_8)
            when (part.name) {
                "model" -> span.setAttribute(GEN_AI_REQUEST_MODEL, value)
                "response_format" -> span.setAttribute("tracy.request.response_format", value)
                "voice" -> span.setAttribute("tracy.request.voice", value)
                "speed" -> span.setAttribute("tracy.request.speed", value)
                "language" -> span.setAttribute("tracy.request.language", value)
                "temperature" -> span.setAttribute("tracy.request.temperature", value)
                "stream" -> span.setAttribute("gen_ai.request.stream", value.toBooleanStrictOrNull() ?: false)
                "stream_format" -> span.setAttribute("tracy.request.stream_format", value)
                "include[]" -> span.setAttribute("tracy.request.include", value)
                "size", "n", "quality", "output_format", "partial_images", "background", "seconds" -> {
                    span.setAttribute("tracy.request.${part.name}", value)
                    span.setAttribute("gen_ai.request.${part.name}", value)
                }
                "file" -> {
                    span.setAttribute("tracy.request.audio.size_bytes", part.content.size.toLong())
                    part.contentType?.let { span.setAttribute("tracy.request.audio.format", it.asString()) }
                }
            }
        }

        val body = request.body.asJson()?.jsonObject ?: return

        body["temperature"]?.let { span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, it.jsonPrimitive.doubleOrNull) }
        body["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it.jsonPrimitive.content) }
        body["max_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, it) }
        body["max_completion_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, it) }
        body["stream"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("gen_ai.request.stream", it) }
        body["store"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.request.store", it) }
        body["metadata"]?.jsonObject?.let { span.setAttribute("tracy.request.metadata.count", it.size.toLong()) }
        body["limit"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.request.limit", it) }
        body["order"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.request.order", it) }
        body["purpose"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.request.purpose", it) }
        listOf("size", "n", "quality", "output_format", "partial_images", "background", "seconds").forEach { key ->
            body[key]?.jsonPrimitive?.contentOrNull?.let {
                span.setAttribute("tracy.request.$key", it)
                span.setAttribute("gen_ai.request.$key", it)
            }
        }
        body["encoding_format"]?.jsonPrimitive?.contentOrNull?.let {
            span.setAttribute("gen_ai.request.encoding_formats", it)
        }
        body["dimensions"]?.jsonPrimitive?.longOrNull?.let {
            span.setAttribute("gen_ai.request.embedding.dimensions", it)
        }
        body["response_format"]?.jsonPrimitive?.contentOrNull?.let {
            span.setAttribute("tracy.request.response_format", it)
        }
    }

    /**
     * Sets common response attributes (id, model, object type)
     */
    fun setCommonResponseAttributes(span: Span, response: TracyHttpResponse) {
        setEndpointAttributes(span, response.url, response.requestMethod)

        val body = response.body.asJson()?.jsonObject ?: return

        body["id"]?.let { span.setAttribute(GEN_AI_RESPONSE_ID, it.jsonPrimitive.content) }
        body["object"]?.let { span.setAttribute(GEN_AI_OPERATION_NAME, it.jsonPrimitive.content) }
        body["model"]?.let { span.setAttribute(GEN_AI_RESPONSE_MODEL, it.jsonPrimitive.content) }
        body["service_tier"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("openai.response.service_tier", it) }
        body["system_fingerprint"]?.jsonPrimitive?.contentOrNull?.let {
            span.setAttribute("openai.response.system_fingerprint", it)
        }
        body["object"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.object", it) }
        body["status"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.status", it) }
        body["store"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.response.store", it) }
        body["has_more"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.response.has_more", it) }
        body["deleted"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.response.deleted", it) }
        body["error"]?.jsonObject?.let { error ->
            error["message"]?.jsonPrimitive?.contentOrNull?.let {
                span.setAttribute("tracy.response.error.message", it)
            }
            error["type"]?.jsonPrimitive?.contentOrNull?.let {
                span.setAttribute("tracy.response.error.type", it)
                span.setAttribute("error.type", it)
            }
            error["code"]?.jsonPrimitive?.contentOrNull?.let {
                span.setAttribute("tracy.response.error.code", it)
            }
        }
        body["usage"]?.jsonObject?.let { usage ->
            usage["input_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it) }
            usage["output_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it) }
            usage["prompt_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, it) }
            usage["completion_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, it) }
            usage["total_tokens"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("gen_ai.usage.total_tokens", it) }
        }
        body["choices"]?.jsonArray?.mapNotNull {
            it.jsonObject["finish_reason"]?.jsonPrimitive?.contentOrNull
        }?.takeIf { it.isNotEmpty() }?.let {
            span.setAttribute("gen_ai.response.finish_reasons", it.joinToString(","))
        }
        setResourceAliases(span, response.url, body)
    }

    fun setEndpointAttributes(span: Span, url: TracyHttpUrl, method: String) {
        span.setAttribute("openai.api.type", apiType(url))
        span.setAttribute(GEN_AI_OPERATION_NAME, operationName(url, method))
        outputType(url)?.let { span.setAttribute(GEN_AI_OUTPUT_TYPE, it) }
        url.parameters.queryParameter("limit")?.let {
            span.setAttribute("tracy.request.limit", it)
            span.setAttribute("gen_ai.request.limit", it)
        }
        url.parameters.queryParameter("order")?.let {
            span.setAttribute("tracy.request.order", it)
            span.setAttribute("gen_ai.request.order", it)
        }
        url.parameters.queryParameter("after")?.let {
            span.setAttribute("tracy.request.after", it)
            span.setAttribute("gen_ai.request.after", it)
        }
    }

    private fun apiType(url: TracyHttpUrl): String = when {
        url.hasSegment("chat") -> "chat_completions"
        url.hasSegment("responses") -> "responses"
        url.hasSegment("audio") -> "audio"
        url.hasSegment("images") -> "images"
        url.hasSegment("embeddings") -> "embeddings"
        url.hasSegment("files") -> "files"
        url.hasSegment("batches") -> "batches"
        url.hasSegment("models") -> "models"
        url.hasSegment("moderations") -> "moderations"
        url.hasSegment("conversations") -> "conversations"
        url.hasSegment("videos") -> "videos"
        else -> "openai"
    }

    private fun operationName(url: TracyHttpUrl, method: String): String {
        val segments = url.pathSegments.filter { it.isNotBlank() && it != "v1" }
        fun indexOf(segment: String) = segments.indexOf(segment)
        return when {
            segments.takeLast(2) == listOf("audio", "speech") -> "audio.speech"
            segments.takeLast(2) == listOf("audio", "transcriptions") -> "audio.transcription"
            segments.takeLast(2) == listOf("audio", "translations") -> "audio.translation"
            segments.contains("embeddings") -> "embeddings"
            segments.contains("moderations") -> "moderations"
            segments.takeLast(2) == listOf("images", "generations") -> "generate_content"
            segments.takeLast(2) == listOf("images", "edits") -> "generate_content"
            segments.takeLast(2) == listOf("images", "variations") -> "generate_content"
            segments.contains("models") -> {
                val i = indexOf("models")
                when {
                    method == "GET" && segments.size == i + 1 -> "models.list"
                    method == "GET" -> "models.retrieve"
                    method == "DELETE" -> "models.delete"
                    else -> "models"
                }
            }
            segments.contains("files") -> {
                val i = indexOf("files")
                when {
                    method == "POST" && segments.size == i + 1 -> "files.create"
                    method == "GET" && segments.size == i + 1 -> "files.list"
                    method == "GET" && segments.lastOrNull() == "content" -> "files.content"
                    method == "DELETE" -> "files.delete"
                    method == "GET" -> "files.retrieve"
                    else -> "files"
                }
            }
            segments.contains("batches") -> {
                val i = indexOf("batches")
                when {
                    method == "POST" && segments.size == i + 1 -> "batches.create"
                    method == "POST" && segments.lastOrNull() == "cancel" -> "batches.cancel"
                    method == "GET" && segments.size == i + 1 -> "batches.list"
                    method == "GET" -> "batches.retrieve"
                    else -> "batches"
                }
            }
            segments.contains("conversations") -> conversationsOperationName(segments, method)
            segments.contains("chat") && segments.contains("completions") -> chatOperationName(segments, method)
            segments.contains("responses") -> responsesOperationName(segments, method)
            segments.contains("videos") -> videosOperationName(segments, method)
            else -> "generate_content"
        }
    }

    private fun outputType(url: TracyHttpUrl): String? = when {
        url.pathSegments.takeLast(2) == listOf("audio", "speech") -> "speech"
        url.pathSegments.takeLast(2) == listOf("audio", "transcriptions") -> "json"
        url.pathSegments.takeLast(2) == listOf("audio", "translations") -> "json"
        url.hasSegment("images") -> "image"
        url.hasSegment("embeddings") -> "embedding"
        else -> null
    }

    private fun chatOperationName(segments: List<String>, method: String): String {
        val i = segments.indexOf("completions")
        return when {
            method == "POST" && segments.size == i + 1 -> "chat"
            method == "GET" && segments.size == i + 1 -> "chat.completions.list"
            method == "GET" && segments.lastOrNull() == "messages" -> "chat.completions.messages.list"
            method == "GET" -> "chat.completions.retrieve"
            method == "POST" -> "chat.completions.update"
            method == "DELETE" -> "chat.completions.delete"
            else -> "chat"
        }
    }

    private fun responsesOperationName(segments: List<String>, method: String): String {
        val i = segments.indexOf("responses")
        return when {
            method == "POST" && segments.size == i + 1 -> "generate_content"
            method == "GET" && segments.lastOrNull() == "input_items" -> "response.input_items.list"
            method == "POST" && segments.lastOrNull() == "input_tokens" -> "response.input_tokens.count"
            method == "POST" && segments.lastOrNull() == "compact" -> "response.compact"
            method == "POST" && segments.lastOrNull() == "cancel" -> "response.cancel"
            method == "DELETE" -> "response.delete"
            method == "GET" -> "response.retrieve"
            else -> "generate_content"
        }
    }

    private fun conversationsOperationName(segments: List<String>, method: String): String {
        val i = segments.indexOf("conversations")
        val hasItems = segments.contains("items")
        return when {
            hasItems && method == "POST" -> "conversations.items.create"
            hasItems && method == "GET" -> "conversations.items.list"
            hasItems && method == "DELETE" -> "conversations.items.delete"
            method == "POST" && segments.size == i + 1 -> "conversations.create"
            method == "POST" -> "conversations.update"
            method == "GET" -> "conversations.retrieve"
            method == "DELETE" -> "conversations.delete"
            else -> "conversations"
        }
    }

    private fun videosOperationName(segments: List<String>, method: String): String {
        val i = segments.indexOf("videos")
        return when {
            method == "POST" && segments.size == i + 1 -> "videos.create"
            method == "POST" && segments.lastOrNull() == "remix" -> "videos.remix"
            method == "GET" && segments.size == i + 1 -> "videos.list"
            method == "GET" && segments.lastOrNull() == "content" -> "videos.content"
            method == "GET" -> "videos.retrieve"
            method == "DELETE" -> "videos.delete"
            else -> "videos"
        }
    }

    private fun TracyHttpUrl.hasSegment(segment: String): Boolean = pathSegments.contains(segment)

    private fun setResourceAliases(span: Span, url: TracyHttpUrl, body: JsonObject) {
        val id = body["id"]?.jsonPrimitive?.contentOrNull
        val createdAt = body["created_at"]?.jsonPrimitive?.longOrNull
        when (apiType(url)) {
            "files" -> {
                id?.let { span.setAttribute("tracy.response.file.id", it) }
                body["bytes"]?.jsonPrimitive?.longOrNull?.let {
                    span.setAttribute("tracy.response.file.size_bytes", it)
                }
                body["filename"]?.jsonPrimitive?.contentOrNull?.let {
                    span.setAttribute("tracy.response.file.filename", it)
                }
                body["purpose"]?.jsonPrimitive?.contentOrNull?.let {
                    span.setAttribute("tracy.response.file.purpose", it)
                }
            }
            "batches" -> {
                id?.let { span.setAttribute("tracy.batch.id", it) }
                body["status"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.batch.status", it) }
                createdAt?.let { span.setAttribute("tracy.batch.created_at", it) }
                body["request_counts"]?.jsonObject?.let { counts ->
                    counts["total"]?.jsonPrimitive?.longOrNull?.let {
                        span.setAttribute("tracy.batch.request_counts.total", it)
                    }
                    counts["completed"]?.jsonPrimitive?.longOrNull?.let {
                        span.setAttribute("tracy.batch.request_counts.completed", it)
                    }
                    counts["failed"]?.jsonPrimitive?.longOrNull?.let {
                        span.setAttribute("tracy.batch.request_counts.failed", it)
                    }
                }
            }
            "conversations" -> {
                id?.let { span.setAttribute("gen_ai.conversation.id", it) }
                createdAt?.let { span.setAttribute("tracy.conversation.created_at", it) }
            }
        }
    }
}

internal val JsonElement.asString: String
    get() = when (this) {
        is JsonArray -> this.jsonArray.toString()
        is JsonObject -> this.jsonObject.toString()
        is JsonPrimitive -> this.jsonPrimitive.content
    }
