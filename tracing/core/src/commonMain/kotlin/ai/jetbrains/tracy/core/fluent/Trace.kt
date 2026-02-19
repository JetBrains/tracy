/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.fluent

import ai.jetbrains.tracy.core.fluent.customizers.DefaultSpanMetadataCustomizer
import ai.jetbrains.tracy.core.fluent.customizers.SpanMetadataCustomizer
import kotlin.reflect.KClass

/**
 * Annotates a function to automatically generate a tracing span on each invocation.
 *
 * @property name The name of the span. If left empty, a default name is derived from the function name.
 * @property metadataCustomizer A [KClass] of a [SpanMetadataCustomizer] to customize span names and attributes.
 *  Must be a Kotlin `object`. Defaults to [DefaultSpanMetadataCustomizer].
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Trace(
    val name: String = "",
    val metadataCustomizer: KClass<out SpanMetadataCustomizer> = DefaultSpanMetadataCustomizer::class,
)
