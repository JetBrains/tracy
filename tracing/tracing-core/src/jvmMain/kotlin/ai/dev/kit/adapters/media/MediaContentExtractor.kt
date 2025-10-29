package ai.dev.kit.adapters.media

import io.opentelemetry.api.trace.Span
import kotlinx.serialization.json.JsonArray

abstract class MediaContentExtractor {
    abstract fun setUploadableContentAttributes(
        span: Span,
        field: String,
        content: JsonArray,
    )
}