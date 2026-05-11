/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.http.parsers.FormData
import org.jetbrains.ai.tracy.core.http.parsers.FormPart
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.http.protocol.asFormData
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import org.jetbrains.ai.tracy.core.policy.orRedactedInput
import org.jetbrains.ai.tracy.core.policy.orRedactedOutput
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal class AudioOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        when (request.url.lastMeaningfulSegment()) {
            "speech" -> handleSpeechRequest(span, request)
            "transcriptions", "translations" -> handleSpeechToTextRequest(span, request)
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        when (response.url.lastMeaningfulSegment()) {
            "speech" -> {
                span.setAttribute(GEN_AI_OUTPUT_TYPE, "speech")
                response.contentType?.asString()?.let { span.setAttribute("tracy.response.audio.content_type", it) }
            }
            "transcriptions" -> handleTranscriptionResponse(span, response, "transcription")
            "translations" -> handleTranscriptionResponse(span, response, "translation")
        }
    }

    override fun handleStreaming(span: Span, events: String) {
        span.setAttribute("tracy.response.stream.events.count", countSseEvents(events).toLong())
        var text = ""
        for (event in events.sseJsonEvents()) {
            event["text"]?.jsonPrimitive?.contentOrNull?.let { text = it }
            event["delta"]?.jsonPrimitive?.contentOrNull?.let { text += it }
            event["usage"]?.jsonObject?.let { OpenAIApiUtils.setUsageAttributes(span, it) }
        }
        if (text.isNotEmpty()) span.setAttribute("tracy.response.text", text.orRedactedOutput())
    }

    private fun handleSpeechRequest(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return
        OpenAIApiUtils.setCommonRequestAttributes(span, request)
        body["voice"]?.stringContent()?.let { span.setAttribute("tracy.request.voice", it.orRedactedInput()) }
        body["response_format"]?.stringContent()?.let { span.setAttribute("tracy.request.response_format", it) }
        body["stream_format"]?.stringContent()?.let { span.setAttribute("tracy.request.stream_format", it) }
        body["speed"]?.jsonPrimitive?.doubleOrNull?.let { span.setAttribute("tracy.request.speed", it) }
        body["input"]?.stringContent()?.let { span.setAttribute("tracy.request.input", it.orRedactedInput()) }
    }

    private fun handleSpeechToTextRequest(span: Span, request: TracyHttpRequest) {
        val form = request.body.asFormData() ?: return
        val values = form.values()
        values["model"]?.let { span.setAttribute(GEN_AI_REQUEST_MODEL, it) }
        values["response_format"]?.let {
            span.setAttribute("tracy.request.response_format", it)
            span.setAttribute(GEN_AI_OUTPUT_TYPE, "json")
        }
        values["language"]?.let { span.setAttribute("tracy.request.language", it) }
        values["prompt"]?.let { span.setAttribute("tracy.request.prompt.present", it.isNotEmpty()) }
        values["temperature"]?.toDoubleOrNull()?.let { span.setAttribute("tracy.request.temperature", it) }
        values["stream"]?.toBooleanStrictOrNull()?.let { span.setAttribute("gen_ai.request.stream", it) }
        values["include"]?.let { span.setAttribute("tracy.request.include", it) }
        values["include[]"]?.let { span.setAttribute("tracy.request.include", it) }
        values["timestamp_granularities[]"]?.let { span.setAttribute("tracy.request.timestamp_granularities", it) }

        form.parts.firstOrNull { it.name == "file" }?.let { file ->
            span.setAttribute("tracy.request.audio.size_bytes", file.content.size.toLong())
            audioFormat(file)?.let { span.setAttribute("tracy.request.audio.format", it) }
        }
    }

    private fun handleTranscriptionResponse(span: Span, response: TracyHttpResponse, prefix: String) {
        val body = response.body.asJson()?.jsonObject ?: return
        span.setAttribute(GEN_AI_OUTPUT_TYPE, "json")
        body["text"]?.stringContent()?.let { span.setAttribute("tracy.response.text", it.orRedactedOutput()) }
        body["duration"]?.jsonPrimitive?.doubleOrNull?.let {
            span.setAttribute("tracy.response.$prefix.duration_seconds", it)
        }
        body["language"]?.stringContent()?.let { span.setAttribute("tracy.response.$prefix.language", it) }
        (body["words"] as? JsonArray)?.let { span.setAttribute("tracy.response.$prefix.words.count", it.size.toLong()) }
        body["usage"]?.jsonObject?.let { OpenAIApiUtils.setUsageAttributes(span, it) }
    }
}

