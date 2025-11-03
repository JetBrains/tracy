package ai.dev.kit.common

import io.ktor.http.ContentType
import org.junit.jupiter.api.Test
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParsingTest {
    @Test
    fun `parseDataUrl should handle various valid data URLs`() {
        val testCases = listOf(
            // basic cases
            TestCase(
                input = "data:,Hello%2C%20World%21",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    false,
                    "Hello%2C%20World%21")
            ),
            TestCase(
                input = "data:text/plain,Hello",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    false,
                    "Hello"
                )
            ),
            TestCase(
                input = "data:text/html,<h1>Hello</h1>",
                expected = DataUrl(
                    "text/html",
                    emptyMap(),
                    false,
                    "<h1>Hello</h1>"
                )
            ),

            // with charset
            TestCase(
                input = "data:text/plain;charset=UTF-8,Hello",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "UTF-8"),
                    false,
                    "Hello"
                )
            ),
            TestCase(
                input = "data:text/html;charset=ISO-8859-1,<h1>Hëllo</h1>",
                expected = DataUrl(
                    "text/html",
                    mapOf("charset" to "ISO-8859-1"),
                    false,
                    "<h1>Hëllo</h1>"
                )
            ),

            // base64 encoded
            TestCase(
                input = "data:text/plain;base64,SGVsbG8gV29ybGQ=",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    true,
                    "SGVsbG8gV29ybGQ=",
                )
            ),
            TestCase(
                input = "data:image/png;base64,iVBORw0KGgoAAAANS",
                expected = DataUrl(
                    "image/png",
                    emptyMap(),
                    true,
                    "iVBORw0KGgoAAAANS"
                )
            ),
            TestCase(
                input = "data:;base64,SGVsbG8=",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    true,
                    "SGVsbG8="
                )
            ),

            // multiple attributes
            TestCase(
                input = "data:text/plain;charset=UTF-8;foo=bar,Hello",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "UTF-8", "foo" to "bar"),
                    false,
                    "Hello"
                )
            ),
            TestCase(
                input = "data:application/json;charset=UTF-8;version=1.0;base64,eyJ0ZXN0IjoxfQ==",
                expected = DataUrl(
                    "application/json",
                    mapOf("charset" to "UTF-8", "version" to "1.0"),
                    true,
                    "eyJ0ZXN0IjoxfQ=="
                )
            ),

            // empty data
            TestCase(
                input = "data:,",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    false,
                    ""
                )
            ),
            TestCase(
                input = "data:text/plain,",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    false,
                    ""
                )
            ),
            TestCase(
                input = "data:image/png;base64,",
                expected = DataUrl(
                    "image/png",
                    emptyMap(),
                    true,
                    ""
                )
            ),

            // data with special characters
            TestCase(
                input = "data:text/plain,Hello,World",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    false,
                    "Hello,World"
                )
            ),
            TestCase(
                input = "data:text/plain,data:test;foo=bar",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    false,
                    "data:test;foo=bar"
                )
            ),
            TestCase(
                input = "data:text/plain,Line1\nLine2\nLine3",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    false,
                    "Line1\nLine2\nLine3"
                )
            ),

            // whitespace handling
            TestCase(
                input = "data: text/plain ,Hello",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    false,
                    "Hello"
                )
            ),
            TestCase(
                input = "data:text/plain; charset=UTF-8 ,Hello",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "UTF-8"),
                    false,
                    "Hello"
                )
            ),

            // media types
            TestCase(
                input = "data:application/json,{\"key\":\"value\"}",
                expected = DataUrl(
                    "application/json",
                    emptyMap(),
                    false,
                    "{\"key\":\"value\"}"
                )
            ),
            TestCase(
                input = "data:application/octet-stream;base64,AQIDBA==",
                expected = DataUrl(
                    "application/octet-stream",
                    emptyMap(),
                    true,
                    "AQIDBA=="
                )
            ),

            // missing media type
            TestCase(
                input = "data:;charset=UTF-8,Hello123",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "UTF-8"),
                    false,
                    "Hello123"
                )
            ),
        )

        testCases.forEach { testCase ->
            val result = testCase.input.parseDataUrl()
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
        )

        invalidUrls.forEach { url ->
            assertNull(url.parseDataUrl(), "Should return null for: $url")
        }
    }

    @Test
    fun `parseDataUrl should handle edge cases`() {
        val testCases = listOf(
            // Very long data
            TestCase(
                input = "data:text/plain," + "a".repeat(10000),
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    false,
                    "a".repeat(10000)
                )
            ),

            // Multiple equals in attribute value
            TestCase(
                input = "data:text/plain;key=val=ue,Hello",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII", "key" to "val=ue"),
                    false,
                    "Hello"
                )
            ),

            // Semicolons in data part
            TestCase(
                input = "data:text/plain;base64,SGVsbG87V29ybGQ=",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    true,
                    "SGVsbG87V29ybGQ="
                )
            ),

            // Equals sign in data part
            TestCase(
                input = "data:text/plain,a=b",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    false,
                    "a=b"
                )
            ),

            // Base64 padding
            TestCase(
                input = "data:text/plain;base64,SGVsbG8===",
                expected = DataUrl(
                    "text/plain",
                    mapOf("charset" to "US-ASCII"),
                    true,
                    "SGVsbG8==="
                )
            )
        )

        testCases.forEach { testCase ->
            val result = testCase.input.parseDataUrl()
            assertEquals(testCase.expected, result, "Failed for input: ${testCase.input}")
        }
    }

    @Test
    fun `asString should reconstruct data URL correctly`() {
        val testCases = listOf(
            // Basic cases
            DataUrl("text/plain", mapOf("charset" to "US-ASCII"), false, "Hello") to
                    "data:text/plain;charset=US-ASCII,Hello",
            DataUrl("text/html", emptyMap(), false, "<h1>Test</h1>") to
                    "data:text/html,<h1>Test</h1>",

            // With base64
            DataUrl("image/png", emptyMap(), true, "iVBORw0KGg") to
                    "data:image/png;base64,iVBORw0KGg",
            DataUrl("text/plain", mapOf("charset" to "UTF-8"), true, "SGVsbG8=") to
                    "data:text/plain;charset=UTF-8;base64,SGVsbG8=",

            // Multiple headers
            DataUrl("application/json", mapOf("charset" to "UTF-8", "version" to "1.0"), false, "{}") to
                    "data:application/json;charset=UTF-8;version=1.0,{}",

            // Empty data
            DataUrl("text/plain", emptyMap(), false, "") to
                    "data:text/plain,",
            DataUrl("image/png", emptyMap(), true, "") to
                    "data:image/png;base64,"
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
        )

        originalUrls.forEach { url ->
            val parsed = url.parseDataUrl()!!
            val reconstructed = parsed.asString()
            val reparsed = reconstructed.parseDataUrl()
            assertEquals(parsed, reparsed, "Round-trip failed for: $url")
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
        val input: String,
        val expected: DataUrl
    )
}
