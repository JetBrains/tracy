/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.test.utils

import ai.jetbrains.tracy.core.adapters.media.DataUrl
import ai.jetbrains.tracy.core.adapters.media.SupportedMediaContentTypes
import java.io.File
import java.util.*

sealed class MediaSource {
    data class File(
        val filepath: String,
        val contentType: String,
    ) : MediaSource()

    data class Link(val url: String) : MediaSource()
}

fun MediaSource.File.toDataUrl(): String {
    val encodedData = loadFileAsBase64Encoded(this.filepath)
    return "data:$contentType;base64,${encodedData}"
}

fun MediaSource.File.asDataUrl(): DataUrl {
    val encodedData = loadFileAsBase64Encoded(this.filepath)
    return DataUrl(
        mediaType = this.contentType,
        parameters = emptyMap(),
        base64 = true,
        data = encodedData,
    )
}

fun MediaSource.toMediaContentAttributeValues(field: String): MediaContentAttributeValues {
    return when (val media = this) {
        is MediaSource.File -> MediaContentAttributeValues.Data(
            field = field,
            contentType = media.contentType,
            data = loadFileAsBase64Encoded(media.filepath)
        )

        is MediaSource.Link -> MediaContentAttributeValues.Url(
            field = field,
            url = media.url,
        )
    }
}

sealed class MediaContentAttributeValues(val type: SupportedMediaContentTypes) {
    data class Url(
        val field: String,
        val url: String?
    ) : MediaContentAttributeValues(SupportedMediaContentTypes.URL)

    data class Data(
        val field: String,
        val contentType: String,
        val data: String?,
    ) : MediaContentAttributeValues(SupportedMediaContentTypes.BASE64) {
        override fun toString(): String {
            val trimmedData = when (data) {
                null -> "null"
                else -> data.take(12) + "..."
            }
            return "Data(field=$field, contentType=$contentType, data=$trimmedData)"
        }
    }
}

fun loadFileAsBase64Encoded(filepath: String): String {
    val file = loadFile(filepath)
    return Base64.getEncoder().encodeToString(file.readBytes())
}

fun loadFile(filepath: String): File {
    val classLoader = Thread.currentThread().contextClassLoader
    val file = classLoader.getResource(filepath)?.file?.let { File(it) }
        ?: error("Could not find file at $filepath")
    return file
}