internal class EmbeddingsOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return
        OpenAIApiUtils.setCommonRequestAttributes(span, request)
        body["encoding_format"]?.stringContent()?.let {
            span.setAttribute(AttributeKey.stringArrayKey("gen_ai.request.encoding_formats"), listOf(it))
        }
        body["dimensions"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("gen_ai.embeddings.dimension.count", it) }
        body["input"]?.let {
            span.setAttribute("tracy.request.input.type", inputType(it))
            span.setAttribute("tracy.request.input.count", inputCount(it).toLong())
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        OpenAIApiUtils.setListResponseAttributes(span, body)
        body["model"]?.stringContent()?.let { span.setAttribute("tracy.response.model", it) }
        body["usage"]?.jsonObject?.let { OpenAIApiUtils.setUsageAttributes(span, it) }
        (body["data"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("embedding")?.jsonArray?.let {
            span.setAttribute("gen_ai.embeddings.dimension.count", it.size.toLong())
        }
    }

    override fun handleStreaming(span: Span, events: String) = Unit
}

internal class FilesOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        setPaginationRequestAttributes(span, request.url)
        extractPathId(request.url, "files")?.let { span.setAttribute("tracy.request.file.id", it) }
        val form = request.body.asFormData() ?: return
        val values = form.values()
        values["purpose"]?.let {
            span.setAttribute("tracy.request.purpose", it)
            span.setAttribute("tracy.request.file.purpose", it)
        }
        values["expires_after[anchor]"]?.let { span.setAttribute("tracy.request.expires_after.anchor", it) }
        values["expires_after[seconds]"]?.toLongOrNull()?.let {
            span.setAttribute("tracy.request.expires_after.seconds", it)
        }
        form.parts.firstOrNull { it.name == "file" }?.let {
            span.setAttribute("tracy.request.file.size_bytes", it.content.size.toLong())
            it.filename?.let { filename ->
                span.setAttribute("tracy.request.file.filename", filename.orRedactedInput())
                span.setAttribute("tracy.request.file.name", filename.orRedactedInput())
            }
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        if (body["data"] is JsonArray) {
            OpenAIApiUtils.setListResponseAttributes(span, body)
            return
        }
        traceFileObject(span, body, "tracy.response.file")
        body["deleted"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.response.deleted", it) }
    }

    override fun handleStreaming(span: Span, events: String) = Unit
}

internal class BatchesOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        setPaginationRequestAttributes(span, request.url)
        val body = request.body.asJson()?.jsonObject ?: return
        body["endpoint"]?.stringContent()?.let { span.setAttribute("tracy.request.batch.endpoint", it) }
        body["completion_window"]?.stringContent()?.let { span.setAttribute("tracy.request.batch.completion_window", it) }
        body["input_file_id"]?.stringContent()?.let { span.setAttribute("tracy.request.batch.input_file.id", it) }
        body["metadata"]?.jsonObject?.let { span.setAttribute("tracy.request.metadata.keys", it.keys.joinToString(",")) }
        body["output_expires_after"]?.jsonObject?.let {
            it["anchor"]?.stringContent()?.let { anchor ->
                span.setAttribute("tracy.request.batch.output_expires_after.anchor", anchor)
            }
            it["seconds"]?.jsonPrimitive?.longOrNull?.let { seconds ->
                span.setAttribute("tracy.request.batch.output_expires_after.seconds", seconds)
            }
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        if (body["data"] is JsonArray) {
            OpenAIApiUtils.setListResponseAttributes(span, body)
            return
        }
        traceBatch(span, body)
    }

    override fun handleStreaming(span: Span, events: String) = Unit
}

internal class ModelsOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        extractPathId(request.url, "models")?.let { span.setAttribute("tracy.request.model.id", it) }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        if (body["data"] is JsonArray) {
            OpenAIApiUtils.setListResponseAttributes(span, body, countKey = "tracy.response.models.count")
            return
        }
        body["id"]?.stringContent()?.let { span.setAttribute("tracy.response.model.id", it) }
        body["object"]?.stringContent()?.let { span.setAttribute("tracy.response.object", it) }
        body["deleted"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.response.deleted", it) }
    }

    override fun handleStreaming(span: Span, events: String) = Unit
}

internal class ModerationsOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val body = request.body.asJson()?.jsonObject ?: return
        OpenAIApiUtils.setCommonRequestAttributes(span, request)
        body["input"]?.let {
            span.setAttribute("tracy.request.input.type", inputType(it))
            span.setAttribute("tracy.request.input.count", inputCount(it).toLong())
            if (it is JsonArray) {
                span.setAttribute("tracy.request.input.content.type", it.firstOrNull()?.jsonObject?.get("type")?.stringContent())
            }
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        (body["results"] as? JsonArray)?.let { results ->
            span.setAttribute("tracy.response.results.count", results.size.toLong())
            span.setAttribute("tracy.response.results.flagged", results.any {
                it.jsonObject["flagged"]?.jsonPrimitive?.booleanOrNull == true
            })
            results.firstOrNull()?.jsonObject?.let {
                it["categories"]?.let { categories -> span.setAttribute("tracy.response.results.categories", categories.toString()) }
                it["category_scores"]?.let { scores ->
                    span.setAttribute("tracy.response.results.category_scores", scores.toString())
                }
                it["category_applied_input_types"]?.let { types ->
                    span.setAttribute("tracy.response.results.category_applied_input_types", types.toString())
                }
            }
        }
    }

    override fun handleStreaming(span: Span, events: String) = Unit
}

