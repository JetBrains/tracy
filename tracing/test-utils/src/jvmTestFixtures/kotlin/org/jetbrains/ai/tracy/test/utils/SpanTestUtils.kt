/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import org.jetbrains.ai.tracy.core.adapters.media.UploadableMediaContentAttributeKeys

/**
 * Extension function to convert [SpanData] to [ReadableSpan].
 * Useful for testing span processors that require [ReadableSpan] instances.
 */
fun SpanData.toReadableSpan(): ReadableSpan = ReadableSpanAdapter(this)

/**
 * Creates an instance of [SpanData] with the given [traceId], [spanName] and [mediaAttributes].
 *
 * @see MediaContentAttributeValues
 */
fun createTestSpanData(
    traceId: String = DEFAULT_TRACE_ID,
    spanName: String = "test-span",
    mediaAttributes: List<MediaContentAttributeValues> = emptyList(),
): SpanData {
    val attributesBuilder = Attributes.builder()

    for ((index, media) in mediaAttributes.withIndex()) {
        val keys = UploadableMediaContentAttributeKeys.forIndex(index)
        attributesBuilder.put(keys.type, media.type.type)

        when (media) {
            is MediaContentAttributeValues.Url -> {
                attributesBuilder.put(keys.field, media.field)
                attributesBuilder.put(keys.url, media.url!!)
            }
            is MediaContentAttributeValues.Data -> {
                attributesBuilder.put(keys.field, media.field)
                attributesBuilder.put(keys.contentType, media.contentType)
                attributesBuilder.put(keys.data, media.data!!)
            }
        }
    }

    return TestSpanData.builder()
        .setName(spanName)
        .setKind(SpanKind.INTERNAL)
        .setSpanContext(
            SpanContext.create(
                traceId,
                "0000000000000001",
                TraceFlags.getSampled(),
                TraceState.getDefault()
            )
        )
        .setStartEpochNanos(System.nanoTime())
        .setEndEpochNanos(System.nanoTime())
        .setStatus(StatusData.ok())
        .setHasEnded(true)
        .setAttributes(attributesBuilder.build())
        .setTotalAttributeCount(attributesBuilder.build().size())
        .setEvents(emptyList())
        .setLinks(emptyList())
        .setResource(Resource.getDefault())
        .setInstrumentationScopeInfo(InstrumentationScopeInfo.empty())
        .build()
}

private const val DEFAULT_TRACE_ID = "00000000000000000000000000000001"

/**
 * Lightweight adapter that wraps [SpanData] as [ReadableSpan].
 * This adapter delegates all calls to the underlying [SpanData] instance.
 */
private class ReadableSpanAdapter(private val span: SpanData) : ReadableSpan {
    override fun getSpanContext(): SpanContext = span.spanContext
    override fun getParentSpanContext(): SpanContext = span.parentSpanContext
    override fun getName(): String = span.name
    override fun toSpanData(): SpanData = span
    @Deprecated("Deprecated in Java")
    override fun getInstrumentationLibraryInfo(): InstrumentationLibraryInfo = span.instrumentationLibraryInfo
    override fun hasEnded(): Boolean = span.hasEnded()
    override fun getKind(): SpanKind = span.kind
    override fun getLatencyNanos(): Long = throw UnsupportedOperationException("Not supported in test adapter")
    override fun getAttributes(): Attributes = span.attributes
    override fun <T> getAttribute(key: AttributeKey<T>): T? = span.attributes.get(key)
}
