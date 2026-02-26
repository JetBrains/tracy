/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.eval.utils

private val ADJECTIVES = listOf(
    "clever", "delightful", "enthusiastic", "inspiring", "passionate", "unique",
    "awesome", "brilliant", "super", "wonderful", "generous", "helpful", "successful", "reliable",
    "proficient", "efficient", "innovative", "creative", "brave", "humble", "loyal", "confident",
    "trustworthy", "sensible", "adaptable", "comfortable", "communal",
    "friendly", "kind", "loving", "patriotic", "aware", "curios", "intelligent",
)

private val NOUNS = listOf(
    "bot", "camera", "engine", "face", "friend", "garden", "home", "sun",
    "car", "cat", "dog", "horse", "phone", "robot", "tree", "works",
    "apple", "banana", "pineapple", "strawberry", "orange", "kiwi", "melon",
    "lion", "bunny", "wolf", "tiger", "cheetah", "leopard", "rhino",
    "cream", "donut", "cake", "pie", "hamburger", "fries", "pizza"
)

fun createRandomRunNameAdjectiveNounNumber(): String {
    val adjective = ADJECTIVES.random()
    val noun = NOUNS.random()
    val number = (100..999).random()
    return "$adjective-$noun-$number"
}

@Suppress("unused")
private fun main() {
    require(ADJECTIVES.toSet().size == ADJECTIVES.size)
    require(NOUNS.toSet().size == NOUNS.size)
    require(ADJECTIVES.all { it.matches(Regex("^[a-z]+$")) })
    require(NOUNS.all { it.matches(Regex("^[a-z]+$")) })

    println("# of possible combinations: ADJECTIVES.size * NOUNS.size * (999 - 100 + 1) = ${ADJECTIVES.size * NOUNS.size * (999 - 100 + 1)}")
    repeat(10) { println(createRandomRunNameAdjectiveNounNumber()) }
}