internal class ConversationsOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        setPaginationRequestAttributes(span, request.url)
        extractPathId(request.url, "conversations")?.let { span.setAttribute("gen_ai.conversation.id", it) }
        val body = request.body.asJson()?.jsonObject ?: return
        body["items"]?.jsonArray?.let { span.setAttribute("tracy.conversation.items.count", it.size.toLong()) }
        body["metadata"]?.jsonObject?.let { span.setAttribute("tracy.request.metadata.keys", it.keys.joinToString(",")) }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        OpenAIApiUtils.setListResponseAttributes(span, body, countKey = "tracy.conversation.items.count")
        body["id"]?.stringContent()?.let {
            span.setAttribute("gen_ai.conversation.id", it)
            span.setAttribute("tracy.conversation.item.id", it)
        }
        body["created_at"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("tracy.conversation.created_at", it) }
        body["deleted"]?.jsonPrimitive?.booleanOrNull?.let {
            span.setAttribute("tracy.conversation.deleted", it)
            span.setAttribute("tracy.response.deleted", it)
        }
        body["type"]?.stringContent()?.let { span.setAttribute("tracy.conversation.item.type", it) }
        body["status"]?.stringContent()?.let { span.setAttribute("tracy.conversation.item.status", it) }
        body["first_id"]?.stringContent()?.let { span.setAttribute("tracy.conversation.items.first_id", it) }
        body["last_id"]?.stringContent()?.let { span.setAttribute("tracy.conversation.items.last_id", it) }
        body["has_more"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.conversation.items.has_more", it) }
    }

    override fun handleStreaming(span: Span, events: String) = Unit
}

internal fun setPaginationRequestAttributes(span: Span, url: TracyHttpUrl) {
    url.parameters.queryParameter("limit")?.toLongOrNull()?.let { span.setAttribute("tracy.request.limit", it) }
    url.parameters.queryParameter("order")?.let { span.setAttribute("tracy.request.order", it) }
    url.parameters.queryParameter("after")?.let { span.setAttribute("tracy.request.after", it) }
    url.parameters.queryParameter("purpose")?.let { span.setAttribute("tracy.request.purpose", it) }
}

internal fun extractPathId(url: TracyHttpUrl, segment: String): String? {
    val segments = url.pathSegments.filter { it.isNotBlank() && it != "v1" }
    val index = segments.indexOf(segment)
    return segments.getOrNull(index + 1)
        ?.takeUnless { it in setOf("content", "cancel", "items") }
}

private fun traceFileObject(span: Span, body: JsonObject, prefix: String) {
    body["id"]?.stringContent()?.let {
        span.setAttribute("gen_ai.response.id", it)
        span.setAttribute("$prefix.id", it)
    }
    body["filename"]?.stringContent()?.let { span.setAttribute("$prefix.filename", it.orRedactedOutput()) }
    body["purpose"]?.stringContent()?.let { span.setAttribute("$prefix.purpose", it) }
    body["bytes"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("$prefix.size_bytes", it) }
    body["created_at"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("$prefix.created_at", it) }
    body["expires_at"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("$prefix.expires_at", it) }
    body["status"]?.stringContent()?.let { span.setAttribute("$prefix.status", it) }
}

private fun traceBatch(span: Span, body: JsonObject) {
    body["id"]?.stringContent()?.let { span.setAttribute("tracy.batch.id", it) }
    body["status"]?.stringContent()?.let { span.setAttribute("tracy.batch.status", it) }
    body["created_at"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("tracy.batch.created_at", it) }
    body["request_counts"]?.jsonObject?.let {
        it["total"]?.jsonPrimitive?.longOrNull?.let { total -> span.setAttribute("tracy.batch.request_counts.total", total) }
        it["completed"]?.jsonPrimitive?.longOrNull?.let { completed ->
            span.setAttribute("tracy.batch.request_counts.completed", completed)
        }
        it["failed"]?.jsonPrimitive?.longOrNull?.let { failed ->
            span.setAttribute("tracy.batch.request_counts.failed", failed)
        }
    }
}

private fun FormData.values(): Map<String, String> =
    parts.mapNotNull { part -> part.name?.let { it to part.textContent() } }.toMap()

private fun FormPart.textContent(): String =
    content.toString(contentType?.charset() ?: Charsets.UTF_8)

private fun audioFormat(part: FormPart): String? =
    part.contentType?.subtype ?: part.filename?.substringAfterLast('.', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }

private fun inputType(input: JsonElement): String = when (input) {
    is JsonArray -> "array"
    is JsonPrimitive -> when {
        input.isString -> "string"
        else -> "tokens"
    }
    else -> "object"
}

private fun inputCount(input: JsonElement): Int = when (input) {
    is JsonArray -> input.size
    else -> 1
}

private fun countSseEvents(events: String): Int = events.lineSequence().count { it.startsWith("data:") }

private fun String.sseJsonEvents(): Sequence<JsonObject> = lineSequence().mapNotNull { line ->
    if (!line.startsWith("data:")) return@mapNotNull null
    val data = line.removePrefix("data:").trim()
    runCatching { kotlinx.serialization.json.Json.parseToJsonElement(data).jsonObject }.getOrNull()
}

private fun TracyHttpUrl.lastMeaningfulSegment(): String? =
    pathSegments.lastOrNull { it.isNotBlank() && it != "v1" }
