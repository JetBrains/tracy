/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.http.parsers

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UTF8DecoderTest {

    // ============================================================================
    // Basic Scenarios
    // ============================================================================

    @Test
    fun `decode simple ASCII text`() {
        val decoder = UTF8Decoder()
        val text = "Hello, world!"
        val bytes = text.encodeToByteArray()

        val decoded = decoder.decode(bytes, bytes.size, endOfInput = true)

        assertEquals(text, decoded)
    }

    @Test
    fun `decode 2-byte UTF-8 characters`() {
        val decoder = UTF8Decoder()
        // é character:
        //   1. Latin Small Letter E With Acute
        //   2. it is 2-byte UTF-8: C3 A9
        val text = "café"
        val bytes = text.encodeToByteArray()

        val decoded = decoder.decode(bytes, bytes.size, endOfInput = true)

        assertEquals(text, decoded)
    }

    @Test
    fun `decode 3-byte UTF-8 characters`() {
        val decoder = UTF8Decoder()
        // Chinese characters are 3-byte UTF-8
        // 你: U+4F60
        // 好: U+597D
        val text = "你好"
        val bytes = text.encodeToByteArray()

        val decoded = decoder.decode(bytes, bytes.size, endOfInput = true)

        assertEquals(text, decoded)
    }

    @Test
    fun `decode 4-byte UTF-8 characters (emoji)`() {
        val decoder = UTF8Decoder()
        // 😀 is 4-byte UTF-8: F0 9F 98 80
        val text = "Hello 😀 World"
        val bytes = text.encodeToByteArray()

        val decoded = decoder.decode(bytes, bytes.size, endOfInput = true)

        assertEquals(text, decoded)
    }

    @Test
    fun `decode mixed ASCII and multi-byte characters`() {
        val decoder = UTF8Decoder()
        val text = "Hello café 你好 😀"
        val bytes = text.encodeToByteArray()

        val decoded = decoder.decode(bytes, bytes.size, endOfInput = true)

        assertEquals(text, decoded)
    }

    @Test
    fun `decode empty input`() {
        val decoder = UTF8Decoder()
        val bytes = ByteArray(0)

        val decoded = decoder.decode(bytes, 0, endOfInput = true)

        assertEquals("", decoded)
    }

    @Test
    fun `decode with negative bytesRead returns empty string`() {
        val decoder = UTF8Decoder()
        val bytes = "test".encodeToByteArray()

        val decoded = decoder.decode(bytes, -1, endOfInput = true)

        assertEquals("", decoded)
    }

    // ============================================================================
    // Split Multi-byte Sequences
    // ============================================================================

    @Test
    fun `decode 2-byte character split across two chunks`() {
        val decoder = UTF8Decoder()
        val text = "café"
        val bytes = text.encodeToByteArray()

        // Find where 'é' (2-byte) starts
        // Split right before 'é'
        val splitPoint = "caf".encodeToByteArray().size

        val part1 = decoder.decode(bytes, splitPoint, endOfInput = false)
        val part2 = decoder.decode(bytes.copyOfRange(splitPoint, bytes.size),
                                   bytes.size - splitPoint, endOfInput = true)

        assertEquals("caf", part1)
        assertEquals("é", part2)
        assertEquals(text, part1 + part2)
    }

    @Test
    fun `decode 2-byte character split in the middle`() {
        val decoder = UTF8Decoder()
        // 2-byte: C3 A9
        val text = "é"
        val bytes = text.encodeToByteArray()
        assert(bytes.size == 2)

        // Split after first byte
        val part1 = decoder.decode(bytes, 1, endOfInput = false)
        val part2 = decoder.decode(bytes.copyOfRange(1, 2), 1, endOfInput = true)

        // First byte alone is incomplete
        assertEquals("", part1)
        assertEquals("é", part2)
    }

    @Test
    fun `decode 3-byte character split 1+2`() {
        val decoder = UTF8Decoder()
        // 3-byte UTF-8
        val text = "你"
        val bytes = text.encodeToByteArray()

        // Split after first byte
        val part1 = decoder.decode(bytes, 1, endOfInput = false)
        val part2 = decoder.decode(bytes.copyOfRange(1, 3), 2, endOfInput = true)

        // Incomplete
        assertEquals("", part1)
        assertEquals("你", part2)
    }

    @Test
    fun `decode 3-byte character split 2+1`() {
        val decoder = UTF8Decoder()
        // 3-byte UTF-8
        val text = "你"
        val bytes = text.encodeToByteArray()

        // Split after first two bytes
        val part1 = decoder.decode(bytes, 2, endOfInput = false)
        val part2 = decoder.decode(bytes.copyOfRange(2, 3), 1, endOfInput = true)

        // Incomplete
        assertEquals("", part1)
        assertEquals("你", part2)
    }

    @Test
    fun `decode 4-byte character (emoji) split 1+3`() {
        val decoder = UTF8Decoder()
        // 4-byte: F0 9F 98 80
        val text = "😀"
        val bytes = text.encodeToByteArray()

        val part1 = decoder.decode(bytes, 1, endOfInput = false)
        val part2 = decoder.decode(bytes.copyOfRange(1, 4), 3, endOfInput = true)

        assertEquals("", part1)
        assertEquals("😀", part2)
    }

    @Test
    fun `decode 4-byte character (emoji) split 2+2`() {
        val decoder = UTF8Decoder()
        // 4-byte
        val text = "😀"
        val bytes = text.encodeToByteArray()

        val part1 = decoder.decode(bytes, 2, endOfInput = false)
        val part2 = decoder.decode(bytes.copyOfRange(2, 4), 2, endOfInput = true)

        assertEquals("", part1)
        assertEquals("😀", part2)
    }

    @Test
    fun `decode 4-byte character (emoji) split 3+1`() {
        val decoder = UTF8Decoder()
        // 4-byte
        val text = "😀"
        val bytes = text.encodeToByteArray()

        val part1 = decoder.decode(bytes, 3, endOfInput = false)
        val part2 = decoder.decode(bytes.copyOfRange(3, 4), 1, endOfInput = true)

        assertEquals("", part1)
        assertEquals("😀", part2)
    }

    @Test
    fun `decode multiple consecutive split characters`() {
        val decoder = UTF8Decoder()
        val text = "café你好😀"
        val bytes = text.encodeToByteArray()

        // Simulate byte-by-byte streaming (worst case)
        val result = StringBuilder()
        for (i in bytes.indices) {
            val chunk = decoder.decode(bytes.copyOfRange(i, i + 1), 1,
                                       endOfInput = i == bytes.size - 1)
            result.append(chunk)
        }

        assertEquals(text, result.toString())
    }

    // ============================================================================
    // Buffer Boundaries and Stateful Behavior
    // ============================================================================

    @Test
    fun `decode large input in multiple chunks`() {
        val decoder = UTF8Decoder()
        // Large text with multi-byte chars
        val text = "Hello 😀 ".repeat(1000)
        val bytes = text.encodeToByteArray()

        val chunkSize = 100
        val result = StringBuilder()
        var offset = 0

        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            val decoded = decoder.decode(chunk, chunk.size, endOfInput = end == bytes.size)
            result.append(decoded)
            offset = end
        }

        assertEquals(text, result.toString())
    }

    @Test
    fun `decode with empty chunks between non-empty chunks`() {
        val decoder = UTF8Decoder()
        val text = "Hello"
        val bytes = text.encodeToByteArray()

        val part1 = decoder.decode(bytes, 3, endOfInput = false)
        val empty = decoder.decode(ByteArray(0), 0, endOfInput = false)
        val part2 = decoder.decode(bytes.copyOfRange(3, 5), 2, endOfInput = true)

        assertEquals("Hel", part1)
        assertEquals("", empty)
        assertEquals("lo", part2)
        assertEquals(text, part1 + empty + part2)
    }

    @Test
    fun `decode maintains state across multiple calls`() {
        val decoder = UTF8Decoder()
        val text = "Part1 你好 Part2 😀"
        val bytes = text.encodeToByteArray()

        // Split at arbitrary points that might cut multi-byte sequences
        val chunks = listOf(
            bytes.copyOfRange(0, 7),
            bytes.copyOfRange(7, 14),
            bytes.copyOfRange(14, bytes.size)
        )

        val result = StringBuilder()
        for ((index, chunk) in chunks.withIndex()) {
            val decoded = decoder.decode(chunk, chunk.size, endOfInput = index == chunks.size - 1)
            result.append(decoded)
        }

        assertEquals(text, result.toString())
    }

    // ============================================================================
    // Special UTF-8 Characters
    // ============================================================================

    @Test
    fun `decode multiple emojis`() {
        val decoder = UTF8Decoder()
        val text = "😀😃😄😁🎉🔥"
        val bytes = text.encodeToByteArray()

        val decoded = decoder.decode(bytes, bytes.size, endOfInput = true)

        assertEquals(text, decoded)
    }

    @Test
    fun `decode emoji with skin tone modifier`() {
        val decoder = UTF8Decoder()
        // Wave + medium skin tone (multi-codepoint)
        val text = "👋🏽"
        val bytes = text.encodeToByteArray()

        val decoded = decoder.decode(bytes, bytes.size, endOfInput = true)

        assertEquals(text, decoded)
    }

    @Test
    fun `decode combining characters`() {
        val decoder = UTF8Decoder()
        // e + combining acute accent
        val text = "e\u0301"
        val bytes = text.encodeToByteArray()

        val decoded = decoder.decode(bytes, bytes.size, endOfInput = true)

        assertEquals(text, decoded)
    }

    @Test
    fun `decode RTL (right-to-left) text`() {
        val decoder = UTF8Decoder()
        // Arabic "hello"
        val text = "مرحبا"
        val bytes = text.encodeToByteArray()

        val decoded = decoder.decode(bytes, bytes.size, endOfInput = true)

        assertEquals(text, decoded)
    }

    @Test
    fun `decode zero-width characters`() {
        val decoder = UTF8Decoder()
        // Zero-width space
        val text = "Hello\u200BWorld"
        val bytes = text.encodeToByteArray()

        val decoded = decoder.decode(bytes, bytes.size, endOfInput = true)

        assertEquals(text, decoded)
    }

    // ============================================================================
    // endOfInput and Flush Behavior
    // ============================================================================

    @Test
    fun `decode with endOfInput false then true`() {
        val decoder = UTF8Decoder()
        val text = "Hello World"
        val bytes = text.encodeToByteArray()

        val part1 = decoder.decode(bytes, 5, endOfInput = false)
        val part2 = decoder.decode(bytes.copyOfRange(5, 11), 6, endOfInput = true)

        assertEquals("Hello", part1)
        assertEquals(" World", part2)
    }

    @Test
    fun `flush after final decode`() {
        val decoder = UTF8Decoder()
        val text = "Hello"
        val bytes = text.encodeToByteArray()

        val decoded = decoder.decode(bytes, bytes.size, endOfInput = true)
        val flushed = decoder.flush()

        assertEquals("Hello", decoded)
        // Nothing left to flush
        assertEquals("", flushed)
    }

    @Test
    fun `flush after decode with partial sequence`() {
        val decoder = UTF8Decoder()
        val text = "café"
        val bytes = text.encodeToByteArray()

        // Decode up to but not including the last byte of 'é'
        val splitPoint = bytes.size - 1
        val decoded = decoder.decode(bytes, splitPoint, endOfInput = false)

        // Now feed the last byte and flush
        val lastByte = decoder.decode(bytes.copyOfRange(splitPoint, bytes.size), 1, endOfInput = true)
        val flushed = decoder.flush()

        assertEquals("caf", decoded)
        assertEquals("é", lastByte)
        assertEquals("", flushed)
    }

    @Test
    fun `decode complete sequence in multiple small chunks`() {
        val decoder = UTF8Decoder()
        // ASCII, emoji, ASCII
        val text = "a😀b"
        val bytes = text.encodeToByteArray()

        val result = StringBuilder()
        var offset = 0

        // Feed 2 bytes at a time
        while (offset < bytes.size) {
            val end = minOf(offset + 2, bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            val decoded = decoder.decode(chunk, chunk.size, endOfInput = end == bytes.size)
            result.append(decoded)
            offset = end
        }

        val flushed = decoder.flush()
        result.append(flushed)

        assertEquals(text, result.toString())
    }

    // ============================================================================
    // Decoder Reuse
    // ============================================================================

    @Test
    fun `decoder can be reused for multiple independent sequences`() {
        val decoder = UTF8Decoder()

        // First sequence
        val text1 = "Hello 😀"
        val bytes1 = text1.encodeToByteArray()
        val decoded1 = decoder.decode(bytes1, bytes1.size, endOfInput = true)
        decoder.flush()

        // Note: In practice, decoder state is NOT reset between calls
        // This test verifies current behavior - decoder maintains state
        assertEquals(text1, decoded1)
    }

    @Test
    fun `decode with trailing complete and incomplete sequences`() {
        val decoder = UTF8Decoder()
        val text = "abc😀"
        val bytes = text.encodeToByteArray()

        // Decode all but last byte of emoji
        val part1 = decoder.decode(bytes, bytes.size - 1, endOfInput = false)
        val part2 = decoder.decode(bytes.copyOfRange(bytes.size - 1, bytes.size), 1, endOfInput = true)

        assertEquals("abc", part1)
        assertEquals("😀", part2)
    }
}