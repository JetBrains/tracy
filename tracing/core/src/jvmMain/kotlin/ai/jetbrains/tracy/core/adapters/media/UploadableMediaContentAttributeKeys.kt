package ai.jetbrains.tracy.core.adapters.media

import io.opentelemetry.api.common.AttributeKey

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
