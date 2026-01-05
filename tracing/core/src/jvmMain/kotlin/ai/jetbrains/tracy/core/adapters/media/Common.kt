package ai.jetbrains.tracy.core.adapters.media

import ai.jetbrains.tracy.core.common.DataUrl
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import ai.jetbrains.tracy.core.common.isValidUrl


enum class SupportedMediaContentTypes(val type: String) {
    BASE64("base64"),
    URL("url"),
}

/**
 * Attribute IDs for uploadable media contents.
 */
class UploadableMediaContentAttributeKeys private constructor(private val index: Int) {
    companion object {
        const val KEY_NAME_PREFIX = "custom.uploadableMediaContent"
        fun forIndex(index: Int) = UploadableMediaContentAttributeKeys(index)
    }

    val type: AttributeKey<String>
        get() = AttributeKey.stringKey("$KEY_NAME_PREFIX.$index.type")

    val url: AttributeKey<String>
        get() = AttributeKey.stringKey("$KEY_NAME_PREFIX.$index.url")

    val contentType: AttributeKey<String>
        get() = AttributeKey.stringKey("$KEY_NAME_PREFIX.$index.contentType")

    val data: AttributeKey<String>
        get() = AttributeKey.stringKey("$KEY_NAME_PREFIX.$index.data")

    val field: AttributeKey<String>
        get() = AttributeKey.stringKey("$KEY_NAME_PREFIX.$index.field")
}

private const val WARNING_URL_LENGTH_LIMIT = 200

/**
 * Installs URL-related fields for the uploadable media content into the span
 *
 *
 * @see UploadableMediaContentAttributeKeys
 * @return error as a string if the given [url] is invalid, otherwise `Unit`
 */
fun setUrlAttributes(
    span: Span,
    field: String,
    index: Int,
    url: String,
): Result<Unit> {
    if (!url.isValidUrl()) {
        return Result.failure(IllegalArgumentException("Expected a valid URL, received: $url"))
    }
    val keys = UploadableMediaContentAttributeKeys.forIndex(index)

    span.setAttribute(keys.type, SupportedMediaContentTypes.URL.type)
    span.setAttribute(keys.field, field)
    span.setAttribute(keys.url, url)

    return Result.success(Unit)
}

/**
 * Sets base64-related attributes into the span, ensuring that [dataUrl]
 * contains the data in the base64-encoded format.
 *
 * @see UploadableMediaContentAttributeKeys
 * @return error as a string if the given [dataUrl] is not base64-encoded, otherwise `Unit`
 */
fun setDataUrlAttributes(
    span: Span,
    field: String,
    index: Int,
    dataUrl: DataUrl
): Result<Unit> {
    if (!dataUrl.base64) {
        val str = dataUrl.asString()
        val trimmed = if (str.length < WARNING_URL_LENGTH_LIMIT) str
        else str.substring(0, WARNING_URL_LENGTH_LIMIT) + "..."
        return Result.failure(
            IllegalArgumentException(
                "Expect base64 encoding for the data url, received '$trimmed'"
            )
        )
    }

    val keys = UploadableMediaContentAttributeKeys.forIndex(index)

    span.setAttribute(keys.type, SupportedMediaContentTypes.BASE64.type)
    span.setAttribute(keys.field, field)
    span.setAttribute(keys.contentType, dataUrl.mediaType)
    span.setAttribute(keys.data, dataUrl.data)

    return Result.success(Unit)
}