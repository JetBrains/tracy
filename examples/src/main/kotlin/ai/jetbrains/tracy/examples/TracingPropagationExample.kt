/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package ai.jetbrains.tracy.examples

import ai.jetbrains.tracy.core.TracingManager
import ai.jetbrains.tracy.core.configureOpenTelemetrySdk
import ai.jetbrains.tracy.core.exporters.ConsoleExporterConfig
import ai.jetbrains.tracy.core.fluent.Trace

private interface OrderProcessor {
    @Trace(name = "ProcessOrder")
    suspend fun processOrder(orderId: Int): String
}

open class DefaultOrderProcessor : OrderProcessor {
    override suspend fun processOrder(orderId: Int): String {
        println("DefaultOrderProcessor: Processing order #$orderId")
        return "Order #$orderId processed successfully"
    }
}

class PremiumOrderProcessor : DefaultOrderProcessor() {
    override suspend fun processOrder(orderId: Int): String {
        println("PremiumOrderProcessor: Prioritizing order #$orderId for VIP handling")
        return "Order #$orderId processed with premium support"
    }
}

/**
 * Demonstrates how tracing logic from [Trace] annotations
 * applies automatically to interface implementations and subclass overrides.
 *
 * This example shows how:
 * - Applying [Trace] on an interface method enables automatic tracing for all its implementations.
 * - The tracing logic is propagated by the Kotlin tracing plugin.
 * - You can trace multiple class hierarchies without re-declaring annotations.
 *
 * When you run this example, both [PremiumOrderProcessor] and [DefaultOrderProcessor]
 * produce traced spans for their `processOrder` calls, even though only the interface method
 * is annotated with [Trace].
 */
suspend fun main() {
    TracingManager.setSdk(configureOpenTelemetrySdk(ConsoleExporterConfig()))
    PremiumOrderProcessor().processOrder(101)
    DefaultOrderProcessor().processOrder(202)
    println("See trace details in the console.")
    // Manual flush - alternatively, configure automatic flushing via ExporterCommonSettings
    TracingManager.flushTraces()
}
