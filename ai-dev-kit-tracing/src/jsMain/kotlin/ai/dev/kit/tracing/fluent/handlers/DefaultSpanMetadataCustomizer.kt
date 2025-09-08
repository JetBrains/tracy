package ai.dev.kit.tracing.fluent.handlers

actual object DefaultSpanMetadataCustomizer : SpanMetadataCustomizer {
    actual override fun formatInputAttributes(method: PlatformMethod, args: Array<Any?>): String =
        throw NotImplementedError()
}
