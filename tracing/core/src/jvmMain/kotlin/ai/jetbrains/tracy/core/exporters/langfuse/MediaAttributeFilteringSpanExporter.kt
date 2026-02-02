package ai.jetbrains.tracy.core.exporters.langfuse

import ai.jetbrains.tracy.core.adapters.media.UploadableMediaContentAttributeKeys
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.DelegatingSpanData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * A [SpanExporter] implementation that filters out specific media content attributes
 * from spans before delegating them to another [SpanExporter].
 *
 * The attributes filtered are identified by a specific prefix defined in
 * [UploadableMediaContentAttributeKeys.KEY_NAME_PREFIX].
 *
 * This exporter wraps the provided [delegate] exporter and ensures that any span data
 * passed through it has unwanted attributes removed.
 *
 * @property delegate The underlying [SpanExporter] to which the filtered span data is forwarded.
 *
 * @see FilteredSpanData
 */
class MediaAttributeFilteringSpanExporter(
    private val delegate: SpanExporter
) : SpanExporter {
    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        val filteredSpans = spans.map { FilteredSpanData(it) }
        return delegate.export(filteredSpans)
    }

    override fun flush(): CompletableResultCode = delegate.flush()

    override fun shutdown(): CompletableResultCode = delegate.shutdown()
}

/**
 * Filters out unwanted media content attributes from the given span
 * that are defined by the prefix [UploadableMediaContentAttributeKeys.KEY_NAME_PREFIX].
 */
private class FilteredSpanData(delegate: SpanData) : DelegatingSpanData(delegate) {
    private val filteredAttributes: Attributes = filterAttributes(delegate.attributes)

    override fun getAttributes(): Attributes = filteredAttributes

    companion object {
        private fun filterAttributes(attributes: Attributes): Attributes {
            val prefix = UploadableMediaContentAttributeKeys.KEY_NAME_PREFIX

            val keysToRemove = attributes.asMap().keys.filter { it.key.startsWith(prefix) }
            if (keysToRemove.isEmpty()) {
                return attributes
            }

            return attributes.toBuilder().apply {
                // filter out unwanted keys
                keysToRemove.forEach { remove(it) }
            }.build()
        }
    }
}
