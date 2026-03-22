/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.interceptors

import io.opentelemetry.api.trace.Span
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.http.parsers.SseParser
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl


internal class SseCapturingSource(
    delegate: Source,
    private val adapter: LLMTracingAdapter,
    private val span: Span,
    private val url: TracyHttpUrl,
) : ForwardingSource(delegate) {
    private val parser = SseParser { event ->
        // dispatch SSE events to the adapter
        adapter.handleStreamingEvent(span, url, event)
    }

    override fun read(sink: Buffer, byteCount: Long): Long {
        val bytesRead = super.read(sink, byteCount)
        if (bytesRead == -1L) {
            return -1L
        }

        // peek at the bytes just written to sink
        val text = sink.peek().apply {
            skip(sink.size - bytesRead)
        }.readUtf8(bytesRead)

        parser.feed(text)
        return bytesRead
    }

    override fun close() {
        super.close()
        parser.close()
    }
}