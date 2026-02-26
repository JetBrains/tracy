/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core

/**
 * Marks declarations that are **internal** to the Tracy library.
 *
 * This annotation indicates that the marked API should not be used outside
 * the Tracy library modules. APIs marked with this annotation:
 * - Are subject to change without notice
 * - May be removed in any release
 * - Provide no compatibility guarantees
 * - Are only public for technical reasons (cross-module visibility)
 *
 * **Usage of internal APIs is strongly discouraged** and should only be done
 * if you understand the risks. If you need functionality provided by an internal
 * API, please report your use case to the [Tracy issue tracker](https://youtrack.jetbrains.com/projects/TRA/issues)
 * so a stable public API can be provided.
 *
 * To use APIs marked with this annotation, you must explicitly opt-in by either:
 * - Adding `@OptIn(InternalTracyApi::class)` to your declaration
 * - Adding `-Xopt-in=org.jetbrains.ai.tracy.core.InternalTracyApi` to your module's compiler options
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS
)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is an internal Tracy API that should not be used from outside of the Tracy library. " +
            "No compatibility guarantees are provided. " +
            "It is recommended to report your use-case to the Tracy issue tracker, " +
            "so a stable API could be provided instead."
)
annotation class InternalTracyApi
