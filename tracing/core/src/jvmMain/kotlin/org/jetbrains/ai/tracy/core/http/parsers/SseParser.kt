/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.http.parsers

import java.io.Closeable

/**
 * A **stateful, non-thread-safe** parser of Server-Sent Events (SSE) compliant with the SSE specification.
 *
 * Parses server-sent events present in the input text and yields them as a structured [SseEvent].
 *
 * See: [SSE Specification | Event Stream Interpretation](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation)
 *
 * @param onEvent A callback invoked for each parsed event.
 * @see SseEvent
 */
class SseParser(private val onEvent: (SseEvent) -> Unit) : Closeable {
    // state of an event being parsed
    private val lineBuffer = StringBuilder()
    private val dataBuffer = StringBuilder()
    private var eventType = ""
    private var lastEventId = ""
    private var retryValue: Long? = null

    private var isFirstChunk = true

    /**
     * The input [text] is expected to be already decoded with UTF-8 (see the note in [spec](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation)).
     */
    fun feed(text: String) {
        var input = text

        if (isFirstChunk) {
            isFirstChunk = false
            if (input.startsWith(BOM_UTF8_BYTE)) {
                input = input.substring(1)
            }
        }

        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                ch == '\r' -> {
                    processLine(lineBuffer.toString())
                    lineBuffer.clear()
                    if (i + 1 < input.length && input[i + 1] == '\n') i++
                }
                ch == '\n' -> {
                    processLine(lineBuffer.toString())
                    lineBuffer.clear()
                }
                else -> lineBuffer.append(ch)
            }
            i++
        }
    }

    private fun processLine(line: String) {
        if (line.isEmpty()) { dispatchEvent(); return }
        if (line.startsWith(':')) return // comment

        val colonIdx = line.indexOf(':')
        val field: String
        val value: String
        if (colonIdx == -1) {
            field = line; value = ""
        } else {
            field = line.substring(0, colonIdx)
            val start = if (colonIdx + 1 < line.length && line[colonIdx + 1] == ' ')
                colonIdx + 2 else colonIdx + 1
            value = line.substring(start)
        }

        when (field) {
            "data" -> { if (dataBuffer.isNotEmpty()) dataBuffer.append('\n'); dataBuffer.append(value) }
            "event" -> eventType = value
            "id" -> if ('\u0000' !in value) lastEventId = value
            "retry" -> if (value.isNotEmpty() && value.all { it in '0'..'9' }) retryValue = value.toLongOrNull()
        }
    }

    private fun dispatchEvent() {
        if (dataBuffer.isEmpty()) { eventType = ""; return }
        onEvent(SseEvent(
            data = dataBuffer.toString(),
            event = eventType.ifEmpty { "message" },
            id = lastEventId,
            retry = retryValue,
        ))
        dataBuffer.clear()
        eventType = ""
        retryValue = null
        // lastEventId persists across events per spec
    }

    override fun close() {
        // purging memory: remove any unfinished/malformed event data
        lineBuffer.clear()
        dataBuffer.clear()
    }
}

/**
 * Represents a single event in a Server-Sent Events stream.
 *
 * @see SseParser
 */
data class SseEvent(
    val data: String,
    val event: String = "",
    val id: String = "",
    val retry: Long? = null,
)

private val BOM_UTF8_BYTE = '\uFEFF'
