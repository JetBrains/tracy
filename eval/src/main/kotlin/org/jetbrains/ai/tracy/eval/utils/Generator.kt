/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.eval.utils

/**
 * An encapsulation of the AI feature under test.
 */
interface Generator<AIInputT : AIInput, AIOutputT : AIOutput> {
    suspend fun generate(input: AIInputT): AIOutputT
}
