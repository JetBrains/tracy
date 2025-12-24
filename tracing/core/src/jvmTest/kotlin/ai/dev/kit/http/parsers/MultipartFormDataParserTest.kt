package ai.dev.kit.http.parsers

import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class MultipartFormDataParserTest {
    @Test
    fun `test parser correctly parses string values`() = runTest {
        val boundary = "MyBoundary"

        val prompt = "my prompt content goes here"
        val completion = "my completion content goes here"

        val body = """
            --$boundary
            Content-Disposition: form-data; name="prompt"
            Content-Type: text/plain; charset=utf-8
            
            $prompt
            --$boundary
            Content-Disposition: form-data; name="completion"
            Content-Type: text/plain; charset=utf-8
            
            $completion
            --$boundary
            Content-Disposition: form-data; name="empty-value"
            Content-Type: text/plain; charset=utf-8
            
            --$boundary--
        """.trimIndent()

        val contentType = assertDoesNotThrow {
            ContentType.parse("multipart/form-data; boundary=$boundary")
        }

        val parser = MultipartFormDataParser()
        val data = assertDoesNotThrow {
            parser.parse(contentType, body.toByteArray())
        }

        assertEquals(3, data.parts.size,
            "Expected 3 parts in the parsed multipart form data")
        assertEquals(
            prompt,
            data.parts.first { it.name == "prompt" }.content.decodeToString()
        )
        assertEquals(
            completion,
            data.parts.first { it.name == "completion" }.content.decodeToString()
        )
        assertTrue(
            data.parts.first { it.name == "empty-value" }.content.decodeToString().isEmpty()
        )
    }

    @Test
    fun `test parser throws incorrect content type`() = runTest {
        val body = "This is NOT multipart/form-data"
        val contentType = ContentType.parse("text/plain")

        val parser = MultipartFormDataParser()
        assertThrows<IllegalArgumentException> {
            parser.parse(contentType, body.toByteArray())
        }
    }

    @Test
    fun `test parser throws on missing boundary in content type`() = runTest {
        val boundary = "MyMissingBoundary"
        val body = """
            --$boundary
            Content-Disposition: form-data; name="item"
            Content-Type: text/plain; charset=utf-8
            
            value
            --$boundary--
        """.trimIndent()

        val contentType = assertDoesNotThrow { ContentType.parse("multipart/form-data") }

        val parser = MultipartFormDataParser()
        assertThrows<IllegalArgumentException> {
            val data = parser.parse(contentType, body.toByteArray())
            println(data.parts)
        }
    }
}