/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.PayloadType
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter.Companion.populateUnmappedAttributes
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.http.protocol.asFormData
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Handler for OpenAI Files API.
 *
 * See: [Files API](https://platform.openai.com/docs/api-reference/files)
 */
internal class FilesOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val op = detectFilesOperation(request.url, request.method)
        span.setAttribute(GEN_AI_OPERATION_NAME, op)
        span.setAttribute("openai.api.type", "files")

        when (op) {
            "files.create" -> handleCreateRequest(span, request)
            "files.list" -> handleListRequest(span, request.url)
            "files.content" -> handleContentRequest(span, request.url)
            else -> {}
        }
    }

    private fun handleCreateRequest(span: Span, request: TracyHttpRequest) {
        val formData = request.body.asFormData() ?: return
        for (part in formData.parts) {
            val charset = part.contentType?.charset() ?: Charsets.UTF_8
            when (part.name) {
                "purpose" -> span.setAttribute("tracy.request.purpose", part.content.toString(charset))
                "file" -> {
                    part.filename?.let { span.setAttribute("tracy.request.file.filename", it) }
                    span.setAttribute("tracy.request.file.size_bytes", part.content.size.toLong())
                }
                "expires_after[anchor]" -> span.setAttribute("tracy.request.expires_after.anchor", part.content.toString(charset))
                "expires_after[seconds]" -> part.content.toString(charset).toLongOrNull()?.let {
                    span.setAttribute("tracy.request.expires_after.seconds", it)
                }
            }
        }
    }

    private fun handleListRequest(span: Span, url: TracyHttpUrl) {
        url.parameters.queryParameter("purpose")?.let { span.setAttribute("tracy.request.purpose", it) }
        url.parameters.queryParameter("limit")?.toLongOrNull()?.let { span.setAttribute("tracy.request.limit", it) }
        url.parameters.queryParameter("order")?.let { span.setAttribute("tracy.request.order", it) }
        url.parameters.queryParameter("after")?.let { span.setAttribute("tracy.request.after", it) }
    }

    private fun handleContentRequest(span: Span, url: TracyHttpUrl) {
        // Extract file ID from URL path: /v1/files/{file_id}/content
        val filesIndex = url.pathSegments.indexOf("files")
        if (filesIndex != -1 && url.pathSegments.size > filesIndex + 1) {
            val fileId = url.pathSegments[filesIndex + 1]
            if (fileId.isNotBlank() && fileId != "content") {
                span.setAttribute("tracy.request.file.id", fileId)
            }
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return

        val op = detectFilesOperation(response.url, response.requestMethod)

        when (op) {
            "files.create", "files.retrieve" -> {
                body["id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.file.id", it) }
                body["filename"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.file.filename", it) }
                body["bytes"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("tracy.response.file.size_bytes", it) }
                body["purpose"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.file.purpose", it) }
                body["created_at"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("tracy.response.file.created_at", it) }
                body["status"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.file.status", it) }
                body["expires_at"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("tracy.response.file.expires_at", it) }
            }
            "files.delete" -> {
                body["id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.response.file.id", it) }
                body["deleted"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.response.deleted", it) }
            }
            "files.list" -> {
                body["data"]?.let { data ->
                    if (data is JsonArray) span.setAttribute("tracy.response.list.count", data.size.toLong())
                }
                body["has_more"]?.jsonPrimitive?.booleanOrNull?.let { span.setAttribute("tracy.response.has_more", it) }
            }
            "files.content" -> {
                // Binary content response - size may come from content-length or raw body
                body["size_bytes"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("tracy.response.file.size_bytes", it) }
            }
        }

        span.populateUnmappedAttributes(body, mappedResponseAttributes, PayloadType.RESPONSE)
    }

    override fun handleStreaming(span: Span, events: String) {
        // Files API does not support streaming
    }

    private fun detectFilesOperation(url: TracyHttpUrl, method: String): String {
        val segments = url.pathSegments
        val filesIndex = segments.indexOf("files")
        if (filesIndex == -1) return "files.list"
        val afterFiles = segments.drop(filesIndex + 1).filter { it.isNotBlank() }
        return when {
            afterFiles.isEmpty() && method == "POST" -> "files.create"
            afterFiles.isEmpty() && method == "GET" -> "files.list"
            afterFiles.size == 1 && method == "GET" -> "files.retrieve"
            afterFiles.size == 1 && method == "DELETE" -> "files.delete"
            afterFiles.contains("content") -> "files.content"
            else -> "files.retrieve"
        }
    }

    private val mappedResponseAttributes = listOf("id", "filename", "bytes", "purpose", "created_at", "status", "expires_at", "deleted", "data", "has_more")
}
