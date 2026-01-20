package ai.jetbrains.tracy.core.http.parsers

import io.ktor.http.ContentType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.Base64

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
            ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        }

        val parser = MultipartFormDataParser()
        val data = assertDoesNotThrow {
            parser.parse(contentType, body.toByteArray())
        }

        Assertions.assertEquals(
            3, data.parts.size,
            "Expected 3 parts in the parsed multipart form data"
        )
        Assertions.assertEquals(
            prompt,
            data.parts.first { it.name == "prompt" }.content.decodeToString()
        )
        Assertions.assertEquals(
            completion,
            data.parts.first { it.name == "completion" }.content.decodeToString()
        )
        Assertions.assertTrue(
            data.parts.first { it.name == "empty-value" }.content.decodeToString().isEmpty()
        )
    }

    @Test
    fun `test parser throws incorrect content type`() = runTest {
        val body = "This is NOT multipart/form-data"
        val contentType = ContentType.Companion.parse("text/plain")

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

        val contentType = assertDoesNotThrow { ContentType.Companion.parse("multipart/form-data") }

        val parser = MultipartFormDataParser()
        assertThrows<IllegalArgumentException> {
            val data = parser.parse(contentType, body.toByteArray())
        }
    }

    @Test
    fun `test parser handles file uploads with filename`() = runTest {
        val boundary = "FileBoundary"
        val fileContent = "This is file content"
        val fileName = "test.txt"

        val body = """
            --$boundary
            Content-Disposition: form-data; name="file"; filename="$fileName"
            Content-Type: text/plain

            $fileContent
            --$boundary--
        """.trimIndent()

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, body.toByteArray())

        Assertions.assertEquals(1, data.parts.size)
        val part = data.parts[0]
        Assertions.assertEquals("file", part.name)
        Assertions.assertEquals(fileName, part.filename)
        Assertions.assertEquals(fileContent, part.content.decodeToString())
        Assertions.assertEquals(ContentType.Text.Plain, part.contentType)
    }

    @Test
    fun `test parser handles unquoted field names and filenames`() = runTest {
        val boundary = "UnquotedBoundary"
        val body = """
            --$boundary
            Content-Disposition: form-data; name=fieldname; filename=file.txt
            Content-Type: text/plain

            content
            --$boundary--
        """.trimIndent()

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, body.toByteArray())

        Assertions.assertEquals(1, data.parts.size)
        Assertions.assertEquals("fieldname", data.parts[0].name)
        Assertions.assertEquals("file.txt", data.parts[0].filename)
    }

    @Test
    fun `test parser handles binary file content`() = runTest {
        val boundary = "BinaryBoundary"
        val binaryData = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte(), 0xFE.toByte())

        // manually construct a multipart body with binary data
        val header = """
            --$boundary
            Content-Disposition: form-data; name="binfile"; filename="binary.bin"
            Content-Type: application/octet-stream


        """.trimIndent().replace("\n", "\r\n")

        val footer = "\r\n--$boundary--"

        val bodyBytes = header.toByteArray() + binaryData + footer.toByteArray()

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, bodyBytes)

        Assertions.assertEquals(1, data.parts.size)
        val part = data.parts[0]
        Assertions.assertEquals("binfile", part.name)
        Assertions.assertEquals("binary.bin", part.filename)
        Assertions.assertEquals(binaryData.toList(), part.content.toList())
    }

    @Test
    fun `test parser handles parts without Content-Type header`() = runTest {
        val boundary = "NoContentTypeBoundary"
        val body = """
            --$boundary
            Content-Disposition: form-data; name="textfield"

            some text value
            --$boundary--
        """.trimIndent()

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, body.toByteArray())

        Assertions.assertEquals(1, data.parts.size)
        Assertions.assertEquals("textfield", data.parts[0].name)
        Assertions.assertEquals(null, data.parts[0].contentType)
        Assertions.assertEquals("some text value", data.parts[0].content.decodeToString())
    }

    @Test
    fun `test parser handles multiple files in single request`() = runTest {
        val boundary = "MultiFileBoundary"
        val body = """
            --$boundary
            Content-Disposition: form-data; name="file1"; filename="file1.txt"
            Content-Type: text/plain

            content of file 1
            --$boundary
            Content-Disposition: form-data; name="file2"; filename="file2.json"
            Content-Type: application/json

            {"key": "value"}
            --$boundary
            Content-Disposition: form-data; name="description"
            Content-Type: text/plain

            Files description
            --$boundary--
        """.trimIndent()

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, body.toByteArray())

        Assertions.assertEquals(3, data.parts.size)

        val file1 = data.parts.first { it.name == "file1" }
        Assertions.assertEquals("file1.txt", file1.filename)
        Assertions.assertEquals("content of file 1", file1.content.decodeToString())

        val file2 = data.parts.first { it.name == "file2" }
        Assertions.assertEquals("file2.json", file2.filename)
        Assertions.assertEquals("{\"key\": \"value\"}", file2.content.decodeToString())

        val description = data.parts.first { it.name == "description" }
        Assertions.assertEquals(null, description.filename)
        Assertions.assertEquals("Files description", description.content.decodeToString())
    }

    @Test
    fun `test parser handles boundary-like content in field values`() = runTest {
        val boundary = "ContentBoundary"
        val content = "--FakeBoundary\nThis looks like a boundary but isn't"

        val body = """
            |--$boundary
            |Content-Disposition: form-data; name="trickycontent"
            |Content-Type: text/plain
            |
            |$content
            |--$boundary--
        """.trimMargin()

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, body.toByteArray())

        Assertions.assertEquals(1, data.parts.size)
        Assertions.assertEquals(content, data.parts[0].content.decodeToString())
    }

    @Test
    fun `test parser handles different charset encodings`() = runTest {
        val boundary = "CharsetBoundary"
        val utf8Text = "Hello 世界 🌍"

        val body = """
            --$boundary
            Content-Disposition: form-data; name="utf8field"
            Content-Type: text/plain; charset=utf-8

            $utf8Text
            --$boundary--
        """.trimIndent()

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, body.toByteArray())

        Assertions.assertEquals(1, data.parts.size)
        Assertions.assertEquals(utf8Text, data.parts[0].content.decodeToString())
    }

    @Test
    fun `test parser handles invalid Content-Type in part gracefully`() = runTest {
        val boundary = "InvalidTypeBoundary"
        val body = """
            --$boundary
            Content-Disposition: form-data; name="field"
            Content-Type: this-is-not-a-valid-content-type!!!

            content
            --$boundary--
        """.trimIndent()

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, body.toByteArray())

        // Parser should handle this gracefully, setting contentType to null
        Assertions.assertEquals(1, data.parts.size)
        Assertions.assertEquals("field", data.parts[0].name)
        Assertions.assertEquals(null, data.parts[0].contentType)
        Assertions.assertEquals("content", data.parts[0].content.decodeToString())
    }

    @Test
    fun `test parser handles empty multipart body`() = runTest {
        val boundary = "EmptyBoundary"
        val body = "--$boundary--"

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, body.toByteArray())

        Assertions.assertEquals(0, data.parts.size)
    }

    @Test
    fun `test parser handles whitespace in Content-Disposition parameters`() = runTest {
        val boundary = "WhitespaceBoundary"
        val body = """
            --$boundary
            Content-Disposition: form-data;  name="field1"  ;  filename="file.txt"
            Content-Type: text/plain

            content
            --$boundary--
        """.trimIndent()

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, body.toByteArray())

        Assertions.assertEquals(1, data.parts.size)
        val part = data.parts[0]

        Assertions.assertEquals("field1", part.name)
        Assertions.assertEquals("file.txt", part.filename)
        Assertions.assertEquals("content", part.content.decodeToString())
    }

    @Test
    fun `test parser does not extract Content-Transfer-Encoding`() = runTest {
        // capturing `Content-Transfer-Encoding` header.
        // This header is important for `multipart/form-data` as it specifies how the body content
        // is encoded (e.g., "base64", "quoted-printable", "binary", "7bit", "8bit")
        val boundary = "EncodingBoundary"
        val content = "Hello World!"
        val base64Content = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))

        val body = """
            --$boundary
            Content-Disposition: form-data; name="encodedfile"; filename="encoded.txt"
            Content-Type: text/plain
            Content-Transfer-Encoding: base64

            $base64Content
            --$boundary--
        """.trimIndent()

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, body.toByteArray())

        Assertions.assertEquals(1, data.parts.size)
        val part = data.parts[0]

        // parser should automatically parse the field according to their MIME types
        Assertions.assertEquals(content, part.content.decodeToString())

        Assertions.assertTrue(part.headers.contains("Content-Transfer-Encoding"))
        Assertions.assertEquals("base64", part.headers["Content-Transfer-Encoding"])
    }

    @Test
    fun `test parser skips parts without name attribute`() = runTest {
        // according to RFC 7578 (multipart/form-data), each part MUST have a "name" parameter
        // in the Content-Disposition header.
        val boundary = "NoNameBoundary"
        val body = """
            --$boundary
            Content-Disposition: form-data
            Content-Type: text/plain

            content without name
            --$boundary--
        """.trimIndent()

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, body.toByteArray())

        Assertions.assertTrue(data.parts.isEmpty())
    }

    @Test
    fun `test parser does not provide access to custom headers`() = runTest {
        // Some applications may include custom headers like:
        // - Content-ID
        // - Content-Description
        // - Custom application-specific headers (X-Custom-Header, etc.)

        val boundary = "CustomHeaderBoundary"
        val body = """
            --$boundary
            Content-Disposition: form-data; name="field"
            Content-Type: text/plain
            Content-ID: <custom-id-123>
            X-Custom-Header: custom-value

            content
            --$boundary--
        """.trimIndent()

        val contentType = ContentType.Companion.parse("multipart/form-data; boundary=$boundary")
        val parser = MultipartFormDataParser()
        val data = parser.parse(contentType, body.toByteArray())

        Assertions.assertEquals(1, data.parts.size)
        val part = data.parts[0]

        val expectedHeaders = mapOf("Content-ID" to "<custom-id-123>", "X-Custom-Header" to "custom-value")
        Assertions.assertEquals(expectedHeaders, part.headers)
    }
}