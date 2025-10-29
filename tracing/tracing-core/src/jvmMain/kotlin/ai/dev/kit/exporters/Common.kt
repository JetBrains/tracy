package ai.dev.kit.exporters

import io.opentelemetry.api.common.AttributeKey


enum class SupportedMediaContentTypes(val type: String) {
    BASE64("base64"),
    URL("url"),
}

/**
 * Attribute IDs for uploadable media contents.
 */
object UploadableMediaContentAttributeKeys {
    private const val UPLOADABLE_MEDIA_CONTENT_ATTRIBUTE_NAME_PREFIX =
        "custom.uploadableMediaContent"

    fun type(index: Int): AttributeKey<String> {
        return AttributeKey.stringKey(
            "$UPLOADABLE_MEDIA_CONTENT_ATTRIBUTE_NAME_PREFIX.$index.type")
    }

    fun url(index: Int): AttributeKey<String> {
        return AttributeKey.stringKey(
            "$UPLOADABLE_MEDIA_CONTENT_ATTRIBUTE_NAME_PREFIX.$index.url")
    }

    fun contentType(index: Int): AttributeKey<String> {
        return AttributeKey.stringKey(
            "$UPLOADABLE_MEDIA_CONTENT_ATTRIBUTE_NAME_PREFIX.$index.contentType")
    }

    fun data(index: Int): AttributeKey<String> {
        return AttributeKey.stringKey(
            "$UPLOADABLE_MEDIA_CONTENT_ATTRIBUTE_NAME_PREFIX.$index.data")
    }

    fun field(index: Int): AttributeKey<String> {
        return AttributeKey.stringKey(
            "$UPLOADABLE_MEDIA_CONTENT_ATTRIBUTE_NAME_PREFIX.$index.field")
    }
}