/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.common

import org.jetbrains.ai.tracy.core.adapters.media.DataUrl
import org.jetbrains.ai.tracy.core.adapters.media.DataUrl.Companion.parseInlineDataUrl
import org.jetbrains.ai.tracy.core.adapters.media.Resource
import org.jetbrains.ai.tracy.core.adapters.media.isValidUrl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataUrlTest {
    @Test
    fun `parseDataUrl should handle various valid data URLs`() {
        val testCases = listOf(
            // basic cases
            TestCase(
                input = Resource.InlineDataUrl("data:,Hello%2C%20World%21"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    false,
                    "Hello%2C%20World%21"
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain,Hello"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    false,
                    "Hello"
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:text/html,<h1>Hello</h1>"),
                expected = DataUrl(
                    "text/html",
                    // defaulted charset
                    mapOf("charset" to "US-ASCII").toParameters(),
                    false,
                    "<h1>Hello</h1>"
                )
            ),

            // with charset
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain;charset=UTF-8,Hello"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "UTF-8").toParameters(),
                    false,
                    "Hello"
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:text/html;charset=ISO-8859-1,<h1>Hëllo</h1>"),
                expected = DataUrl(
                    "text/html",
                    mapOf("charset" to "ISO-8859-1").toParameters(),
                    false,
                    "<h1>Hëllo</h1>"
                )
            ),

            // base64 encoded
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain;base64,SGVsbG8gV29ybGQ="),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    true,
                    "SGVsbG8gV29ybGQ=",
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:image/png;base64,iVBORw0KGgoAAAANS"),
                expected = DataUrl(
                    "image/png",
                    emptyMap(),
                    true,
                    "iVBORw0KGgoAAAANS"
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:;base64,SGVsbG8="),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    true,
                    "SGVsbG8="
                )
            ),

            // multiple attributes
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain;charset=UTF-8;foo=bar,Hello"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "UTF-8", "foo" to "bar").toParameters(),
                    false,
                    "Hello"
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:application/json;charset=UTF-8;version=1.0;base64,eyJ0ZXN0IjoxfQ=="),
                expected = DataUrl(
                    "application/json",
                    mapOf("charset" to "UTF-8", "version" to "1.0").toParameters(),
                    true,
                    "eyJ0ZXN0IjoxfQ=="
                )
            ),

            // empty data
            TestCase(
                input = Resource.InlineDataUrl("data:,"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    false,
                    ""
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain,"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    false,
                    ""
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:image/png;base64,"),
                expected = DataUrl(
                    "image/png",
                    emptyMap(),
                    true,
                    ""
                )
            ),

            // data with special characters
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain,Hello,World"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    false,
                    "Hello,World"
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain,data:test;foo=bar"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    false,
                    "data:test;foo=bar"
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain,Line1\nLine2\nLine3"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    false,
                    "Line1\nLine2\nLine3"
                )
            ),

            // whitespace handling
            TestCase(
                input = Resource.InlineDataUrl("data: text/plain ,Hello"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    false,
                    "Hello"
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain; charset=UTF-8 ,Hello"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "UTF-8").toParameters(),
                    false,
                    "Hello"
                )
            ),

            // media types
            TestCase(
                input = Resource.InlineDataUrl("data:application/json,{\"key\":\"value\"}"),
                expected = DataUrl(
                    "application/json",
                    emptyMap(),
                    false,
                    "{\"key\":\"value\"}"
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:application/octet-stream;base64,AQIDBA=="),
                expected = DataUrl(
                    "application/octet-stream",
                    emptyMap(),
                    true,
                    "AQIDBA=="
                )
            ),

            // missing media type
            TestCase(
                input = Resource.InlineDataUrl("data:;charset=UTF-8,Hello123"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "UTF-8").toParameters(),
                    false,
                    "Hello123"
                )
            ),
        )

        testCases.forEach { testCase ->
            val result = testCase.input.parseInlineDataUrl()
            assertEquals(testCase.expected, result, "Failed for input: ${testCase.input}")
        }
    }

    @Test
    fun `parseDataUrl should return null for invalid data URLs`() {
        val invalidUrls = listOf(
            "",
            "not-a-data-url",
            "http://example.com",
            "data",
            "data:",
            "Data:,test",  // capital D
            "data:text/plain",  // missing comma
            "data text/plain,Hello",  // missing colon
        ).map { Resource.InlineDataUrl(it) }

        invalidUrls.forEach { url ->
            assertNull(url.parseInlineDataUrl(), "Should return null for: $url")
        }
    }

    @Test
    fun `parseDataUrl should handle edge cases`() {
        val testCases = listOf(
            // Very long data
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain," + "a".repeat(10000)),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    false,
                    "a".repeat(10000)
                )
            ),

            // Multiple equals in attribute value
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain;key=val=ue,Hello"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII", "key" to "val=ue").toParameters(),
                    false,
                    "Hello"
                )
            ),

            // Semicolons in data part
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain;base64,SGVsbG87V29ybGQ="),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    true,
                    "SGVsbG87V29ybGQ="
                )
            ),

            // Equals sign in data part
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain,a=b"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    false,
                    "a=b"
                )
            ),

            // Base64 padding
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain;base64,SGVsbG8==="),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII").toParameters(),
                    true,
                    "SGVsbG8==="
                )
            )
        )

        testCases.forEach { testCase ->
            val result = testCase.input.parseInlineDataUrl()
            assertEquals(testCase.expected, result, "Failed for input: ${testCase.input}")
        }
    }

    @Test
    fun `asString should reconstruct data URL correctly`() {
        val testCases = listOf(
            // Basic cases
            DataUrl(
                "text/plain",
                mapOf("charset" to "US-ASCII").toParameters(),
                false,
                "Hello"
            ) to "data:text/plain;charset=US-ASCII,Hello",

            DataUrl(
                "text/html",
                emptyMap(),
                false,
                "<h1>Test</h1>"
            ) to "data:text/html,<h1>Test</h1>",

            // With base64
            DataUrl(
                "image/png",
                emptyMap(),
                true,
                "iVBORw0KGg"
            ) to "data:image/png;base64,iVBORw0KGg",

            DataUrl(
                "text/plain",
                mapOf("charset" to "UTF-8").toParameters(),
                true,
                "SGVsbG8="
            ) to "data:text/plain;charset=UTF-8;base64,SGVsbG8=",

            // Multiple headers
            DataUrl(
                "application/json",
                mapOf("charset" to "UTF-8", "version" to "1.0").toParameters(),
                false,
                "{}"
            ) to "data:application/json;charset=UTF-8;version=1.0,{}",

            // Empty data
            DataUrl(
                "text/plain",
                emptyMap(),
                false,
                ""
            ) to "data:text/plain,",

            DataUrl(
                "image/png",
                emptyMap(),
                true,
                ""
            ) to "data:image/png;base64,"
        )

        testCases.forEach { (dataUrl, expected) ->
            assertEquals(expected, dataUrl.asString(), "Failed for DataUrl: $dataUrl")
        }
    }

    @Test
    fun `asString should be reversible with parseDataUrl`() {
        val originalUrls = listOf(
            "data:text/plain;charset=UTF-8,Hello",
            "data:image/png;base64,iVBORw0KGgoAAAANS",
            "data:application/json;charset=UTF-8;version=1.0,{}",
            "data:text/html,<h1>Test</h1>",
            "data:,Hello%20World"
        ).map { Resource.InlineDataUrl(it) }

        originalUrls.forEach { url ->
            val parsed = url.parseInlineDataUrl()!!
            val reconstructed = Resource.InlineDataUrl(parsed.asString())
            val reparsed = reconstructed.parseInlineDataUrl()
            assertEquals(parsed, reparsed, "Round-trip failed for: $url")
        }
    }

    @Test
    fun `parseDataUrl should handle RFC 2045 case sensitivity rules`() {
        val testCases = listOf(
            // Test 1: Case-insensitive attribute names
            // Per RFC 2045: "parameter names (keys) as defined are case-insensitive"
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain;Charset=UTF-8,Hello"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "UTF-8").toParameters(),
                    false,
                    "Hello"
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain;CHARSET=UTF-8,Hello"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "UTF-8").toParameters(),
                    false,
                    "Hello"
                )
            ),

            // Test 2: Case-sensitive attribute values
            // Per RFC 2045: "parameter values are normally case-sensitive"
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain;charset=UTF-8,Hello"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "UTF-8").toParameters(),
                    false,
                    "Hello"
                )
            ),
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain;foo=MixedCaseValue,Hello"),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII", "foo" to "MixedCaseValue").toParameters(),
                    false,
                    "Hello"
                )
            ),

            // Test 3: Duplicate attribute names with different cases merge into same key
            // Per RFC 2045: Different cases of same parameter name should be treated as same parameter
            TestCase(
                input = Resource.InlineDataUrl("data:text/plain;foo=value1;FOO=value2;Foo=value3,Hello"),
                expected = DataUrl(
                    "text/plain",
                    mapOf(
                        "charset" to listOf("US-ASCII"),
                        "foo" to listOf("value1", "value2", "value3")
                    ),
                    false,
                    "Hello"
                )
            ),
        )

        testCases.forEach { testCase ->
            val result = testCase.input.parseInlineDataUrl()
            assertEquals(testCase.expected, result, "Failed for input: ${testCase.input}")
        }
    }

    @Test
    fun `isValidUrl should return true for valid URLs`() {
        val validUrls = listOf(
            "http://example.com",
            "https://example.com",
            "https://example.com:8080",
            "https://example.com/path",
            "https://example.com/path?query=value",
            "https://example.com/path?query=value#fragment",
            "https://user:password@example.com",
            "ftp://ftp.example.com",
            "file:///path/to/file",
            "https://subdomain.example.com",
            "https://example.com:8080/path?query=value#fragment",
            "http://192.168.1.1",
            "http://[2001:db8::1]"
        )

        validUrls.forEach { url ->
            assertTrue(url.isValidUrl(), "Should be valid: $url")
        }
    }

    @Test
    fun `isValidUrl should return false for invalid URLs`() {
        val invalidUrls = listOf(
            "",
            "not a url",
            "htp://example.com",
            "://example.com",
            "example.com",
            "javascript:alert('xss')",
        )

        invalidUrls.forEach { url ->
            assertFalse(url.isValidUrl(), "Should be invalid: $url")
        }
    }

    @Test
    fun `isValidUrl should handle edge cases`() {
        val testCases = listOf(
            // Very long URL
            "https://example.com/" + "a".repeat(2000) to true,

            // URL with special characters
            "https://example.com/path%20with%20spaces" to true,
            "https://example.com/path?param=value&other=123" to true,

            // International domain names (depending on URL parser)
            "https://例え.jp" to true,

            // Empty components
            "https://example.com?" to true,
            "https://example.com#" to true
        )

        testCases.forEach { (url, expected) ->
            assertEquals(expected, url.isValidUrl(), "Failed for URL: $url")
        }
    }

    private data class TestCase(
        val input: Resource.InlineDataUrl,
        val expected: DataUrl
    )
}

/**
 * Simply wraps entries' values into a list.
 */
private fun Map<String, String>.toParameters(): Map<String, List<String>> {
    return this.mapValues { listOf(it.value) }
}
