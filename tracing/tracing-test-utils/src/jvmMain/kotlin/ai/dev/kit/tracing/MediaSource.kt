package ai.dev.kit.tracing

import ai.dev.kit.common.DataUrl
import io.ktor.http.Headers
import java.io.File
import java.util.Base64

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
        headers = Headers.Empty,
        base64 = true,
        data = encodedData,
    )
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