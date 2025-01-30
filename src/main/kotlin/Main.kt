package org.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.example.ai.features.haiku.generateHaiku


suspend fun main() {
    val response = generateHaiku("table")
    println(response)
}
