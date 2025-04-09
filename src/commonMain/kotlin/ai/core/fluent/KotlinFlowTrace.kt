package ai.core.fluent

import ai.core.fluent.handlers.BaseSpanAttributeHandler
import ai.core.fluent.handlers.SpanAttributeHandler
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class KotlinFlowTrace(
    val name: String,
    val spanType: String = SpanType.UNKNOWN,
    val attributeHandler: KClass<out SpanAttributeHandler> = BaseSpanAttributeHandler::class
)
