package ai.dev.kit.tracing.fluent.handlers

expect class DefaultSpanMetadataCustomizer : SpanMetadataCustomizer {
    override fun formatInputAttributes(method: PlatformMethod, args: Array<Any?>): String
}
