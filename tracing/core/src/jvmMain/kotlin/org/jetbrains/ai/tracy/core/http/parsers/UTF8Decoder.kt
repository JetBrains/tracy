/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.http.parsers

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder

/**
 * Stateful UTF-8 decoder that accepts raw chunks of bytes and converts them to valid UTF-8 sequences.
 *
 * This decoder is expected to be used together with [SseParser],
 * as the latter expects valid UTF-8 input.
 *
 * @see SseParser
 */
class UTF8Decoder {
    // Use a stateful UTF-8 decoder so that multibyte sequences split across
    // read boundaries are reassembled correctly instead of producing replacement chars.
    private val utf8Decoder = Charsets.UTF_8.newDecoder()
    // An extra 3 bytes of capacity hold at most one incomplete multi-byte sequence
    // (a 4-byte UTF-8 code-point can leave up to 3 bytes undecoded when only
    // the first 1–3 bytes arrive in a chunk).
    private val byteBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE + 3)
    // Each UTF-8 code unit is at least 1 byte, so the char count cannot exceed the
    // byte count. In the worst case (all ASCII), byteBuffer holds DEFAULT_BUFFER_SIZE + 3
    // bytes, so charBuffer needs the same capacity.
    private val charBuffer = CharBuffer.allocate(DEFAULT_BUFFER_SIZE + 3)

    /**
     *
     * @param buffer The raw bytes to decode.
     * @param bytesRead The number of bytes read from the buffer.
     * @param endOfInput `true` if, and only if, the invoker can provide
     *                   no additional input bytes beyond those in the given buffer
     *                   (see `endOfInput` description in [CharsetDecoder.decode])
     */
    fun decode(
        buffer: ByteArray,
        bytesRead: Int,
        endOfInput: Boolean,
    ): String {
        if (bytesRead <= 0) {
            return ""
        }

        byteBuffer.put(buffer, 0, bytesRead)
        byteBuffer.flip()
        charBuffer.clear()

        utf8Decoder.decode(byteBuffer, charBuffer, endOfInput)
        // move any undecoded partial-sequence bytes to the start of the buffer
        byteBuffer.compact()
        charBuffer.flip()

        return if (charBuffer.hasRemaining()) {
            charBuffer.toString()
        } else {
            ""
        }
    }
}
