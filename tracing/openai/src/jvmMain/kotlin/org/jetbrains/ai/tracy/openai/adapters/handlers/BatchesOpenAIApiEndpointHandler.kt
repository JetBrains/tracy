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
import org.jetbrains.ai.tracy.core.http.protocol.asJson
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Handler for OpenAI Batches API.
 *
 * See: [Batches API](https://platform.openai.com/docs/api-reference/batch)
 */
internal class BatchesOpenAIApiEndpointHandler : EndpointApiHandler {
    override fun handleRequestAttributes(span: Span, request: TracyHttpRequest) {
        val op = detectBatchesOperation(request.url, request.method)
        span.setAttribute(GEN_AI_OPERATION_NAME, op)
        span.setAttribute("openai.api.type", "batches")

        if (op == "batches.create") {
            val body = request.body.asJson()?.jsonObject ?: return
            body["endpoint"]?.jsonPrimitive?.contentOrNull?.let {
                span.setAttribute("tracy.request.batch.endpoint", it)
            }
            body["completion_window"]?.jsonPrimitive?.contentOrNull?.let {
                span.setAttribute("tracy.request.batch.completion_window", it)
            }
            body["input_file_id"]?.jsonPrimitive?.contentOrNull?.let {
                span.setAttribute("tracy.request.batch.input_file.id", it)
            }
            body["metadata"]?.jsonObject?.let { metadata ->
                span.setAttribute("tracy.request.metadata.keys", metadata.keys.joinToString(","))
            }
            // output_expires_after
            body["output_expires_after"]?.jsonObject?.let { oea ->
                oea["anchor"]?.jsonPrimitive?.contentOrNull?.let {
                    span.setAttribute("tracy.request.batch.output_expires_after.anchor", it)
                }
                oea["seconds"]?.jsonPrimitive?.longOrNull?.let {
                    span.setAttribute("tracy.request.batch.output_expires_after.seconds", it)
                }
            }
            span.populateUnmappedAttributes(body, mappedCreateRequestAttributes, PayloadType.REQUEST)
        } else if (op == "batches.list") {
            request.url.parameters.queryParameter("limit")?.toLongOrNull()?.let {
                span.setAttribute("tracy.request.limit", it)
            }
            request.url.parameters.queryParameter("after")?.let {
                span.setAttribute("tracy.request.after", it)
            }
        }
    }

    override fun handleResponseAttributes(span: Span, response: TracyHttpResponse) {
        val body = response.body.asJson()?.jsonObject ?: return
        val op = detectBatchesOperation(response.url, response.requestMethod)

        when (op) {
            "batches.list" -> {
                body["data"]?.let { data ->
                    if (data is JsonArray) span.setAttribute("tracy.response.list.count", data.size.toLong())
                }
                body["has_more"]?.jsonPrimitive?.let { span.setAttribute("tracy.response.has_more", it.toString()) }
            }
            else -> setBatchAttributes(span, body)
        }

        span.populateUnmappedAttributes(body, mappedResponseAttributes, PayloadType.RESPONSE)
    }

    private fun setBatchAttributes(span: Span, body: kotlinx.serialization.json.JsonObject) {
        body["id"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.batch.id", it) }
        body["status"]?.jsonPrimitive?.contentOrNull?.let { span.setAttribute("tracy.batch.status", it) }
        body["created_at"]?.jsonPrimitive?.longOrNull?.let { span.setAttribute("tracy.batch.created_at", it) }
        body["request_counts"]?.jsonObject?.let { counts ->
            counts["total"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute("tracy.batch.request_counts.total", it.toLong()) }
            counts["completed"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute("tracy.batch.request_counts.completed", it.toLong()) }
            counts["failed"]?.jsonPrimitive?.intOrNull?.let { span.setAttribute("tracy.batch.request_counts.failed", it.toLong()) }
        }
    }

    override fun handleStreaming(span: Span, events: String) {
        // Batches API does not support streaming
    }

    private fun detectBatchesOperation(url: TracyHttpUrl, method: String): String {
        val segments = url.pathSegments
        val batchesIndex = segments.indexOf("batches")
        if (batchesIndex == -1) return "batches.list"
        val afterBatches = segments.drop(batchesIndex + 1).filter { it.isNotBlank() }
        return when {
            afterBatches.isEmpty() && method == "POST" -> "batches.create"
            afterBatches.isEmpty() && method == "GET" -> "batches.list"
            afterBatches.size == 1 && method == "GET" -> "batches.retrieve"
            afterBatches.contains("cancel") -> "batches.cancel"
            else -> "batches.retrieve"
        }
    }

    private val mappedCreateRequestAttributes = listOf("endpoint", "completion_window", "input_file_id", "metadata", "output_expires_after")
    private val mappedResponseAttributes = listOf("id", "status", "created_at", "request_counts", "data", "has_more")
}
