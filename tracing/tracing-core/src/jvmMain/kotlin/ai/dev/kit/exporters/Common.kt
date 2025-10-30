package ai.dev.kit.exporters

import ai.dev.kit.common.DataUrl
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import ai.dev.kit.common.Result
import ai.dev.kit.common.isValidUrl


enum class SupportedMediaContentTypes(val type: String) {
    BASE64("base64"),
    URL("url"),
}

/**
 * Attribute IDs for uploadable media contents.
 */
object UploadableMediaContentAttributeKeys {
    private const val KEY_NAME_PREFIX = "custom.uploadableMediaContent"

    fun type(index: Int): AttributeKey<String> {
        return AttributeKey.stringKey(
            "$KEY_NAME_PREFIX.$index.type")
    }

    fun url(index: Int): AttributeKey<String> {
        return AttributeKey.stringKey(
            "$KEY_NAME_PREFIX.$index.url")
    }

    fun contentType(index: Int): AttributeKey<String> {
        return AttributeKey.stringKey(
            "$KEY_NAME_PREFIX.$index.contentType")
    }

    fun data(index: Int): AttributeKey<String> {
        return AttributeKey.stringKey(
            "$KEY_NAME_PREFIX.$index.data")
    }

    fun field(index: Int): AttributeKey<String> {
        return AttributeKey.stringKey(
            "$KEY_NAME_PREFIX.$index.field")
    }
}

private const val WARNING_URL_LENGTH_LIMIT = 200

/**
 * Installs URL-related fields for the uploadable media content into the span
 *
 *
 * @see UploadableMediaContentAttributeKeys
 * @return error as a string if the given [url] is not invalid, otherwise `Unit`
 */
fun setUrlAttributes(
    span: Span,
    field: String,
    index: Int,
    url: String,
): Result<Unit> {
    if (!url.isValidUrl()) {
        return Result.Error("Expected a valid URL, received: $url")
    }

    val typeKey = UploadableMediaContentAttributeKeys.type(index)
    val fieldKey = UploadableMediaContentAttributeKeys.field(index)
    val urlKey = UploadableMediaContentAttributeKeys.url(index)

    span.setAttribute(typeKey, SupportedMediaContentTypes.URL.type)
    span.setAttribute(fieldKey, field)
    span.setAttribute(urlKey, url)

    return Result.Success(Unit)
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
        return Result.Error("Expect base64 encoding for the data url, received '$trimmed'")
    }

    val typeKey = UploadableMediaContentAttributeKeys.type(index)
    val fieldKey = UploadableMediaContentAttributeKeys.field(index)
    val contentTypeKey = UploadableMediaContentAttributeKeys.contentType(index)
    val dataKey = UploadableMediaContentAttributeKeys.data(index)

    span.setAttribute(typeKey, SupportedMediaContentTypes.BASE64.type)
    span.setAttribute(fieldKey, field)
    span.setAttribute(contentTypeKey, dataUrl.mediaType)
    span.setAttribute(dataKey, dataUrl.data)

    return Result.Success(Unit)
}