/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.test.utils

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * Extension function to convert [SpanData] to [ReadableSpan].
 * Useful for testing span processors that require [ReadableSpan] instances.
 */
fun SpanData.toReadableSpan(): ReadableSpan = ReadableSpanAdapter(this)

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
