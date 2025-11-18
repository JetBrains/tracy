package ai.dev.kit.tracing

import ai.dev.kit.exporters.UploadableMediaContentAttributeKeys
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseAITracingTest : BaseOpenTelemetryTracingTest() {
    protected fun validateBasicTracing(url: String, model: String) {
        val traces = analyzeSpans()

        assertEquals(1, traces.size)
        val trace = traces.firstOrNull()

        assertNotNull(trace)
        assertTrue(
            url.startsWith(trace.attributes[AttributeKey.stringKey("gen_ai.api_base")].toString())
        )

        val responseModel = trace.attributes[AttributeKey.stringKey("gen_ai.response.model")]
        assertNotNull(responseModel)
        assertTrue(responseModel.startsWith(model))

        val content = trace.attributes[AttributeKey.stringKey("gen_ai.completion.0.content")]
        assertFalse(content.isNullOrEmpty())
    }

    protected fun verifyMediaContentUploadAttributes(
        span: SpanData,
        expected: List<MediaContentAttributeValues>
    ) {
        for ((index, values) in expected.withIndex()) {
            val keys = UploadableMediaContentAttributeKeys.forIndex(index)

            when (values) {
                is MediaContentAttributeValues.Data -> {
                    assertEquals(values.type, span.attributes[keys.type])
                    assertEquals(values.field, span.attributes[keys.field])
                    assertEquals(values.contentType, span.attributes[keys.contentType])
                    assertEquals(values.data, span.attributes[keys.data])
                }
                is MediaContentAttributeValues.Url -> {
                    assertEquals(values.type, span.attributes[keys.type])
                    assertEquals(values.field, span.attributes[keys.field])
                    assertEquals(values.url, span.attributes[keys.url])
                }
            }
        }
    }

    /**
     * Expects `image.jpg` image under `resources` directory of the test module.
     */
    protected fun provideImagesForUpload(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(MediaSource.File(
                filepath = "./image.jpg",
                contentType = "image/jpeg",
            )),
            Arguments.of(MediaSource.Link(CAT_IMAGE_URL))
        )
    }

    /**
     * Expects `sample.pdf` file under `resources` directory of the test module.
     */
    protected fun provideFilesForUpload(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(MediaSource.File(
                filepath = "./sample.pdf",
                contentType = "application/pdf",
            )),
            Arguments.of(MediaSource.Link(SAMPLE_PDF_FILE_URL))
        )
    }

    companion object {
        protected const val CAT_IMAGE_URL = "https://images.pexels.com/photos/104827/cat-pet-animal-domestic-104827.jpeg"
        protected const val SAMPLE_PDF_FILE_URL = "https://pdfobject.com/pdf/sample.pdf"
    }
}