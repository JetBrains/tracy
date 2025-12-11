package ai.dev.kit.tracing.fluent.handlers

expect object DefaultSpanMetadataCustomizer : SpanMetadataCustomizer {
    override fun formatInputAttributes(method: PlatformMethod, args: Array<Any?>): String
}
