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

    /**
     * The [input] is expected to be already decoded with UTF-8
     * (see the note in [spec](https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation)).
     */
    fun feed(input: String) {
        var i = 0
        while (i < input.length) {
            // lines may be split by any of:
            //  1. \r\n (CRLF): `U+000D` CARRIAGE RETURN, `U+000A` LINE FEED
            //  2. \n (LF): `U+000A` LINE FEED
            //  3. \r (CR): `U+000D` CARRIAGE RETURN
            when (val ch = input[i]) {
                // CR
                '\r' -> {
                    processLine(lineBuffer.toString())
                    lineBuffer.clear()
                    // CRLF: skip the next character
                    if (i + 1 < input.length && input[i + 1] == '\n') {
                        i++
                    }
                }
                // LF
                '\n' -> {
                    processLine(lineBuffer.toString())
                    lineBuffer.clear()
                }
                else -> lineBuffer.append(ch)
            }
            i++
        }
    }

    /**
     *
     * @param line An entry line of an event. The line **does NOT** end with CRLF/LF/CR; it only contains the event-related information.
     */
    private fun processLine(line: String) {
        // empty line -> dispatch
        if (line.isEmpty()) {
            dispatchEvent()
            return
        }
        // starts with colon (a comment line) -> ignore
        if (line.startsWith(':')) {
            return
        }

        val colonIdx = line.indexOf(':')
        val field: String
        val value: String

        if (colonIdx == -1) {
            // no colon -> use the whole line as field name, and empty string as field value
            field = line
            value = ""
        } else {
            // content before colon -> field name
            field = line.substring(0, colonIdx)
            // content after colon -> field value
            val start = if (colonIdx + 1 < line.length && line[colonIdx + 1] == ' ') {
                // ignore the very first space after colon
                colonIdx + 2
            } else {
                colonIdx + 1
            }
            value = line.substring(start)
        }

        when (field) {
            "data" -> {
                // Steps:
                //   1. Append field value to data buffer
                //   2. Append a single U+000A LINE FEED (LF) character to data buffer
                // Note: when dispatching, the trailing LF is removed; here, we simply don't add it right after
                //       the value but rather append it to the previous value, if any.
                if (dataBuffer.isNotEmpty()) {
                    dataBuffer.append('\n')
                }
                dataBuffer.append(value)
            }
            "event" -> {
                // set the event type buffer to the field value
                eventType = value
            }
            "id" -> {
                // field value doesn't contain U+0000 NULL -> set last event ID buffer to the field value
                if ('\u0000' !in value) {
                    lastEventId = value
                }
            }
            "retry" -> {
                // contains only ASCII digits -> interpret the field value as int in base ten
                if (value.isNotEmpty() && value.all { it in '0'..'9' }) {
                    retryValue = value.toLongOrNull()
                }
            }
        }
    }

    private fun dispatchEvent() {
        if (dataBuffer.isEmpty()) {
            eventType = ""
            retryValue = null
            return
        }

        onEvent(SseEvent(
            data = dataBuffer.toString(),
            event = eventType.ifEmpty { "message" },
            id = lastEventId,
            retry = retryValue,
        ))

        dataBuffer.clear()
        eventType = ""
        retryValue = null
        // `lastEventId` persists across events per spec
    }

    override fun close() {
        // flush any pending line as if the stream ended with a newline
        if (lineBuffer.isNotEmpty()) {
            processLine(lineBuffer.toString())
            lineBuffer.clear()
        }
        // if there is any buffered event data, dispatch a final event
        if (dataBuffer.isNotEmpty()) {
            dispatchEvent()
        }
    }
}

/**
 * Represents a single event in a Server-Sent Events stream.
 *
 * @see SseParser
 */
data class SseEvent(
    val event: String = "",
    val id: String = "",
    val data: String,
    val retry: Long? = null,
)
