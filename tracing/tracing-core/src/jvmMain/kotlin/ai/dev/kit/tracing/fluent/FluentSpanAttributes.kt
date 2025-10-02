package ai.dev.kit.tracing.fluent

import ai.dev.kit.tracing.fluent.processor.SpanData
import io.opentelemetry.api.common.AttributeKey

actual fun SpanData.getAttribute(spanAttributeKey: FluentSpanAttributes): String? =
    this.attributes[AttributeKey.stringKey(spanAttributeKey.key)]