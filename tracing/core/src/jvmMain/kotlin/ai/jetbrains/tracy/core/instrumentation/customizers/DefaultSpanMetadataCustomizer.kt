/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.core.instrumentation.customizers

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.lang.reflect.Method
import kotlin.coroutines.Continuation

actual typealias PlatformMethod = Method

actual object DefaultSpanMetadataCustomizer : SpanMetadataCustomizer {
    actual override fun formatInputAttributes(method: PlatformMethod, args: Array<Any?>): String {
        val inputJsonObject = buildJsonObject {
            method.parameters
                .filter { it.type != Continuation::class.java } // Exclude Continuation parameters
                .forEachIndexed { index, parameter ->
                    val paramName = parameter.name ?: "arg$index"
                    val argumentValue = args.getOrNull(index)
                    put(paramName, convertToJsonPrimitive(argumentValue))
                }
        }
        return inputJsonObject.toString()
    }

    private fun convertToJsonPrimitive(value: Any?): JsonPrimitive {
        return when (value) {
            null -> JsonPrimitive("null")
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }
}
