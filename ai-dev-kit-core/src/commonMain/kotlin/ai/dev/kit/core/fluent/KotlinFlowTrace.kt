package ai.dev.kit.core.fluent

import ai.dev.kit.core.fluent.handlers.BaseSpanAttributeHandler
import ai.dev.kit.core.fluent.handlers.SpanAttributeHandler
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class KotlinFlowTrace(
    val name: String,
    val spanType: String = SpanType.UNKNOWN,
    val attributeHandler: KClass<out SpanAttributeHandler> = BaseSpanAttributeHandler::class
)
